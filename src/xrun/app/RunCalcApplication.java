package xrun.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.http.Cookie;

import org.json.JSONArray;
import org.json.JSONObject;

import xrun.common.Constants;
import xrun.common.XRuntimeCache;
import xrun.parser.TrackParser;
import xrun.storage.CookieHandler;
import xrun.storage.DBStorage;
import xrun.storage.GoogleDriveStorage;
import xrun.utils.TimeUtils;
import xrun.utils.ChartUtils;
import xrun.utils.CommonUtils;
import xrun.utils.JsonSanitizer;

public class RunCalcApplication {
  
  private File                     gpxBase;
  private DBStorage                storage;
  private GoogleDriveStorage       drive;
  private CookieHandler            cookieHandler;
  private ReliveCCGarbageCollector reliveGC;
  private XRuntimeCache            cache;
  
  public RunCalcApplication(XRuntimeCache cache, File base, File clientSecret) {
    this.cache = cache;
    storage = new DBStorage(base);
    gpxBase = new File(base, "gpx");
    gpxBase.mkdirs();
    cookieHandler = new CookieHandler(storage);
    reliveGC = new ReliveCCGarbageCollector(this, storage);
    if (clientSecret != null) {
      drive = new GoogleDriveStorage(clientSecret);
    }
  }
  
