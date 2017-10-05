package xrun;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class RunCalcUtils {
  
  static final String RUNNING = "Running";
  static final String TRAIL = "Trail";
  static final String HIKING = "Hiking";
  static final String WALKING = "Walking";
  static final String OTHER = "Other";
  
  private File gpxBase;
  private SQLiteManager sqLite;
  private GoogleDrive drive;
  
  RunCalcUtils(File base, File clientSecret) {
    sqLite = new SQLiteManager(base);
    gpxBase = new File(base, "gpx");
    gpxBase.mkdirs();
    if (clientSecret != null) {
      drive = new GoogleDrive(clientSecret);
    }
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      throw new IllegalArgumentException("Not enough arguments");
    }
    File base = new File(args[0]);
    if (!base.isDirectory()) {
      throw new IllegalArgumentException(base + " is not a valid folder path");
    }
    new RunCalcUtils(base, null).rescan();
  }
  
  void rescan() {
    List<JSONObject> runs = new ArrayList<JSONObject>();
    String[] all = gpxBase.list();
    boolean entriesAdded = false;
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx") || sqLite.hasActivity(fileName)) {
        continue;
      }
      File targ = new File(gpxBase, fileName);
      if (!targ.isFile()) {
        continue;
      }
      System.out.println("Process file " + targ);
      try {
        JSONObject current = new JSONObject();
        CalcDist.run(targ, 9, 100, 1, current); // default values
        runs.add(current);
        sqLite.addActivity(current);
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
      drive.backupDB(sqLite.getDBFile());
    }
  }
  
  private File addActivity0(String name, InputStream is) throws IOException {
  	if (sqLite.hasActivity(name)) {
  		String sname = null;
  		int i;
  		for (i = 0; i < 1000; ++i) {
  			sname = name + CalcDist.FILE_SUFF + i;
  			if (!sqLite.hasActivity(sname)) {
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
  
  JSONArray getMonthlyTotals() {
    return sqLite.getMonthlyTotals();
  }
  
  String addActivity(String name, InputStream is, String activityName, String activityType) {
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
      JSONObject current = new JSONObject();
      CalcDist.run(file, 9, 100, 1, current); // default values
      if (activityName != null) {
        current.put("name", activityName);
      }
      current.put("type", activityType);
      sqLite.addActivity(current);
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
      drive.backupDB(sqLite.getDBFile());
    }
  	return null;
  }
  
  private List<JSONObject> filter(boolean run, boolean trail, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance, int maxCount) {
    return sqLite.fetchActivities(run, trail, hike, walk, other, startDate, endDate, minDistance, maxDistance, maxCount);
  }
  
  JSONObject filter(String nameFilter, boolean run, boolean trail, boolean hike, boolean walk, boolean other, int records,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    JSONObject result = new JSONObject();
    JSONArray activities = new JSONArray();
    List<JSONObject> matched = filter(run, trail, hike, walk, other, startDate, endDate,
        minDistance, maxDistance, records);
    Iterator<JSONObject> it = matched.iterator();
    JSONObject totals = null;
    if (it.hasNext()) {
      totals = it.next();
    }
    while(it.hasNext()) {
      JSONObject cr = it.next();
      if (!matchName(nameFilter, cr.getString("name"))) {
        it.remove();
        totals.put("totalDistance", totals.getDouble("totalDistance") - cr.getDouble("distRaw"));
        totals.put("totalTime", totals.getLong("totalTime") - cr.getLong("timeTotalRaw"));
        totals.put("elePos", totals.getLong("elePos") - cr.getLong("eleTotalPos"));
        totals.put("eleNeg", totals.getLong("eleNeg") - cr.getLong("eleTotalNeg"));
        totals.put("totalRunDist", totals.getDouble("totalRunDist") - cr.getDouble("distRunningRaw"));
      }
    }
    if (totals != null) {
      totals.put("avgSpeed", String.format("%.3f", totals.getDouble("totalDistance") / (totals.getLong("totalTime") / 3600.0)));
      totals.put("totalTime", CalcDist.formatTime(totals.getLong("totalTime"), true));
    }
    for (int i = 1; i < matched.size(); ++i) {
      activities.put(matched.get(i));
    }
    result.put("activities", activities);
    result.put("mtotals", getMonthlyTotals());
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
  
  JSONObject getActivity(String fileName) {
    return sqLite.getActivity(fileName);
  }
  
  static void silentClose(Closeable cl) {
    try {
      if (cl != null) {
        cl.close();
      }
    } catch (Exception ignore) {
    }
  }
  
  private static boolean matchName(String namePattern, String name) {
    if (namePattern == null) {
      return true;
    }
    namePattern = namePattern.trim();
    if (namePattern.length() == 0) {
      return true;
    }
    String[] splits = namePattern.split("%7C");
    for (String str : splits) {
      if (name.contains(str)) {
        return true;
      }
    }
    return false;
  }
  
  JSONObject compare(JSONObject run1, JSONObject run2) {
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
      diff.put("currentDiff", (currentDiff > 0 ? "+" : (currentDiff < 0 ? "-" : "")) + CalcDist.formatTime(Math.abs(currentDiff), false));
      long totalDiff = sp1.getLong("timeTotalRaw") - sp2.getLong("timeTotalRaw");
      diff.put("totalDiff", (totalDiff > 0 ? "+" : (totalDiff < 0 ? "-" : "")) + CalcDist.formatTime(Math.abs(totalDiff), false));
      diffsByTime.put(diff);
    }
    result.put("general", general);
    result.put("times", diffsByTime);
    return result;
  }
  
  private JSONObject getBest(String columnName, String suff) {
    JSONObject best = sqLite.getBestActivities(columnName);
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
    return result;
  }
  
  private JSONObject getBest(double distMin, double distMax) {
    JSONObject best = sqLite.getBestActivities(distMin, distMax);
    if (best == null) {
      return new JSONObject();
    }
    JSONObject result = new JSONObject();
    result.put("ach", best.get("timeTotal"));
    result.put("when", best.get("date"));
    return result;
  }
  
  JSONObject getBest() {
    JSONObject result = new JSONObject();
    result.put("longest", getBest("distRaw", "km"));
    result.put("fastest", getBest("avgSpeedRaw", "km/h"));
    result.put("maxAsc", getBest("eleTotalPos", "m"));
    result.put("1K", getBest(0.99, 1.1));
    result.put("2K5", getBest(2.49, 2.55));
    result.put("5K", getBest(4.99, 5.2));
    result.put("10K", getBest(9.98, 10.3));
    result.put("21K", getBest(21, 21.8));
    result.put("30K", getBest(30, 31));
    result.put("42K", getBest(42, 43.5));
    return result;
  }
  
  JSONObject getBestSplits() {
    JSONArray arr = sqLite.getActivitySplits();
    Map<Integer, Long> best = new TreeMap<Integer, Long>();
    Map<Integer, String[]> bestAttrs = new TreeMap<Integer, String[]>();
    for (int i = 0; i < arr.length(); ++i) {
      JSONObject crnt = arr.getJSONObject(i);
      JSONArray splits = crnt.getJSONArray("splits");
      for (int j = 0; j < splits.length(); ++j) {
        JSONObject sp = splits.getJSONObject(j);
        double point = sp.getDouble("totalRaw");
        if (Math.abs(point - Math.round(point)) > 1e-3) {
          continue;
        }
        int rounded = (int) Math.round(point);
        long totalTimeRaw = sp.getLong("timeTotalRaw");
        Long currentBest = best.get(rounded);
        if (currentBest == null || totalTimeRaw < currentBest.longValue()) {
          best.put(rounded, totalTimeRaw);
          bestAttrs.put(rounded, new String[] {crnt.getString("name"), crnt.getString("date")});
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
      ach.put("ach", CalcDist.formatTime(entry.getValue(), true));
      arr.put(ach);
    }
    result.put("totals", arr);
    return result;
  }
  
  void editActivity(String fileName, String newName, String newType) {
    sqLite.updateActivity(fileName, newName, newType);
    if (drive != null) {
      drive.backupDB(sqLite.getDBFile());
    }
  }
  
  void deleteActivity(String fileName) {
    File file = new File(gpxBase, fileName);
    if (file.isFile() && !file.delete()) {
      file.deleteOnExit();
    }
    sqLite.deleteActivities(fileName);
    if (drive != null) {
      drive.backupDB(sqLite.getDBFile());
    }
  }
  
  void dispose() {
    if (sqLite != null) {
      sqLite.close();
    }
  }

}
