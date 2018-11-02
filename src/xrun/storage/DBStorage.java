package xrun.storage;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import xrun.common.Constants;
import xrun.items.Activity;
import xrun.items.ActivityCoords;
import xrun.items.Cookie;
import xrun.items.Dashboard;
import xrun.items.Features;
import xrun.items.Preset;
import xrun.items.SecureId;
import xrun.orm.test.Account;
import xrun.utils.CalendarUtils;
import xrun.utils.CommonUtils;
import xrun.utils.JsonSanitizer;
import xrun.utils.TimeUtils;

public class DBStorage {

  private static final String   RUNS_TABLE_NAME                   = "runs";
  private static final String   PRESETS_TABLE_NAME                = "presets";

  private static final String[] KEYS                              = new String[] {
      "genby", "name", "type", "date", "year", "month", "day", "dist", "distRaw",
      "starttime", "timeTotal", "timeTotalRaw", "timeRawMs", "timeRunning", "timeRest",
      "avgSpeed", "avgSpeedRaw", "avgPace", "distRunning", "distRunningRaw",
      "eleTotalPos", "eleTotalNeg", "eleRunningPos", "eleRunningNeg",
      "garminLink", "ccLink", "photosLink",
      "parent",
      "distByInterval", "distByIntervalLabels",
      "dashboards", "isExt",
      "speedDist", "splits",
      "origData"
  };
  private static final String[] TYPES                             = new String[] {
      "text", "text", "text", "text", "integer", "integer", "integer", "text", "real",
      "text", "text", "real", "integer", "text", "text",
      "text", "real", "text", "text", "real",
      "integer", "integer", "integer", "integer",
      "text", "text", "text",
      "text",
      "text", "text",
      "text", "integer",
      "text", "text",
      "text"
  };
  private static final String   DB_FILE_PREF                      = "activities";

  private File                  dbActivities;
  private File                  dbCoords;
  private Connection            conn                              = null;

  private ConnectionSource            connectionActivities              = null;
  private Dao<Activity, String>       runsDao                           = null;
  private Dao<Features, String>       featuresDao                       = null;
  private Dao<Preset, String>         presetsDao                        = null;
  private Dao<SecureId, String>       secureDao                         = null;
  private Dao<Cookie, String>         cookieDao                         = null;
  private Dao<Dashboard, String>      dashboardDao                      = null;
  private ConnectionSource            connectionCoords                  = null;
  private Dao<ActivityCoords, String> coordsDao                         = null;

  public DBStorage(File base) {
    dbActivities = new File(base, "activities.db");
    dbCoords = new File(base, "coords.db");
    if (!dbActivities.isFile()) {
      File[] children = base.listFiles();
      if (children != null) {
        for (File child : children) {
          String name = child.getName();
          if (name.startsWith(DB_FILE_PREF) && name.endsWith(".db")) {
            dbActivities = child;
            break;
          }
        }
      }
    }
  }

  private void ensureCoordsInit() throws SQLException {
    if (connectionCoords != null) {
      return;
    }
    connectionCoords = new JdbcConnectionSource("jdbc:sqlite:" + dbCoords.getAbsolutePath().replace('\\', '/'));
    TableUtils.createTableIfNotExists(connectionCoords, ActivityCoords.class);
    coordsDao = DaoManager.createDao(connectionCoords, ActivityCoords.class);
  }

  private void addCoordsData(String id, JSONArray lats, JSONArray lons, JSONArray times, JSONArray markers)
      throws SQLException {
    synchronized (dbCoords) {
      ensureCoordsInit();
      coordsDao.createOrUpdate(new ActivityCoords(id, lats, lons, times, markers));
    }
  }

  public JSONObject getCoordsData(String id) throws SQLException {
    synchronized (dbCoords) {
      ensureCoordsInit();
      ActivityCoords coords = coordsDao.queryForId(id);
      return coords != null ? new JSONObject(JsonSanitizer.sanitize(coords.getData())) : null;
    }
  }

  private void removeCoordsData(String id) throws SQLException {
    synchronized (dbCoords) {
      ensureCoordsInit();
      coordsDao.deleteById(id);
    }
  }