  public RunCalcApplication(File base) { // minimalistic constructor
    storage = new DBStorage(base);
    gpxBase = new File(base, "gpx");
    gpxBase.mkdirs();
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      throw new IllegalArgumentException("Not enough arguments");
    }
    File base = new File(args[0]);
    if (!base.isDirectory()) {
      throw new IllegalArgumentException(base + " is not a valid folder path");
    }
    new RunCalcApplication(null, base, null).rescan();
  }
  
  void resetHandlerCache() {
    if (cache != null) {
      cache.reset();
    }
  }
  
  public Cookie generateCookie() {
    Cookie cookie = cookieHandler.generateCookie();
    if (cookie != null && drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return cookie;
  }
  
  public boolean isValidCookie(Cookie cookie) {
    return cookieHandler.isAuthorized(cookie);
  }
  
  public void removeCookie(Cookie cookie) {
  	cookieHandler.removeCookie(cookie);
  }
  
  public void rescan() {
    String[] all = gpxBase.list();
    boolean entriesAdded = false;
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx") || storage.hasActivity(fileName)) {
        continue;
      }
      File targ = new File(gpxBase, fileName);
      if (!targ.isFile()) {
        continue;
      }
      System.out.println("Process file " + targ);
      JSONArray dashboardsMainArr = new JSONArray();
      dashboardsMainArr.put(Constants.MAIN_DASHBOARD);
      String dashboardsMainS = dashboardsMainArr.toString();
      try {
        JSONObject current = TrackParser.parse(targ);
        current.put("dashboards", dashboardsMainS);
        current.put("type", Constants.RUNNING);
        current.put("ccLink", "none");
        current.put("photosLink", "none");
        storage.addActivity(current);
        if (drive != null) {
          drive.backupTrack(targ);
        }
        entriesAdded = true;
      } catch (Exception e) {
        System.out.println("Error processing " + targ);
        e.printStackTrace();
      }
    }
    if (entriesAdded && drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
      drive.backupDB(storage.getCoordsDBFile(), "coords");
    }
  }
  
  public synchronized List<JSONObject> getAllActivities() {
    return storage.getAllActivities();
  }
  
  public JSONObject retrieveCoords(String activity, boolean percentage) throws SQLException {
    JSONObject coords = storage.getCoordsData(activity);
    if (coords == null) {
      coords = storage.getCoordsData(activity + ".gpx");
    }
    if (coords == null) {
      return null;
    }
    if (!percentage) {
    	return coords;
    }
    JSONArray lats = coords.getJSONArray("lats");
    JSONArray lons = coords.getJSONArray("lons");
    JSONArray perc = new JSONArray();
    double dist = 0.0;
    for (int i = 1; i < lats.length(); ++i) {
      dist += TimeUtils.distance(lats.getDouble(i - 1), lats.getDouble(i),
          lons.getDouble(i - 1), lons.getDouble(i));
    }
		perc.put(0);
		double cdist = 0.0;
		for (int i = 1; i < lats.length(); ++i) {
			cdist += TimeUtils.distance(lats.getDouble(i - 1), lats.getDouble(i),
					lons.getDouble(i - 1), lons.getDouble(i));
			perc.put((cdist / dist) * 100.0);
		}
		coords.put("perc", perc);
		return coords;
  }
  
  private File addActivity0(String name, InputStream is) throws IOException {
  	if (storage.hasActivity(name)) {
  		String sname = null;
  		int i;
  		for (i = 0; i < 1000; ++i) {
  			sname = name + Constants.FILE_SUFF + i;
  			if (!storage.hasActivity(sname)) {
  				name = sname;
  				break;
  			}
  		}
  		if (i == 1000) {
  			return null;
  		}
  	}
  	File file = new File(gpxBase, name);
  	OutputStream os = null;
  	int rd = 0;
  	byte[] buff = new byte[8192];
  	try {
  		os = new FileOutputStream(file);
  		while ((rd = is.read(buff)) != -1) {
  			os.write(buff, 0, rd);
  		}
  		os.flush();
  	} catch (IOException ioe) {
  		file.delete();
  		throw ioe;
  	} finally {
  		try {
  			if (os != null) {
  				os.close();
  			}
  		} catch (IOException ignore) {
  			// silent catch
  		}
  	}
  	return file;
  }
  
  public void importActivity(JSONObject json) throws SQLException {
    storage.addActivity(json);
  }
  
  public String addActivity(String name, InputStream is, String activityName, String activityType, String reliveCC, String photos,
      String dashboard, boolean secure) {
  	File file = null;
  	try {
  	  file = addActivity0(name, is);
  	} catch (IOException ioe) {
  	  return "I/O error: " + ioe.getMessage();
  	}
  	if (file == null) {
  	  return "DB error";
  	}
    try {
      JSONObject current = TrackParser.parse(file);
      if (activityName != null) {
        current.put("name", activityName);
      }
      current.put("type", activityType);
      current.put("ccLink", reliveCC);
      current.put("photosLink", photos);
      if (!storage.dashboardExists(dashboard)) {
        dashboard = Constants.MAIN_DASHBOARD;
      }
      JSONArray arr = new JSONArray();
      arr.put(dashboard);
      if (!Constants.EXTERNAL_DASHBOARD.equals(dashboard) && !Constants.MAIN_DASHBOARD.equals(dashboard)) {
        arr.put(Constants.MAIN_DASHBOARD);
      }
      current.put("dashboards", arr);
      storage.addActivity(current);
      if (secure) {
      	storage.setSecureFlag(current.getString("genby"), true);
      }
      if (drive != null) {
        drive.backupTrack(file);
      }
    } catch (Exception e) {
      System.out.println("Error processing " + file);
      e.printStackTrace();
      file.delete();
      return "Processing file error: " + e.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
      drive.backupDB(storage.getCoordsDBFile(), "coords");
    }
  	return null;
  }
  
  private void removeFromTotals(JSONObject totals, JSONObject cr) {
    totals.put("totalDistance", totals.getDouble("totalDistance") - cr.getDouble("distRaw"));
    totals.put("totalTime", totals.getLong("totalTime") - cr.getLong("timeTotalRaw"));
    totals.put("elePos", totals.getLong("elePos") - cr.getLong("eleTotalPos"));
    totals.put("eleNeg", totals.getLong("eleNeg") - cr.getLong("eleTotalNeg"));
    totals.put("totalRunDist", totals.getDouble("totalRunDist") - cr.getDouble("distRunningRaw"));
  }
  
  private List<JSONObject> filter(boolean run, boolean trail, boolean uphill, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    return storage.fetchActivities(run, trail, uphill, hike, walk, other, startDate, endDate, minDistance, maxDistance);
  }
  
  public JSONObject filter(String nameFilter, boolean run, boolean trail, boolean uphill, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance, String dashboard, int periodLen, boolean getWMT,
      boolean isLoggedIn) {
    if (dashboard == null || dashboard.trim().length() == 0) {
      dashboard = Constants.MAIN_DASHBOARD;
    }
    JSONObject result = new JSONObject();
    JSONArray activities = new JSONArray();
    boolean isAllTrail = true;
    List<JSONObject> matched = filter(run, trail, uphill, hike, walk, other, startDate, endDate,
        minDistance, maxDistance);
    Iterator<JSONObject> it = matched.iterator();
    JSONObject totals = null;
    if (it.hasNext()) {
      totals = it.next();
    }
    int count = 0;
    while(it.hasNext()) {
      JSONObject cr = it.next();
      if (!matchName(nameFilter, cr.getString("name")) || !isFromDashboard(cr, dashboard)) {
        it.remove();
        removeFromTotals(totals, cr);
      } else if (!isLoggedIn && cr.optBoolean("secured")) {
        it.remove();
        removeFromTotals(totals, cr);
      } else {
      	++count;
      }
    }
    if (totals != null) {
      totals.put("avgSpeed", String.format("%.3f", totals.getDouble("totalDistance") / (totals.getLong("totalTime") / 3600.0)));
      totals.put("totalTime", TimeUtils.formatTime(totals.getLong("totalTime"), true, true));
      totals.put("avgDist", String.format("%.3f", totals.getDouble("totalDistance") / (double) count));
    }
    for (int i = 1; i < matched.size(); ++i) {
      JSONObject jobj = matched.get(i);
      activities.put(jobj);
      if (!Constants.TRAIL.equals(jobj.getString("type"))) {
        isAllTrail = false;
      }
    }
    result.put("isAllTrail", isAllTrail);
    result.put("activities", activities);
    result.put("charts", ChartUtils.getResultCharts(activities));
    if (getWMT) {
      result.put("mtotals", storage.getMonthlyTotals());
      result.put("wtotals", storage.getWeeklyTotals());
    } else {
      result.put("WMT", "none");
    }
    if (totals != null) {
      totals.put("avgDaily", "");
      totals.put("avgWeekly", "");
      totals.put("avgMonthly", "");
      if (periodLen > 0) {
        double avgDaily = totals.getDouble("totalDistance") / (double) periodLen;
        totals.put("avgDaily", String.format("%.3f", avgDaily));
        if (periodLen >= 7) {
          totals.put("avgWeekly", String.format("%.3f", 7 * avgDaily));
        }
        if (periodLen >= 28) {
          totals.put("avgMonthly", String.format("%.3f", (365 / 12.0) * avgDaily));
        }
      }
    }
    if (activities.length() > 0) {
      for (String key : totals.keySet()) {
        Object value = totals.get(key);
        if (value instanceof Double) {
          result.put(key, String.format("%.3f", (Double) value));
        } else {
          result.put(key, totals.get(key));
        }
      }
    }
    return result;
  }

  public JSONObject getActivity(String fileName) {
    return storage.getActivity(fileName);
  }
  
  private boolean matchName(String namePattern, String name) {
    if (namePattern == null) {
      return true;
    }
    namePattern = namePattern.trim();
    if (namePattern.length() == 0) {
      return true;
    }
    String[] splits = namePattern.split("%7C");
    for (String str : splits) {
      str = str.trim();
      if (str.length() > 0 && str.charAt(0) == '!') {
        if (name.contains(str.substring(1))) {
          return false;
        }
      }
    }
    boolean hasPositive = false;
    for (String str : splits) {
      str = str.trim();
      if (str.length() > 0 && str.charAt(0) == '!') {
        continue;
      }
      hasPositive = true;
      if (name.contains(str)) {
        return true;
      }
    }
    return !hasPositive;
  }
  
  private boolean isFromDashboard(JSONObject activity, String dashboard) {
    return CommonUtils.find(new JSONArray(JsonSanitizer.sanitize(activity.getString("dashboards"))), dashboard) != -1;
  }
  
  public JSONObject compare(JSONObject run1, JSONObject run2) {
    JSONObject result = new JSONObject();
    JSONObject general = new JSONObject();
    general.put("name1", run1.get("name"));
    general.put("name2", run2.get("name"));
    general.put("date1", run1.get("date"));
    general.put("date2", run2.get("date"));
    general.put("speed1", run1.get("avgSpeed"));
    general.put("speed2", run2.get("avgSpeed"));
    general.put("dist1", run1.get("dist"));
    general.put("dist2", run2.get("dist"));
    general.put("elePos1", run1.get("eleTotalPos"));
    general.put("elePos2", run2.get("eleTotalPos"));
    general.put("eleNeg1", run1.get("eleTotalNeg"));
    general.put("eleNeg2", run2.get("eleTotalNeg"));
    general.put("time1", run1.get("timeTotal"));
    general.put("time2", run2.get("timeTotal"));
    general.put("distRunning1", run1.get("distRunning"));
    general.put("distRunning2", run2.get("distRunning"));
    general.put("eleRunningPos1", run1.get("eleRunningPos"));
    general.put("eleRunningPos2", run2.get("eleRunningPos"));
    general.put("eleRunningNeg1", run1.get("eleRunningNeg"));
    general.put("eleRunningNeg2", run2.get("eleRunningNeg"));
    general.put("timeRunning1", run1.get("timeRunning"));
    general.put("timeRunning2", run2.get("timeRunning"));
    JSONArray splits1 = run1.getJSONArray("splits");
    JSONArray splits2 = run2.getJSONArray("splits");
    JSONArray diffsByTime = new JSONArray();
    for (int i = 0; i < Math.min(splits1.length(), splits2.length()); ++i) {
      JSONObject sp1 = splits1.getJSONObject(i);
      JSONObject sp2 = splits2.getJSONObject(i);
      double total1 = sp1.getDouble("totalRaw");
      double total2 = sp2.getDouble("totalRaw");
      if (Math.abs(total1 - total2) > 1e-3) {
        break;
      }
      JSONObject diff = new JSONObject();
      diff.put("time1", sp1.getString("time"));
      diff.put("time2", sp2.getString("time"));
      diff.put("point", String.format("%.3f", total1));
      long currentDiff = sp1.getLong("timeRaw") - sp2.getLong("timeRaw");
      diff.put("currentDiff", (currentDiff > 0 ? "+" : (currentDiff < 0 ? "-" : "")) + TimeUtils.formatTime(Math.abs(currentDiff), false));
      long totalDiff = sp1.getLong("timeTotalRaw") - sp2.getLong("timeTotalRaw");
      diff.put("totalDiff", (totalDiff > 0 ? "+" : (totalDiff < 0 ? "-" : "")) + TimeUtils.formatTime(Math.abs(totalDiff), false));
      diffsByTime.put(diff);
    }
    result.put("general", general);
    result.put("times", diffsByTime);
    return result;
  }
  
  private JSONObject getBest(String columnName, String suff) {
    JSONObject best = storage.getBestActivities(columnName);
    JSONObject result = new JSONObject();
    if (best == null) {
      return result;
    }
    Object val = best.get(columnName);
    if (val instanceof Double) {
      val = String.format("%.3f", (Double) val);
    }
    result.put("ach", val + " " + suff);
    result.put("when", best.get("date"));
    String genby = best.getString("genby");
    if (genby.endsWith(".gpx")) {
      genby = genby.substring(0, genby.length() - 4);
    }
    result.put("genby", genby);
    return result;
  }
  
  private JSONObject getBest(double distMin, double distMax) {
    JSONObject best = storage.getBestActivities(distMin, distMax);
    if (best == null) {
      return new JSONObject();
    }
    JSONObject result = new JSONObject();
    result.put("ach", best.get("timeTotal"));
    result.put("when", best.get("date"));
    String genby = best.getString("genby");
    if (genby.endsWith(".gpx")) {
      genby = genby.substring(0, genby.length() - 4);
    }
    result.put("genby", genby);
    return result;
  }
  
  public JSONObject getBest() {
    JSONObject result = new JSONObject();
    result.put("longest", getBest("distRaw", "km"));
    result.put("fastest", getBest("avgSpeedRaw", "km/h"));
    result.put("maxAsc", getBest("eleTotalPos", "m"));
    result.put("maxRun", getBest("distRunningRaw", "km"));
    result.put("1K", getBest(0.99, 1.1));
    result.put("2K5", getBest(2.47, 2.55));
    result.put("5K", getBest(4.95, 5.2));
    result.put("10K", getBest(9.88, 10.3));
    result.put("21K", getBest(20.64, 21.8));
    result.put("30K", getBest(29.4, 31));
    result.put("42K", getBest(42, 43.5));
    return result;
  }
  
  public JSONObject getBestSplits() {
    JSONArray arr = storage.getActivitySplits();
    Map<Integer, Long> best = new TreeMap<Integer, Long>();
    Map<Integer, String[]> bestAttrs = new TreeMap<Integer, String[]>();
    for (int i = 0; i < arr.length(); ++i) {
      JSONObject crnt = arr.getJSONObject(i);
      JSONArray splits = crnt.getJSONArray("splits");
      for (int j = 0; j < splits.length(); ++j) {
        JSONObject sp = splits.getJSONObject(j);
        double point = sp.getDouble("totalRaw");
        if (Math.abs(point - Math.round(point)) > 0.010001) {
          continue;
        }
        int rounded = (int) Math.round(point);
        long totalTimeRaw = sp.getLong("timeTotalRaw");
        Long currentBest = best.get(rounded);
        if (currentBest == null || totalTimeRaw < currentBest.longValue()) {
          best.put(rounded, totalTimeRaw);
          bestAttrs.put(rounded, new String[] {crnt.getString("name"), crnt.getString("date"), crnt.getString("genby")});
        }
      }
    }
    JSONObject result = new JSONObject();
    arr = new JSONArray();
    for (Entry<Integer, Long> entry : best.entrySet()) {
      String[] ba = bestAttrs.get(entry.getKey());
      JSONObject ach = new JSONObject();
      ach.put("point", entry.getKey());
      ach.put("name", ba[0]);
      ach.put("date", ba[1]);
      ach.put("genby", ba[2].endsWith(".gpx") ? ba[2].substring(0, ba[2].length() - 4) : ba[2]);
      long seconds = entry.getValue();
      ach.put("ach", TimeUtils.formatTime(seconds, true));
      ach.put("speed", String.format("%.3f", entry.getKey() / (seconds / 3600.0)));
      ach.put("pace", TimeUtils.formatPace((seconds / 60.0) / entry.getKey()));
      arr.put(ach);
    }
    result.put("totals", arr);
    return result;
  }
  
  public String editActivity(String fileName, String newName, String newType,
			String newGarmin, String newCC, String newPhotos, boolean secure,
			JSONObject mods) {
		JSONObject activity = storage.getActivity(fileName);
		if (activity == null) {
			return null;
		}
		try {
			if (mods != null) {
				if (modifyActivity(activity, mods)) {
					activity.put("name", newName);
					activity.put("type", newType);
					activity.put("garminLink", newGarmin.length() > 0 ? newGarmin
							: "none");
					activity.put("ccLink", newCC.length() > 0 ? newCC : "none");
					activity.put("photosLink", newPhotos.length() > 0 ? newPhotos
							: "none");
					storage.deleteActivity(fileName);
					storage.addActivity(activity);
				} else {
					storage.updateActivity(fileName, newName, newType, newGarmin, newCC,
							newPhotos, secure);
				}
			} else if (revertActivityChanges(activity)) {
				storage.deleteActivity(fileName);
				storage.addActivity(activity);
			}
		} catch (Exception e) {
			return "Error editing activity " + fileName + ": " + e.getMessage();
		}
		if (drive != null) {
			drive.backupDB(storage.getActivitiesDBFile(), "activities");
		}
		return null;
	}
  
  public String setFeatures(String fileName, String descr, List<String> links) {
    try {
      storage.setFeatures(fileName, descr != null ? descr : "", links);
    } catch (SQLException e) {
      return "DB Error - " + e.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public boolean isSecured(String fileName) {
  	return storage.isSecured(fileName);
  }
  
  public void deleteActivity(String fileName) throws Exception {
    File file = new File(gpxBase, fileName);
    if (file.isFile() && !file.delete()) {
      file.deleteOnExit();
    }
    storage.deleteActivity(fileName);
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
      drive.backupDB(storage.getCoordsDBFile(), "coords");
    }
  }
  
  public void deleteAllActivities() throws Exception {
    storage.dropAllActivities();
  }
  
  public String addDashboard(String name) {
    try {
      storage.addDashboard(name);
    } catch (SQLException e) {
      return "Error creating dashboard " + name + " - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public String renameDashboard(String name, String newName) {
    try {
      storage.renameDashboard(name, newName);
    } catch (SQLException e) {
      return "Error renaming dashboard " + name + " - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public String removeDashboard(String name) {
    try {
      storage.removeDashboard(name);
    } catch (SQLException e) {
      return "Error removing dashboard " + name + " - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public JSONObject getDashboards() {
    return storage.getDashboards();
  }
  
  public JSONObject getCompOptions(String activity, boolean ext) {
    return storage.getCompOptions(activity, ext);
  }
  
  public JSONObject getSplitsAndDist(String activity) {
    return storage.getSplitsAndDist(activity);
  }
  
  public String addToDashboard(String activity, String dashboard) {
    if (activity == null || dashboard == null) {
      return "Invalid parameters";
    }
    try {
      storage.addToDashboard(activity, dashboard);
    } catch (SQLException e) {
      return "Error adding activity " + activity + " to " + dashboard + " - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public String removeFromDashboard(String activity, String dashboard) {
    if (activity == null || dashboard == null) {
      return "Invalid parameters";
    }
    try {
      storage.removeFromDashboard(activity, dashboard);
    } catch (SQLException e) {
      return "Error removing activity " + activity + " from " + dashboard + " - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }
  
  public void dispose() {
    if (storage != null) {
      storage.close();
    }
    if (cookieHandler != null) {
      cookieHandler.dispose();
    }
    if (reliveGC != null) {
      reliveGC.dispose();
    }
  }
  
  private static String[] ORIG_DATA_KEYS = new String[] {
      "dist", "distRaw", "timeTotal", "timeTotalRaw", "avgSpeed", "avgSpeedRaw", "avgPace",
      "eleTotalPos", "eleTotalNeg"
  };
  
  private boolean modifyActivity(JSONObject activity, JSONObject mods) {
    if (activity == null || !mods.keys().hasNext()) {
      return false;
    }
    JSONObject initData = activity.optJSONObject("origData");
    JSONObject origData = null;
    if (initData == null || !initData.keys().hasNext()) {
      origData = new JSONObject();
      for (String key : ORIG_DATA_KEYS) {
        origData.put(key, activity.get(key));
      }
    }
    boolean result = false;
    if (mods.has("dist") || mods.has("time")) {
      double dist = mods.has("dist") ? mods.getDouble("dist") : activity.getDouble("distRaw");
      double time = mods.has("time") ? mods.getLong("time") : activity.getDouble("timeTotalRaw");
      activity.put("dist", String.format("%.3f", dist));
      activity.put("distRaw", dist);
      activity.put("timeTotal", TimeUtils.formatTime((long) time, true));
      activity.put("timeTotalRaw", time);
      double speed = dist / (time / 3600.0);
      activity.put("avgSpeed", String.format("%.3f", speed));
      activity.put("avgSpeedRaw", speed);
      activity.put("avgPace", TimeUtils.speedToPace(speed));
      result = true;
    }
    if (mods.has("gain")) {
      activity.put("eleTotalPos", mods.getLong("gain"));
      result = true;
    }
    if (mods.has("loss")) {
      activity.put("eleTotalNeg", mods.getLong("loss"));
      result = true;
    }
    if (origData != null) {
      activity.put("origData", origData);
    }
    return result;
  }
  
  private boolean revertActivityChanges(JSONObject activity) {
    JSONObject origData = activity.optJSONObject("origData");
    if (origData == null || !origData.keys().hasNext()) {
      return false;
    }
    for (String key : ORIG_DATA_KEYS) {
      Object val = origData.opt(key);
      if (val != null) {
        activity.put(key, val);
      }
    }
    activity.put("origData", new JSONObject());
    return true;
  }
  
  public JSONArray getPresets() {
    return storage.getPresets(null);
  }
  
  public JSONObject fetchPreset(String presetName) {
  	JSONObject preset = storage.getPresetData(presetName);
  	if (preset == null) {
  		return null;
  	}
  	return null;
  }
  
  public String addPreset(String name, String types, String pattern, String startDate, String endDate, int minDist, int maxDist, int top,
      String dashboard) {
  	try {
  		return storage.addPreset(name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard) ? null :
  		  "Existing preset overwritten";
  	} catch (SQLException e) {
  		return "Error adding preset " + name + " - db error";
  	} catch (RuntimeException re) {
  		return re.getMessage();
  	} finally {
  	  if (drive != null) {
        drive.backupDB(storage.getActivitiesDBFile(), "activities");
      }
  	}
  }
  
  public String renamePreset(String name, String newName) {
  	try {
  		storage.renamePreset(name, newName);
  	} catch (SQLException e) {
  		return "Error renaming preset " + name + " - db error";
  	} catch (RuntimeException re) {
  		return re.getMessage();
  	}
  	if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
  	return null;
  }
  
  public String removePreset(String name) {
  	try {
  		storage.removePreset(name);
  	} catch (SQLException e) {
  		return "Error removing preset " + name + " - db error";
  	} catch (RuntimeException re) {
  		return re.getMessage();
  	}
  	if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
  	return null;
  }
  
  public String reorder(String elements, int option) {
    int ind = -1;
    int next = 0;
    List<String> ordered = new LinkedList<String> ();
    while (next < elements.length() && (ind = elements.indexOf("|||", next)) != -1) {
      ordered.add(elements.substring(next, ind));
      next = ind + 3;
    }
    try {
      switch (option) {
        case 1:
          storage.reorderPresets(ordered);
          break;
        case 2:
          ordered.add(0, Constants.MAIN_DASHBOARD);
          storage.reorderDashboards(ordered);
      }
    } catch (SQLException e) {
      return "Error reordering - db error";
    } catch (RuntimeException re) {
      return re.getMessage();
    }
    if (drive != null) {
      drive.backupDB(storage.getActivitiesDBFile(), "activities");
    }
    return null;
  }

}