  public synchronized JSONArray getWeeklyTotals() {
    JSONArray result = new JSONArray();
    try {
      List<Activity> distinctYears = runsDao.queryBuilder().selectColumns("year").distinct().query();
      List<Integer> years = new ArrayList<Integer>();
      for (Activity yr : distinctYears) {
        years.add(yr.getYear());
      }
      Collections.sort(years);
      Calendar current = new GregorianCalendar(TimeZone.getDefault());
      int currentYear = current.get(Calendar.YEAR);
      for (int i = years.size() - 1; i >= 0; --i) {
        List<Activity> selection = runsDao.queryBuilder()
            .selectColumns("timeRawMs", "type", "distRaw", "eleTotalPos", "isExt", "parent")
            .where().eq("year", years.get(i)).query();
        Map<Integer, JSONObject> weekly = new HashMap<Integer, JSONObject>();
        int maxWeek;
        if (years.get(i) == currentYear) {
          maxWeek = CalendarUtils.identifyWeek(current.get(Calendar.DAY_OF_MONTH), current.get(Calendar.MONTH) + 1,
              current.get(Calendar.YEAR), new String[1])[0];
        } else {
          maxWeek = CalendarUtils.getWeekCount(years.get(i));
        }
        for (int w = 1; w <= maxWeek; ++w) {
          JSONObject data = new JSONObject();
          data.put("r", 0d);
          data.put("countr", 0);
          data.put("t", 0d);
          data.put("countt", 0);
          data.put("u", 0d);
          data.put("countu", 0);
          data.put("h", 0d);
          data.put("counth", 0);
          data.put("rt", 0d);
          data.put("countrt", 0);
          data.put("totalPositiveEl", 0);
          data.put("a", 0d);
          data.put("counta", 0);
          data.put("info", "Empty week");
          weekly.put(w, data);
        }
        JSONArray wArr = new JSONArray();
        for (Activity selected : selection) {
          if (selected.getIsExt() == 1 || selected.getParent().length() > 0) {
            continue;
          }
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(selected.getTimeRawMs());
          String[] formatted = new String[1];
          int[] idf = CalendarUtils.identifyWeek(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1,
              cal.get(Calendar.YEAR), formatted);
          int week = idf[0];
          JSONObject data = weekly.get(week);
          if ("Empty week".equals(data.getString("info"))) {
            data.put("info", "W" + week + " " + formatted[0]);
          }
          String type = selected.getType();
          double dist = selected.getDistRaw();
          if (Constants.RUNNING.equals(type)) {
            data.put("r", data.getDouble("r") + dist);
            data.put("rt", data.getDouble("rt") + dist);
            data.put("countr", data.getInt("countr") + 1);
            data.put("countrt", data.getInt("countrt") + 1);
          } else if (Constants.TRAIL.equals(type)) {
            data.put("t", data.getDouble("t") + dist);
            data.put("rt", data.getDouble("rt") + dist);
            data.put("countt", data.getInt("countt") + 1);
            data.put("countrt", data.getInt("countrt") + 1);
          } else if (Constants.UPHILL.equals(type)) {
            data.put("u", data.getDouble("u") + dist);
            data.put("countu", data.getInt("countu") + 1);
          } else if (Constants.HIKING.equals(type)) {
            data.put("h", data.getDouble("h") + dist);
            data.put("counth", data.getInt("counth") + 1);
          }
          data.put("a", data.getDouble("a") + dist);
          data.put("counta", data.getInt("counta") + 1);
          long totalPositiveEl = selected.getEleTotalPos();
          if (totalPositiveEl > 0) {
            data.put("totalPositiveEl", data.getInt("totalPositiveEl") + totalPositiveEl);
          }
          weekly.put(week, data);
        }
        for (int week = 60; week >= 0; --week) {
          JSONObject data = weekly.get(week);
          if (data != null) {
            data.put("r", String.format("%.3f", data.getDouble("r")));
            data.put("t", String.format("%.3f", data.getDouble("t")));
            data.put("u", String.format("%.3f", data.getDouble("u")));
            data.put("h", String.format("%.3f", data.getDouble("h")));
            data.put("rt", String.format("%.3f", data.getDouble("rt")));
            data.put("a", String.format("%.3f", data.getDouble("a")));
            wArr.put(data);
          }
        }
        JSONObject yinfo = new JSONObject();
        yinfo.put("year", years.get(i));
        yinfo.put("data", wArr);
        result.put(yinfo);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  private static boolean isExternal(JSONArray arr) {
    if (arr == null) {
      return false;
    }
    for (int i = 0; i < arr.length(); ++i) {
      if (Constants.EXTERNAL_DASHBOARD.equals(arr.getString(i))) {
        return true;
      }
    }
    return false;
  }

  public synchronized JSONArray getMonthlyTotals() {
    JSONArray result = new JSONArray();
    try {
      List<Activity> distinctYears = runsDao.queryBuilder().selectColumns("year").distinct().query();
      List<Integer> years = new ArrayList<Integer>();
      for (Activity yr : distinctYears) {
        years.add(yr.getYear());
      }
      Collections.sort(years);
      String[] filters = new String[] {
          "type='" + Constants.RUNNING + '\'',
          "type='" + Constants.TRAIL + '\'',
          "type='" + Constants.UPHILL + '\'',
          "type='" + Constants.HIKING + '\'',
          "type='" + Constants.RUNNING + "' OR type='" + Constants.TRAIL + '\'',
          null
      };
      String[] acms = new String[] {
          "r", "t", "u", "h", "rt", "a"
      };
      for (int i = years.size() - 1; i >= 0; --i) {
        JSONArray yearData = new JSONArray();
        int year = years.get(i);
        JSONObject[] months = new JSONObject[12];
        for (int j = 0; j < 12; ++j) {
          months[j] = new JSONObject();
          months[j].put("name", Constants.MONTHS[j] + ' ' + year);
          for (int k = 0; k < acms.length; ++k) {
            months[j].put(acms[k], "0");
            months[j].put("count" + acms[k], 0);
          }
        }
        for (int j = 0; j < filters.length; ++j) {
          String typeFilter = (filters[j] != null ? ("(" + filters[j] + ") AND ") : "");
          List<String[]> rs = runsDao.queryRaw("SELECT month, SUM(distRaw) FROM " + RUNS_TABLE_NAME + " WHERE " + typeFilter +
              "year=" + years.get(i) + " AND isExt=0 AND parent='' GROUP BY month").getResults();
          for (String[] entry : rs) {
            int ind = Integer.parseInt(entry[0]);
            months[ind].put(acms[j], String.format("%.3f", Double.valueOf(entry[1])));
            months[ind].remove("emp");
          }
          rs = runsDao.queryRaw("SELECT month, COUNT(genby) FROM "
              + RUNS_TABLE_NAME + " WHERE " + typeFilter + " year="
              + years.get(i) + " AND isExt=0 AND parent='' GROUP BY month").getResults();
          for (String[] entry : rs) {
            int ind = Integer.parseInt(entry[0]);
            months[ind].put("count" + acms[j], Integer.valueOf(entry[1]));
            months[ind].put("totalPositiveEl", 0);
            months[ind].remove("emp");
          }
        }
        List<String[]> rs = runsDao.queryRaw("SELECT month, SUM(eleTotalPos) FROM " + RUNS_TABLE_NAME + " WHERE year=" +
            years.get(i) + " AND isExt=0 AND parent='' GROUP BY month").getResults();
        for (String[] entry : rs) {
          int totalPositiveEl = Integer.parseInt(entry[1]);
          if (totalPositiveEl > 0) {
            months[Integer.parseInt(entry[0])].put("totalPositiveEl", totalPositiveEl);
          }
        }
        for (int j = 0; j < months.length; ++j) {
          JSONObject monthData = new JSONObject();
          monthData.put("month", Constants.MONTHS[j]);
          monthData.put("data", months[j]);
          yearData.put(monthData);
        }
        JSONObject yearObj = new JSONObject();
        yearObj.put("year", year);
        yearObj.put("data", yearData);
        result.put(yearObj);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public synchronized void ensureActivitiesInit() throws SQLException {
    if (conn != null) {
      return;
    }
    conn = DriverManager.getConnection("jdbc:sqlite:" + dbActivities.getAbsolutePath().replace('\\', '/'));
    connectionActivities = new JdbcConnectionSource("jdbc:sqlite:" + dbActivities.getAbsolutePath().replace('\\', '/'));
    createTablesIfNotExists();
  }

  private ResultSet executeQuery(String query, boolean returnResult) {
    try {
      ensureActivitiesInit();
      if (returnResult) {
        return conn.createStatement().executeQuery(query);
      }
      conn.createStatement().executeUpdate(query);
    } catch (SQLException e) {
      System.out.println("Error working with db");
      e.printStackTrace();
    }
    return null;
  }

  private ResultSet executeQueryExc(String query, boolean returnResult) throws SQLException {
    ensureActivitiesInit();
    if (returnResult) {
      return conn.createStatement().executeQuery(query);
    }
    conn.createStatement().executeUpdate(query);
    return null;
  }

  private void createTablesIfNotExists() throws SQLException {
    TableUtils.createTableIfNotExists(connectionActivities, Account.class);
    TableUtils.createTableIfNotExists(connectionActivities, Features.class);
    TableUtils.createTableIfNotExists(connectionActivities, Preset.class);
    TableUtils.createTableIfNotExists(connectionActivities, SecureId.class);
    TableUtils.createTableIfNotExists(connectionActivities, Cookie.class);
    TableUtils.createTableIfNotExists(connectionActivities, Dashboard.class);
    runsDao = DaoManager.createDao(connectionActivities, Activity.class);
    featuresDao = DaoManager.createDao(connectionActivities, Features.class);
    presetsDao = DaoManager.createDao(connectionActivities, Preset.class);
    secureDao = DaoManager.createDao(connectionActivities, SecureId.class);
    cookieDao = DaoManager.createDao(connectionActivities, Cookie.class);
    dashboardDao = DaoManager.createDao(connectionActivities, Dashboard.class);
  }

  synchronized private void fillInFeatures(JSONObject activity) {
    activity.put("descr", "");
    try {
      Features features = featuresDao.queryForId(activity.getString("genby"));
      if (features != null) {
        activity.put("descr", features.getDescr());
        activity.put("links", features.getLinks());
      }
    } catch (SQLException e) {
      System.out.println("Error working with features db - retrieving results");
    }
    if (!activity.has("links")) {
      activity.put("links", new JSONArray().toString());
    }
  }

  public synchronized void setFeatures(String id, String descr, List<String> links) throws SQLException {
    featuresDao.createOrUpdate(new Features(id, descr, links));
  }

  synchronized private void removeFeatures(String id) throws SQLException {
    featuresDao.deleteById(id);
  }

  public synchronized boolean addPreset(String name, String types, String pattern, String startDate, String endDate,
      int minDist, int maxDist,
      int top, String dashboard) throws SQLException {
    boolean result = presetsDao.idExists(name);
    presetsDao.createOrUpdate(new Preset(name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard));
    return result;
  }

  public synchronized void renamePreset(String name, String newName) throws SQLException {
    ResultSet rs = executePreparedQuery("SELECT * FROM " + PRESETS_TABLE_NAME + " WHERE name=?", newName);
    if (rs != null && rs.next()) {
      throw new IllegalArgumentException("Preset " + newName + " already exists");
    }
    executePreparedQuery("UPDATE " + PRESETS_TABLE_NAME + " SET name=? WHERE name=?", newName, name);
  }

  public synchronized void removePreset(String name) throws SQLException {
    presetsDao.deleteById(name);
  }

  public synchronized JSONObject getPresetData(String name) {
    try {
      Preset found = presetsDao.queryForId(name);
      return found != null ? found.exportToJson() : null;
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public synchronized JSONArray getPresets(Map<String, JSONObject> out) {
    JSONArray result = new JSONArray();
    try {
      List<Preset> presets = presetsDao.queryForAll();
      for (Preset preset : presets) {
        JSONObject json = preset.exportToJson();
        if (out != null) {
          json.put("types", preset.getTypes());
          out.put(json.getString("name"), json);
        } else {
          result.put(json);
        }
      }
    } catch (Exception e) {
      System.out.println("Error working with db - getting presets");
      e.printStackTrace();
    }
    return result;
  }

  public synchronized void reorderPresets(List<String> presets) throws SQLException {
    Map<String, JSONObject> current = new LinkedHashMap<String, JSONObject>();
    getPresets(current);
    executeQueryExc("DELETE FROM " + PRESETS_TABLE_NAME, false);
    for (String preset : presets) {
      JSONObject data = current.remove(preset);
      if (data != null) {
        addPreset(preset, data.getString("types"), data.getString("pattern"), data.getString("startDate"),
            data.getString("endDate"),
            data.getInt("minDist"), data.getInt("maxDist"), data.getInt("top"), data.getString("dashboard"));
      }
    }
    for (JSONObject data : current.values()) {
      addPreset(data.getString("name"), data.getString("types"), data.getString("pattern"), data.getString("startDate"),
          data.getString("endDate"),
          data.getInt("minDist"), data.getInt("maxDist"), data.getInt("top"), data.getString("dashboard"));
    }
  }

  public synchronized void reorderDashboards(List<String> dashboards) throws SQLException {
    TableUtils.clearTable(connectionActivities, Dashboard.class);
    for (String dashboard : dashboards) {
      dashboardDao.create(new Dashboard(dashboard));
    }
  }

  public synchronized void addActivity(JSONObject entry) throws SQLException {
    if (!entry.has("parent")) {
      entry.put("parent", "");
    }
    JSONArray dashboards = null;
    String dashStr = entry.optString("dashboards");
    if (dashStr != null) {
      dashboards = new JSONArray(JsonSanitizer.sanitize(dashStr));
    } else {
      dashboards = entry.optJSONArray("dashboards");
    }
    boolean isExt = isExternal(dashboards);
    entry.put("isExt", isExt ? 1 : 0);
    runsDao.create(new Activity(entry));
    if (entry.has("lons") && entry.has("lats") && entry.has("times") && entry.has("markers")) {
      addCoordsData(entry.getString("genby"), entry.getJSONArray("lats"), entry.getJSONArray("lons"),
          entry.getJSONArray("times"),
          entry.getJSONArray("markers"));
    }
  }

  private ResultSet executePreparedQuery(String statement, Object... values)
      throws SQLException {
    ensureActivitiesInit();
    ResultSet result = null;
    PreparedStatement prep = conn.prepareStatement(statement);
    for (int i = 0; i < values.length; ++i) {
      if (values[i] instanceof String) {
        prep.setString(i + 1, (String) values[i]);
      } else if (values[i] instanceof Long || values[i] instanceof Integer) {
        prep.setLong(i + 1, ((Number) values[i]).longValue());
      } else if (values[i] instanceof Double || values[i] instanceof Float) {
        prep.setDouble(i + 1, ((Number) values[i]).doubleValue());
      }
    }
    if (prep.execute()) {
      result = prep.getResultSet();
    }
    if (!conn.getAutoCommit()) {
      conn.commit();
    }
    return result;
  }

  private JSONObject readActivity2 (Activity activity, boolean includeSplitsAndDistr) {
    JSONObject result = activity.exportToJSON(includeSplitsAndDistr);
    if (isSecured(result.getString("genby"))) {
      result.put("secured", true);
    }
    fillInFeatures(result);
    return result;
  }

  private JSONObject readActivity(ResultSet rs, boolean includeSplitsAndDistr) throws JSONException, SQLException {
    if (!rs.next()) {
      return null;
    }
    JSONObject activity = new JSONObject();
    int len = KEYS.length;
    for (int i = 0; i < len - 3; ++i) {
      if (i != len - 6 && i != len - 7) { // bad db architecture :(
        activity.put(KEYS[i], rs.getObject(i + 1));
      }
    }
    if (includeSplitsAndDistr) {
      activity.put(KEYS[len - 3], new JSONArray(JsonSanitizer.sanitize(rs.getString(len - 2))));
      activity.put(KEYS[len - 2], new JSONArray(JsonSanitizer.sanitize(rs.getString(len - 1))));
      activity.put(KEYS[len - 7], new JSONArray(JsonSanitizer.sanitize(rs.getString(len - 6))));
      activity.put(KEYS[len - 6], new JSONArray(JsonSanitizer.sanitize(rs.getString(len - 5))));
    }
    String origData = rs.getString(len);
    activity.put(KEYS[len - 1],
        (origData != null ? new JSONObject(JsonSanitizer.sanitize(origData)) : new JSONObject()));
    JSONObject data = activity.getJSONObject(KEYS[len - 1]);
    if (data != null && data.keys().hasNext()) {
      activity.put("isModified", "y");
    }
    if (isSecured(activity.getString("genby"))) {
      activity.put("secured", true);
    }
    if (includeSplitsAndDistr) {
      JSONArray splits = activity.getJSONArray("splits");
      double accEle = 0.0;
      for (int i = 0; i < splits.length(); ++i) {
        JSONObject split = splits.getJSONObject(i);
        accEle += split.getDouble("eleD");
        split.put("accEle", (long) accEle);
        split.put("total", split.getString("total").replace(',', '.'));
        split.put("speed", split.getString("speed").replace(',', '.'));
        split.put("accumSpeed", split.getString("accumSpeed").replace(',', '.'));
      }
    }
    Calendar cal = new GregorianCalendar();
    cal.setTimeInMillis(activity.getLong("timeRawMs"));
    long corr = TimeZone.getDefault().inDaylightTime(cal.getTime()) ? Constants.CORRECTION_BG_SUMMER
        : Constants.CORRECTION_BG_WINTER;
    cal.setTimeInMillis(activity.getLong("timeRawMs") + corr);
    activity.put("startAt", TimeUtils.formatDate(cal, true));
    fillInFeatures(activity);
    return activity;
  }


  @SuppressWarnings("unchecked")
  public synchronized List<JSONObject> fetchActivities(boolean run, boolean trail, boolean uphill, boolean hike,
      boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) throws SQLException {
    QueryBuilder<Activity, String> builder = runsDao.queryBuilder();
    Where<Activity, String> where = builder.where();
    if (run || trail || uphill || hike || walk || other) {
      if (run) {
        where.eq("type", Constants.RUNNING).or();
      }
      if (trail) {
        where.eq("type", Constants.TRAIL).or();
      }
      if (uphill) {
        where.eq("type", Constants.UPHILL).or();
      }
      if (hike) {
        where.eq("type", Constants.HIKING).or();
      }
      if (walk) {
        where.eq("type", Constants.WALKING).or();
      }
      if (other) {
        where.eq("type", Constants.OTHER).or();
      }
      where.eq("type", "not"); // to finish the statement
    } else {
      return Collections.emptyList();
    }
    where.and(where.ge("distRaw", minDistance).and().le("distRaw", maxDistance), where);
    List<JSONObject> result = new ArrayList<JSONObject>();
    if (startDate != null) {
      int yr = startDate.get(Calendar.YEAR);
      int mt = startDate.get(Calendar.MONTH);
      int d = startDate.get(Calendar.DAY_OF_MONTH);
      where.and(where.or(where.gt("year", yr), where.eq("year", yr).and().gt("month", mt),
          where.eq("year", yr).and().eq("month", mt).and().ge("day", d)), where);
    }
    if (endDate != null) {
      int yr = endDate.get(Calendar.YEAR);
      int mt = endDate.get(Calendar.MONTH);
      int d = endDate.get(Calendar.DAY_OF_MONTH);
      where.and(where.or(where.lt("year", yr), where.eq("year", yr).and().lt("month", mt),
          where.eq("year", yr).and().eq("month", mt).and().le("day", d)), where);
    }
    builder.orderBy("timeRawMs", false);
    List<Activity> activities = builder.query();
    double distTotal = 0.0;
    double timeTotal = 0;
    long elePosTotal = 0;
    long eleNegTotal = 0;
    double distRunTotal = 0.0;
    JSONObject totals = new JSONObject();
    for (Activity activity : activities) {
      distTotal += activity.getDistRaw();
      timeTotal += activity.getTimeTotalRaw();
      elePosTotal += activity.getEleTotalPos();
      eleNegTotal += activity.getEleTotalNeg();
      distRunTotal += activity.getDistRunningRaw();
    }
    try {
      totals.put("totalDistance", distTotal);
      totals.put("totalTime", timeTotal);
      totals.put("elePos", elePosTotal);
      totals.put("eleNeg", eleNegTotal);
      totals.put("totalRunDist", distRunTotal);
      result.add(totals);
    } catch (Exception ignore) {
      // silent catch
    }
    for (Activity activity : activities) {
      result.add(readActivity2(activity, false));
    }
    return result;
  }
  
  public synchronized List<JSONObject> getAllActivities() {
    List<JSONObject> result = new ArrayList<JSONObject>();
    List<Activity> activities;
    try {
      activities = runsDao.queryForAll();
      for (Activity activity : activities) {
        result.add(activity.exportToJSON(true));
      }
    } catch (SQLException ignore) {
      // ignore
    }
    return result;
  }

  public synchronized void updateActivity(String fileName, String newName, String newType, String newGarmin,
      String newCC, String newPhotos, boolean secure) throws SQLException {
    StringBuffer sb = new StringBuffer();
    if (newGarmin == null || newGarmin.length() == 0) {
      newGarmin = "none";
    }
    if (newCC == null || newCC.length() == 0) {
      newCC = "none";
    }
    if (newPhotos == null || newPhotos.length() == 0) {
      newPhotos = "none";
    }
    sb.append("UPDATE " + RUNS_TABLE_NAME
        + " SET name = ?, type = ?, garminLink = ?, ccLink = ?, photosLink = ? WHERE genby = ?");
    executePreparedQuery(sb.toString(), newName, newType, newGarmin, newCC, newPhotos, fileName);
    setSecureFlag(fileName, secure);
  }

  public synchronized void deleteActivity(String fileName, boolean deleteFeatsAndCoords) throws SQLException {
    runsDao.deleteById(fileName);
    secureDao.deleteById(fileName);
    if (deleteFeatsAndCoords) {
      removeFeatures(fileName);
      removeCoordsData(fileName);
    }
  }
  
  public synchronized void dropAllActivities() throws SQLException {
    if (conn == null) {
      conn = DriverManager.getConnection("jdbc:sqlite:" + dbActivities.getAbsolutePath().replace('\\', '/'));
    }
    conn.createStatement().executeUpdate("DROP TABLE IF EXISTS " + RUNS_TABLE_NAME);
    conn = null;
  }

  public synchronized boolean isSecured(String fileName) {
    try {
      return secureDao.queryForId(fileName) != null;
    } catch (SQLException se) {
      System.out.println("Error checking secured flag");
    }
    return true;
  }

  public synchronized void setSecureFlag(String fileName, boolean flag) throws SQLException {
    boolean isSecured = isSecured(fileName);
    if (flag) {
      if (!isSecured) {
        secureDao.create(new SecureId(fileName));
      }
    } else if (isSecured) {
      secureDao.deleteById(fileName);
    }
  }

  public File getActivitiesDBFile() {
    return dbActivities;
  }

  public File getCoordsDBFile() {
    return dbCoords;
  }

  public synchronized JSONObject getActivity(String fileName) {
    try {
      return readActivity2(runsDao.queryForId(fileName), true);
    } catch (Exception e) {
      System.out.println("Error reading activity " + fileName);
      e.printStackTrace();
    }
    return null;
  }

  public synchronized boolean hasActivity(String fileName) {
    try {
      return runsDao.idExists(fileName);
    } catch (SQLException e) {
      return false;
    }
  }

  public synchronized JSONObject getBestActivities(String columnName) {
    JSONArray extArr = new JSONArray();
    extArr.put(Constants.EXTERNAL_DASHBOARD);
    String extS = extArr.toString();
    try {
      ResultSet rs = executePreparedQuery("SELECT date, genby, " + columnName + " FROM " + RUNS_TABLE_NAME +
          " WHERE " + columnName +
          "=(SELECT MAX(" + columnName + ") FROM " + RUNS_TABLE_NAME +
          " WHERE dashboards!=?)", extS);
      if (rs == null || !rs.next()) {
        return null;
      }
      JSONObject result = new JSONObject();
      result.put("date", rs.getString("date"));
      result.put("genby", rs.getString("genby"));
      result.put(columnName, rs.getObject(columnName));
      return result;
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return null;
  }

  public synchronized JSONObject getBestActivities(double distMin, double distMax, double distActual) {
    JSONObject best = null;
    try {
      List<Activity> activities = runsDao.queryBuilder().orderBy("timeTotalRaw", true)
          .selectColumns("genby", "date", "timeTotal", "timeTotalRaw", "isExt", "parent")
          .where().ge("distRaw", distMin).and().le("distRaw", distMax).and().ne("type", Constants.OTHER)
          .query();
      /*ResultSet rs = executeQuery("SELECT genby, date, timeTotal, timeTotalRaw, isExt, parent FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distMin + " AND distRaw <= " + distMax + " AND type!='" + Constants.OTHER + "')
          ORDER BY timeTotalRaw", true);*/
      double bestTime = Double.MAX_VALUE;
      for (Activity activity : activities) {
        if (activity.getIsExt() == 1 || activity.getParent().length() > 0) {
          continue;
        }
        JSONObject result = new JSONObject();
        result.put("date", activity.getDate());
        result.put("genby", activity.getGenby());
        result.put("timeTotal", activity.getTimeTotal());
        best = result;
        bestTime = activity.getTimeTotalRaw();
        break;
      }
      activities = runsDao.queryBuilder().selectColumns("genby", "date", "splits", "isExt", "parent")
          .where().ge("distRaw", distMin).and().le("distRaw", distMax).and().ne("type", Constants.OTHER).query();
      /*rs = executeQuery("SELECT genby, date, splits, isExt, parent FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distActual + " AND type!='" + Constants.OTHER + "')", true);*/
      int distFloor = (int) distActual;
      for (Activity activity : activities) {
        if (activity.getIsExt() == 1 || activity.getParent().length() > 0) {
          continue;
        }
        if (best != null && best.getString("genby").equals(activity.getGenby())) {
          continue;
        }
        JSONArray splits = new JSONArray(JsonSanitizer.sanitize(activity.getSplits()));
        for (int i = Math.max(distFloor - 2, 0); i < Math.min(splits.length(), distFloor + 2); ++i) {
          JSONObject split = splits.getJSONObject(i);
          double dist = split.getDouble("totalRaw");
          if (dist >= distFloor) {
            double timeTotalEst = split.getDouble("timeTotalRaw") * (distActual / dist);
            if (timeTotalEst < bestTime) {
              bestTime = timeTotalEst;
              JSONObject result = new JSONObject();
              result.put("date", activity.getDate());
              result.put("genby", activity.getGenby());
              result.put("timeTotal", "Estimated effort " + TimeUtils.formatTime((long) timeTotalEst));
              best = result;
            }
            break;
          }
        }
      }
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return best;
  }

  public synchronized JSONArray getActivitySplits() {
    JSONArray result = new JSONArray();
    try {
      List<Activity> activities = runsDao.queryBuilder().selectColumns("name", "date", "splits", "genby", "isExt", "parent")
          .where().eq("type", Constants.RUNNING).or().eq("type", Constants.TRAIL).query();
      for (Activity activity : activities) {
        if (activity.getIsExt() == 1 || activity.getParent().length() > 0) {
          continue;
        }
        JSONObject crnt = new JSONObject();
        crnt.put("name", activity.getName());
        crnt.put("date", activity.getDate());
        crnt.put("splits", new JSONArray(JsonSanitizer.sanitize(activity.getSplits())));
        crnt.put("genby", activity.getGenby());
        result.put(crnt);
      }
    } catch (SQLException e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return result;
  }

  public synchronized void addToDashboard(String activity, String dashboard) throws SQLException {
    if (!dashboardDao.idExists(dashboard)) {
      throw new IllegalArgumentException("Dashboard not found");
    }
    Activity da = runsDao.queryBuilder().selectColumns("dashboards").where().eq("genby", activity).queryForFirst();
    if (da == null) {
      throw new IllegalArgumentException("Activity not found");
    }
    JSONArray dashboards = new JSONArray(JsonSanitizer.sanitize(da.getDashboards()));
    if (CommonUtils.find(dashboards, dashboard) != -1) {
      throw new IllegalArgumentException("Activity already in dashboard");
    }
    dashboards.put(dashboard);
    Activity target = runsDao.queryForId(activity);
    if (target != null) {
      target.setDashboards(dashboard);
      runsDao.update(target);
    }
  }

  public synchronized void removeFromDashboard(String activity, String dashboard) throws SQLException {
    if (!dashboardDao.idExists(dashboard)) {
      throw new IllegalArgumentException("Dashboard not found");
    }
    Activity da = runsDao.queryBuilder().selectColumns("dashboards").where().eq("genby", activity).queryForFirst();
    if (da == null) {
      throw new IllegalArgumentException("Activity not found");
    }
    JSONArray dashboards = new JSONArray(JsonSanitizer.sanitize(da.getDashboards()));
    int ind = CommonUtils.find(dashboards, dashboard);
    if (ind == -1) {
      throw new IllegalArgumentException("Activity not in dashboard");
    }
    if (dashboards.length() == 1) {
      throw new IllegalArgumentException("Activity must be present in at least one dashboard");
    }
    dashboards.remove(ind);
    executePreparedQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards=? WHERE genby=?",
        dashboards.toString(), activity);
  }

  public synchronized void addDashboard(String name) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (Constants.MAIN_DASHBOARD.equals(name)) {
      throw new IllegalArgumentException("Cannot re-add main dashboard");
    }
    dashboardDao.create(new Dashboard(name));
  }

  public synchronized void renameDashboard(String name, String newName) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (Constants.MAIN_DASHBOARD.equals(name) || Constants.MAIN_DASHBOARD.equals(newName)) {
      throw new IllegalArgumentException("Cannot rename main dashboard");
    }
    if (dashboardDao.idExists(newName)) {
      throw new IllegalArgumentException("Dashboard " + newName + " already exists");
    }
    if (!dashboardDao.idExists(name)) {
      throw new IllegalArgumentException("Dashboard " + name + " doest not exist");
    }

    executePreparedQuery("UPDATE dashboards SET name=? WHERE name=?", newName, name); // TODO
    ResultSet rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
    while (rs.next()) {
      String dash = rs.getString(2);
      JSONArray arr = new JSONArray(JsonSanitizer.sanitize(dash));
      boolean mod = false;
      for (int i = 0; i < arr.length(); ++i) {
        if (name.equals(arr.get(i))) {
          arr.remove(i);
          arr.put(i, newName);
          mod = true;
          break;
        }
      }
      if (mod) {
        String genby = rs.getString(1);
        executePreparedQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards=? WHERE genby=?",
            arr.toString(), genby);
      }
    }
    executePreparedQuery("UPDATE " + PRESETS_TABLE_NAME + " SET dashboard=? WHERE dashboard=?",
        newName, name);
  }

  public synchronized void removeDashboard(String name) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (Constants.MAIN_DASHBOARD.equals(name)) {
      throw new IllegalArgumentException("Cannot remove main dashboard");
    }
    dashboardDao.deleteById(name);
    //ResultSet rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
    List<Activity> activities = runsDao.queryBuilder().selectColumns("genby", "dashboards").query();
    for (Activity activity : activities) {
      String dash = activity.getDashboards();
      JSONArray arr = new JSONArray(JsonSanitizer.sanitize(dash));
      boolean mod = false;
      for (int i = 0; i < arr.length(); ++i) {
        if (name.equals(arr.get(i))) {
          arr.remove(i);
          mod = true;
          break;
        }
      }
      if (mod) {
        String genby = activity.getGenby();
        UpdateBuilder<Activity, String> ub = runsDao.updateBuilder();
        ub.where().eq("genby", genby);
        ub.updateColumnValue("dashboards", arr.toString()).update();
      }
    }
    /*executePreparedQuery(
        "UPDATE " + PRESETS_TABLE_NAME + " SET dashboard='" + Constants.MAIN_DASHBOARD + "' WHERE dashboard=?",
        name);*/
    runsDao.updateBuilder().updateColumnValue("dashboard", Constants.MAIN_DASHBOARD).where().eq("dashboard", name);
  }

  public synchronized JSONObject getDashboards() {
    JSONArray arr = new JSONArray();
    try {
      List<Dashboard> dashboards = dashboardDao.queryForAll();
      if (dashboards == null || dashboards.isEmpty()) {
        Dashboard main = new Dashboard(Constants.MAIN_DASHBOARD);
        dashboardDao.create(main);
        dashboards = new ArrayList<>();
        dashboards.add(main);
      }
      for (Dashboard dashboard : dashboards) {
        arr.put(dashboard.getName());
      }
    } catch (SQLException e) {
      System.out.println("Error fetching dashboards");
      e.printStackTrace();
    }
    JSONObject result = new JSONObject();
    result.put("dashboards", arr);
    return result;
  }

  public synchronized boolean dashboardExists(String dashboard) {
    if (dashboard == null) {
      return false;
    }
    try {
      return dashboardDao.idExists(dashboard);
    } catch (Exception e) {
      System.out.println("Error saving cookie");
      e.printStackTrace();
    }
    return false;
  }

  public synchronized boolean saveCookie(String uid, Calendar expires) {
    try {
      boolean hasCookie = cookieDao.idExists(uid);
      if (!hasCookie) {
        Cookie cookie = new Cookie(uid,
            expires.get(Calendar.YEAR) + "-" + expires.get(Calendar.MONTH) + "-" + expires.get(Calendar.DATE));
        cookieDao.create(cookie);
      }
      return !hasCookie;
    } catch (SQLException e) {
      System.out.println("Error saving cookie");
      e.printStackTrace();
    }
    return false;
  }

  private boolean isCookieValid(String expires) {
    if (expires == null) {
      return true;
    }
    String[] tokens = expires.split("-");
    if (tokens.length != 3) {
      return false;
    }
    Calendar current = new GregorianCalendar(TimeZone.getDefault());
    int year = new Integer(tokens[0]);
    int month = new Integer(tokens[1]);
    int date = new Integer(tokens[2]);
    if (current.get(Calendar.YEAR) < year) {
      return true;
    }
    if (current.get(Calendar.YEAR) == year) {
      if (current.get(Calendar.MONTH) < month) {
        return true;
      }
      if (current.get(Calendar.MONTH) == month) {
        return current.get(Calendar.DATE) < date;
      }
    }
    return false;
  }

  public synchronized boolean isValidCookie(String uid) {
    try {
      Cookie cookie = cookieDao.queryForId(uid);
      if (cookie == null) {
        return false;
      }
      if (isCookieValid(cookie.getExpires())) {
        return true;
      }
      deleteCookie(uid);
    } catch (Exception e) {
      System.out.println("Error verifying cookie");
      e.printStackTrace();
    }
    return false;
  }

  public synchronized void deleteCookie(String uid) throws SQLException {
    cookieDao.deleteById(uid);
  }

  public synchronized void checkForExpiredCookies() {
    try {
      List<Cookie> cookies = cookieDao.queryForAll();
      for (Cookie cookie : cookies) {
        if (!isCookieValid(cookie.getExpires())) {
          cookieDao.delete(cookie);
        }
      }
    } catch (Exception ignore) {
      // silent catch
    }
  }

  private static boolean areTypesCompatible(String type1, String type2) {
    if (type1.equals(type2)) {
      return true;
    }
    if (Constants.TRAIL.equals(type1)) {
      return Constants.HIKING.equals(type2) || Constants.UPHILL.equals(type2);
    }
    if (Constants.HIKING.equals(type1)) {
      return Constants.TRAIL.equals(type2) || Constants.UPHILL.equals(type2);
    }
    if (Constants.UPHILL.equals(type1)) {
      return Constants.HIKING.equals(type2) || Constants.TRAIL.equals(type2);
    }
    return false;
  }

  public synchronized JSONObject getSplitsAndDist(String activity) {
    JSONObject json = null;
    try {
      ResultSet rs = executePreparedQuery("SELECT splits, speedDist, distByInterval, distByIntervalLabels FROM " + RUNS_TABLE_NAME + " WHERE genby=?",
          activity);
      if (rs != null && rs.next()) {
        json = new JSONObject();
        JSONArray splits = new JSONArray(JsonSanitizer.sanitize(rs.getString("splits")));
        double accEle = 0.0;
        for (int i = 0; i < splits.length(); ++i) {
          JSONObject split = splits.getJSONObject(i);
          accEle += split.getDouble("eleD");
          split.put("accEle", (long) accEle);
          split.put("total", split.getString("total").replace(',', '.'));
          split.put("speed", split.getString("speed").replace(',', '.'));
          split.put("accumSpeed", split.getString("accumSpeed").replace(',', '.'));
        }
        json.put("splits", splits);
        json.put("speedDist", new JSONArray(JsonSanitizer.sanitize(rs.getString("speedDist"))));
        json.put("distByInterval", new JSONArray(JsonSanitizer.sanitize(rs.getString("distByInterval"))));
        json.put("distByIntervalLabels", new JSONArray(JsonSanitizer.sanitize(rs.getString("distByIntervalLabels"))));
      }
    } catch (SQLException e) {
      System.out.println("Error getting splits " + e);
      e.printStackTrace();
    }
    return json;
  }

  public synchronized JSONObject getCompOptions(String activity, boolean searchOnlyExt) {
    JSONObject json = null;
    Map<String, Boolean> dashboards = new HashMap<String, Boolean>();
    String type = null;
    double dist = 0.0;
    double speed = 0.0;
    List<DataEntry> entries = new ArrayList<>();
    boolean isFromExt = false;
    try {
      List<Activity> activities = runsDao.queryBuilder().selectColumns("dashboards", "type", "distRaw", "avgSpeedRaw")
          .where().eq("genby", activity).query();
      if (activities.isEmpty()) {
        return null;
      }
      Activity result = activities.get(0);
      type = result.getType();
      dist = result.getDistRaw();
      speed = result.getAvgSpeedRaw();
      JSONArray arr = new JSONArray(JsonSanitizer.sanitize(result.getDashboards()));
      for (int i = 0; i < arr.length(); ++i) {
        String dash = arr.getString(i);
        if (!Constants.MAIN_DASHBOARD.equals(dash)) {
          dashboards.put(dash, Boolean.TRUE);
        }
        if (Constants.EXTERNAL_DASHBOARD.equals(dash)) {
          isFromExt = true;
        }
      }
      if (isFromExt && searchOnlyExt) {
        json = new JSONObject();
        json.put("comps", new JSONArray());
        return json;
      }
      activities = runsDao.queryBuilder().selectColumns("genby", "dashboards", "type", "distRaw", "avgSpeedRaw").query();
      for (Activity entry : activities) {
        if (activity.equals(entry.getGenby())) {
          continue;
        }
        if (!areTypesCompatible(type, entry.getType())) {
          continue;
        }
        double sspeed = entry.getAvgSpeedRaw();
        if (dist < 25 && (0.66 * speed > sspeed || 1.5 * speed < sspeed)) {
          continue;
        }
        arr = new JSONArray(JsonSanitizer.sanitize(entry.getDashboards()));
        boolean dashboardMatch = isFromExt;
        if (!dashboardMatch) {
          for (int i = 0; i < arr.length(); ++i) {
            String cdash = arr.getString(i);
            if (searchOnlyExt && Constants.EXTERNAL_DASHBOARD.equals(cdash)) {
              dashboardMatch = true;
              break;
            }
            if (!searchOnlyExt && dashboards.containsKey(cdash)) {
              dashboardMatch = true;
              break;
            }
          }
          if (!dashboardMatch) {
            continue;
          }
        }
        entries.add(new DataEntry(entry.getGenby(), Math.abs(dist - entry.getDistRaw())));
      }
      Collections.sort(entries);
      int len = searchOnlyExt ? entries.size() : Math.min(10, entries.size());
      arr = new JSONArray();
      for (int i = 0; i < len; ++i) {
        String id = entries.get(i).getId();
        activities = runsDao.queryBuilder().selectColumns("name", "type", "date", "dist").where().eq("genby", id).query();
        if (!activities.isEmpty()) {
          Activity act = activities.get(0);
          JSONObject cr = new JSONObject();
          cr.put("id", id);
          cr.put("text", act.getName() + ' ' + act.getType() + ", " + act.getDate()
              + "| " + act.getDist() + "km");
          arr.put(cr);
        }
      }
      json = new JSONObject();
      json.put("comps", arr);
    } catch (SQLException e) {
      System.out.println("Error getting comparison options " + e);
      e.printStackTrace();
    }
    return json;
  }

  public synchronized void close() {
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException ignore) {
      // silent catch
    }
    try {
      if (connectionActivities != null) {
        connectionActivities.close();
      }
    } catch (IOException ignore) {
      // silent catch
    }
    try {
      if (connectionCoords != null) {
        connectionCoords.close();
      }
    } catch (IOException ignore) {
      // silent catch
    }
    conn = null;
    connectionCoords = null;
  }

  public synchronized void cleanupReliveCCBefore(Calendar cal) {
    try {
      ensureActivitiesInit();
      int day = cal.get(Calendar.DAY_OF_MONTH);
      int month = cal.get(Calendar.MONTH);
      int year = cal.get(Calendar.YEAR);
      UpdateBuilder<Activity, String> ub = runsDao.updateBuilder();
      Where<Activity, String> where = ub.where();
      where.or(where.lt("year", year), where.eq("year", year).and().lt("month", month),
          where.eq("year", year).and().eq("month", month).and().lt("day", day));
      ub.updateColumnValue("ccLink", "none").update();
    } catch (Exception e) {
      // ignore
    }
  }

}

class DataEntry implements Comparable<DataEntry> {

  private String id;
  private double diff;

  DataEntry(String id, double diff) {
    this.id = id;
    this.diff = diff;
  }

  public int compareTo(DataEntry arg0) {
    if (diff == arg0.diff) {
      return 0;
    }
    return diff < arg0.diff ? -1 : 1;
  }

  String getId() {
    return id;
  }
}
