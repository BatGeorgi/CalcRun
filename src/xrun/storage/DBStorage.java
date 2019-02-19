package xrun.storage;

import java.io.File;
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
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import xrun.utils.TimeUtils;
import xrun.app.BestSplitAch;
import xrun.common.Constants;
import xrun.utils.CalendarUtils;
import xrun.utils.JsonSanitizer;
import xrun.utils.CommonUtils;

public class DBStorage {

  private static final String   RUNS_TABLE_NAME                   = "runs";
  private static final String   COOKIES_TABLE_NAME                = "cookies";
  private static final String   DASHBOARDS_TABLE_NAME             = "dashboards";
  private static final String   PRESETS_TABLE_NAME                = "presets";
  private static final String   COORDS_TABLE_NAME                 = "coords";
  private static final String   FEATURES_TABLE_NAME               = "features";
  private static final String   SECURED_TABLE_NAME                = "secured";
  private static final String   BEST_TABLE_NAME                = "best";

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

  private static final String   CREATE_STATEMENT_COOKIES_TABLE    = "CREATE TABLE IF NOT EXISTS " + COOKIES_TABLE_NAME +
      "(uid PRIMARY KEY NOT NULL, expires NOT NULL)";
  private static final String   CREATE_STATEMENT_DASHBOARDS_TABLE = "CREATE TABLE IF NOT EXISTS "
      + DASHBOARDS_TABLE_NAME +
      "(name PRIMARY KEY NOT NULL)";
  private static final String   CREATE_STATEMENT_PRESETS_TABLE    = "CREATE TABLE IF NOT EXISTS " + PRESETS_TABLE_NAME +
      "(name text NOT NULL, types text NOT NULL, pattern text NOT NULL, startDate text NOT NULL, endDate text NOT NULL, "
      + "minDist integer NOT NULL, maxDist integer NOT NULL, top integer NOT NULL, dashboard text NOT NULL)";
  private static final String   CREATE_STATEMENT_FEATURES_TABLE   = "CREATE TABLE IF NOT EXISTS " + FEATURES_TABLE_NAME
      +
      "(id text NOT NULL, descr text not null, links text NOT NULL)";
  private static final String   CREATE_STATEMENT_SECURED_TABLE    = "CREATE TABLE IF NOT EXISTS " + SECURED_TABLE_NAME +
      "(id text NOT NULL)";
  private static final String   CREATE_STATEMENT_BEST_TABLE    = "CREATE TABLE IF NOT EXISTS " + BEST_TABLE_NAME +
      "(dist PRIMARY KEY NOT NULL, genby text NOT NULL, time integer NOT NULL, startpoint real NOT NULL)";

  private File                  dbActivities;
  private File                  dbCoords;
  private String                createStatementRunsTable;
  private Connection            conn                              = null;
  private Connection            connDB2                           = null;

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
    StringBuffer cr = new StringBuffer();
    cr.append("CREATE TABLE IF NOT EXISTS " + RUNS_TABLE_NAME + " (");
    cr.append(KEYS[0] + ' ' + TYPES[0] + " PRIMARY KEY NOT NULL, ");
    int len = KEYS.length;
    for (int i = 1; i < len - 1; ++i) {
      cr.append(KEYS[i] + ' ' + TYPES[i] + " NOT NULL, ");
    }
    cr.append(KEYS[len - 1] + ' ' + TYPES[len - 1] + " NOT NULL)");
    createStatementRunsTable = cr.toString();
  }

  private void ensureCoordsInit() throws SQLException {
    if (connDB2 != null) {
      return;
    }
    connDB2 = DriverManager.getConnection("jdbc:sqlite:" + dbCoords.getAbsolutePath().replace('\\', '/'));
    connDB2.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS " + COORDS_TABLE_NAME +
        " (id text PRIMARY KEY NOT NULL, data text NOT NULL)");
  }

  private void addCoordsData(String id, JSONArray lats, JSONArray lons, JSONArray times, JSONArray markers)
      throws SQLException {
    synchronized (dbCoords) {
      ensureCoordsInit();
      JSONObject json = new JSONObject();
      json.put("lats", lats);
      json.put("lons", lons);
      json.put("times", times);
      json.put("markers", markers);
      String str = json.toString();
      ResultSet rs = connDB2.createStatement()
          .executeQuery("SELECT * FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
      if (!rs.next()) {
        connDB2.createStatement()
            .executeUpdate("INSERT INTO " + COORDS_TABLE_NAME + " VALUES ('" + id + "', '" + str + "')");
      } else {
        connDB2.createStatement()
            .executeUpdate("UPDATE " + COORDS_TABLE_NAME + " SET data='" + str + "' WHERE id='" + id + "'");
      }
    }
  }

  public JSONObject getCoordsData(String id) throws SQLException {
    JSONObject json = null;
    synchronized (dbCoords) {
      ensureCoordsInit();
      ResultSet rs = connDB2.createStatement()
          .executeQuery("SELECT data FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
      if (rs.next()) {
        json = new JSONObject(JsonSanitizer.sanitize((String) rs.getString(1)));
      }
    }
    return json;
  }

  public Map<String, JSONObject> getAllCoords() throws SQLException {
    Map<String, JSONObject> result = new HashMap<String, JSONObject>();
    synchronized (dbCoords) {
      ensureCoordsInit();
      ResultSet rs = connDB2.createStatement()
          .executeQuery("SELECT * FROM " + COORDS_TABLE_NAME);
      while (rs.next()) {
        result.put(rs.getString(1), new JSONObject(JsonSanitizer.sanitize(rs.getString(2))));
      }
    }
    return result;
  }

  private void removeCoordsData(String id) throws SQLException {
    synchronized (dbCoords) {
      ensureCoordsInit();
      connDB2.createStatement().executeUpdate("DELETE FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
    }
  }

  public synchronized JSONArray getWeeklyTotals() {
    JSONArray result = new JSONArray();
    try {
      ResultSet rs = executeQuery("SELECT DISTINCT year FROM " + RUNS_TABLE_NAME, true);
      List<Integer> years = new ArrayList<Integer>();
      while (rs.next()) {
        years.add(rs.getInt(1));
      }
      Collections.sort(years);
      Calendar current = new GregorianCalendar(TimeZone.getDefault());
      int currentYear = current.get(Calendar.YEAR);
      for (int i = years.size() - 1; i >= 0; --i) {
        rs = executePreparedQuery(
            "SELECT timeRawMs, type, distRaw, eleTotalPos, isExt, parent FROM " + RUNS_TABLE_NAME + " WHERE year=?",
            years.get(i));
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
        while (rs.next()) {
          if (rs.getInt("isExt") == 1 || rs.getString("parent").length() > 0) {
            continue;
          }
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(rs.getLong("timeRawMs"));
          String[] formatted = new String[1];
          int[] idf = CalendarUtils.identifyWeek(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1,
              cal.get(Calendar.YEAR), formatted);
          int week = idf[0];
          JSONObject data = weekly.get(week);
          if ("Empty week".equals(data.getString("info"))) {
            data.put("info", "W" + week + " " + formatted[0]);
          }
          String type = rs.getString("type");
          double dist = rs.getDouble("distRaw");
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
          int totalPositiveEl = rs.getInt("eleTotalPos");
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

  public static boolean isExternal(JSONArray arr) {
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
      ResultSet rs = executeQuery("SELECT DISTINCT year FROM " + RUNS_TABLE_NAME, true);
      List<Integer> years = new ArrayList<Integer>();
      while (rs.next()) {
        years.add(rs.getInt(1));
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
          rs = executeQuery("SELECT month, SUM(distRaw) FROM " + RUNS_TABLE_NAME + " WHERE " + typeFilter +
              "year=" + years.get(i) + " AND isExt=0 AND parent='' GROUP BY month", true);
          while (rs.next()) {
            months[rs.getInt(1)].put(acms[j], String.format("%.3f", rs.getDouble(2)));
            months[rs.getInt(1)].remove("emp");
          }
          rs = executeQuery("SELECT month, COUNT(genby) FROM "
              + RUNS_TABLE_NAME + " WHERE " + typeFilter + " year="
              + years.get(i) + " AND isExt=0 AND parent='' GROUP BY month", true);
          while (rs.next()) {
            months[rs.getInt(1)].put("count" + acms[j], rs.getInt(2));
            months[rs.getInt(1)].put("totalPositiveEl", 0);
            months[rs.getInt(1)].remove("emp");
          }
        }
        rs = executeQuery("SELECT month, SUM(eleTotalPos) FROM " + RUNS_TABLE_NAME + " WHERE year=" +
            years.get(i) + " AND isExt=0 AND parent='' GROUP BY month", true);
        while (rs.next()) {
          int totalPositiveEl = rs.getInt(2);
          if (totalPositiveEl > 0) {
            months[rs.getInt(1)].put("totalPositiveEl", totalPositiveEl);
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

  private void executeCreate(String statement) {
    try {
      conn.createStatement().executeUpdate(statement);
    } catch (SQLException e) {
      System.out.println("Error working with db - table creation");
      e.printStackTrace();
    }
  }

  private void createTablesIfNotExists() {
    executeCreate(createStatementRunsTable);
    executeCreate(CREATE_STATEMENT_COOKIES_TABLE);
    executeCreate(CREATE_STATEMENT_DASHBOARDS_TABLE);
    executeCreate(CREATE_STATEMENT_PRESETS_TABLE);
    executeCreate(CREATE_STATEMENT_FEATURES_TABLE);
    executeCreate(CREATE_STATEMENT_SECURED_TABLE);
    executeCreate(CREATE_STATEMENT_BEST_TABLE);
  }

  synchronized private void fillInFeatures(JSONObject activity) {
    activity.put("descr", "");
    try {
      ResultSet rs = executePreparedQuery("SELECT * FROM " + FEATURES_TABLE_NAME + " WHERE id=?",
          activity.getString("genby"));
      if (rs != null && rs.next()) {
        activity.put("descr", rs.getString("descr"));
        activity.put("links", rs.getString("links"));
      }
    } catch (SQLException e) {
      System.out.println("Error working with features db - retrieving results");
    }
    if (!activity.has("links")) {
      activity.put("links", new JSONArray().toString());
    }
  }

  public synchronized void setFeatures(String id, String descr, List<String> links) throws SQLException {
    JSONArray arr = new JSONArray();
    for (String link : links) {
      arr.put(link);
    }
    ResultSet rs = executePreparedQuery("SELECT * FROM " + FEATURES_TABLE_NAME + " WHERE id=?", id);
    if (rs == null || !rs.next()) {
      executePreparedQuery("INSERT INTO " + FEATURES_TABLE_NAME + " VALUES(?, ?, ?)",
          id, descr, arr.toString());
    } else {
      executePreparedQuery("UPDATE " + FEATURES_TABLE_NAME + " SET descr=?, links=? WHERE id=?",
          descr, arr.toString(), id);
    }
  }

  synchronized private void removeFeatures(String id) throws SQLException {
    executePreparedQuery("DELETE FROM " + FEATURES_TABLE_NAME + " WHERE id=?", id);
  }

  public synchronized boolean addPreset(String name, String types, String pattern, String startDate, String endDate,
      int minDist, int maxDist,
      int top, String dashboard) throws SQLException {
    ResultSet rs = executePreparedQuery("SELECT * FROM " + PRESETS_TABLE_NAME + " WHERE name=?", name);
    if (rs != null && rs.next()) {
      updatePreset(name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard);
      return false;
    }
    executePreparedQuery("INSERT INTO " + PRESETS_TABLE_NAME + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
        name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard);
    return true;
  }

  private void updatePreset(String name, String types, String pattern, String startDate, String endDate, int minDist,
      int maxDist,
      int top, String dashboard) throws SQLException {
    StringBuffer sb = new StringBuffer("UPDATE " + PRESETS_TABLE_NAME + " SET ");
    sb.append("types=?, pattern=?, startDate=?, endDate=?, minDist=?, maxDist=?, top=?, dashboard=? WHERE name=?");
    executePreparedQuery(sb.toString(), types, pattern, startDate, endDate, minDist, maxDist, top, dashboard, name);
  }

  public synchronized void renamePreset(String name, String newName) throws SQLException {
    ResultSet rs = executePreparedQuery("SELECT * FROM " + PRESETS_TABLE_NAME + " WHERE name=?", newName);
    if (rs != null && rs.next()) {
      throw new IllegalArgumentException("Preset " + newName + " already exists");
    }
    executePreparedQuery("UPDATE " + PRESETS_TABLE_NAME + " SET name=? WHERE name=?", newName, name);
  }

  public synchronized void removePreset(String name) throws SQLException {
    executePreparedQuery("DELETE FROM " + PRESETS_TABLE_NAME + " WHERE name=?", name);
  }

  public synchronized JSONObject getPresetData(String name) {
    try {
      ResultSet rs = executePreparedQuery("SELECT * FROM " + PRESETS_TABLE_NAME
          + " WHERE name=?", name);
      if (rs == null || !rs.next()) {
        return null;
      }
      JSONObject preset = new JSONObject();
      preset.put("name", rs.getString("name"));
      preset.put("pattern", rs.getString("pattern"));
      preset.put("startDate", rs.getString("startDate"));
      preset.put("endDate", rs.getString("endDate"));
      preset.put("minDist", rs.getInt("minDist"));
      preset.put("maxDist", rs.getInt("maxDist"));
      preset.put("top", rs.getInt("top"));
      preset.put("dashboard", rs.getString("dashboard"));
      String types = rs.getString("types");
      StringTokenizer st = new StringTokenizer(types, ",", false);
      while (st.hasMoreTokens()) {
        String next = st.nextToken().trim();
        if (next.length() > 0) {
          preset.put(next, true);
        }
      }
      return preset;
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public synchronized JSONArray getPresets(Map<String, JSONObject> out) {
    JSONArray result = new JSONArray();
    ResultSet rs = executeQuery("SELECT * FROM " + PRESETS_TABLE_NAME, true);
    if (rs == null) {
      return result;
    }
    try {
      while (rs.next()) {
        JSONObject preset = new JSONObject();
        preset.put("name", rs.getString("name"));
        preset.put("pattern", rs.getString("pattern"));
        preset.put("startDate", rs.getString("startDate"));
        preset.put("endDate", rs.getString("endDate"));
        preset.put("minDist", rs.getInt("minDist"));
        preset.put("maxDist", rs.getInt("maxDist"));
        preset.put("top", rs.getInt("top"));
        preset.put("dashboard", rs.getString("dashboard"));
        String types = rs.getString("types");
        if (out != null) {
          preset.put("types", types);
        } else {
          StringTokenizer st = new StringTokenizer(types, ",", false);
          while (st.hasMoreTokens()) {
            String next = st.nextToken().trim();
            if (next.length() > 0) {
              preset.put(next, true);
            }
          }
        }
        if (out != null) {
          out.put(preset.getString("name"), preset);
        } else {
          result.put(preset);
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
    executeQueryExc("DELETE FROM " + DASHBOARDS_TABLE_NAME, false);
    for (String dashboard : dashboards) {
      executePreparedQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES(?)", dashboard);
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
    StringBuffer sb = new StringBuffer();
    sb.append("INSERT INTO " + RUNS_TABLE_NAME + " VALUES (");
    if (!entry.has("origData")) {
      entry.put("origData", new JSONObject());
    }
    Object[] values = new Object[KEYS.length];
    for (int i = 0; i < KEYS.length; ++i) {
      String str = entry.get(KEYS[i]).toString();
      if ("real".equals(TYPES[i])) {
        values[i] = Double.valueOf(str);
      } else if ("text".equals(TYPES[i])) {
        values[i] = str;
      } else { // integer
        values[i] = Long.valueOf(str);
      }
      sb.append(" ?");
      if (i < KEYS.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(')');
    executePreparedQuery(sb.toString(), values);
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

  public synchronized List<JSONObject> fetchActivities(boolean run, boolean trail, boolean uphill, boolean hike,
      boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    List<JSONObject> result = new ArrayList<JSONObject>();
    StringBuffer selectClause = new StringBuffer();
    StringBuffer whereClause = new StringBuffer();
    selectClause.append("SELECT * FROM " + RUNS_TABLE_NAME);
    whereClause.append("WHERE ");
    whereClause.append("(distRaw >= " + minDistance + " AND distRaw <= " + maxDistance + ") ");
    if (run || trail || uphill || hike || walk || other) {
      whereClause.append(" AND ");
      whereClause.append('(');
      List<String> types = new ArrayList<String>();
      if (run) {
        types.add(Constants.RUNNING);
      }
      if (trail) {
        types.add(Constants.TRAIL);
      }
      if (uphill) {
        types.add(Constants.UPHILL);
      }
      if (hike) {
        types.add(Constants.HIKING);
      }
      if (walk) {
        types.add(Constants.WALKING);
      }
      if (other) {
        types.add(Constants.OTHER);
      }
      for (int i = 0; i < types.size(); ++i) {
        whereClause.append("type = '" + types.get(i) + '\'');
        if (i < types.size() - 1) {
          whereClause.append(" OR ");
        }
      }
      whereClause.append(')');
    } else {
      return Collections.emptyList();
    }
    if (startDate != null) {
      int yr = startDate.get(Calendar.YEAR);
      int mt = startDate.get(Calendar.MONTH);
      int d = startDate.get(Calendar.DAY_OF_MONTH);
      whereClause.append(" AND (");
      whereClause.append("(YEAR > " + yr + ") OR ");
      whereClause.append("(YEAR = " + yr + " AND MONTH > " + mt + ") OR ");
      whereClause.append("(YEAR = " + yr + " AND MONTH = " + mt + " AND DAY >= " + d + ")");
      whereClause.append(')');
    }
    if (endDate != null) {
      int yr = endDate.get(Calendar.YEAR);
      int mt = endDate.get(Calendar.MONTH);
      int d = endDate.get(Calendar.DAY_OF_MONTH);
      whereClause.append(" AND (");
      whereClause.append("(YEAR < " + yr + ") OR ");
      whereClause.append("(YEAR = " + yr + " AND MONTH < " + mt + ") OR ");
      whereClause.append("(YEAR = " + yr + " AND MONTH = " + mt + " AND DAY <= " + d + ")");
      whereClause.append(')');
    }
    selectClause.append(' ' + whereClause.toString());
    selectClause.append(" ORDER BY timeRawMs DESC");
    StringBuffer aggQuery = new StringBuffer();
    aggQuery.append("SELECT SUM(distRaw), SUM(timeTotalRaw), SUM(eleTotalPos), SUM(eleTotalNeg), SUM(distRunningRaw)");
    aggQuery.append("FROM (" + selectClause + ')');
    ResultSet rs = executeQuery(aggQuery.toString(), true);
    JSONObject totals = new JSONObject();
    try {
      totals.put("totalDistance", rs.getDouble(1));
      totals.put("totalTime", rs.getLong(2));
      totals.put("elePos", rs.getLong(3));
      totals.put("eleNeg", rs.getLong(4));
      totals.put("totalRunDist", rs.getDouble(5));
    } catch (SQLException ignore) {
      // no data
    }
    result.add(totals);
    rs = executeQuery(selectClause.toString(), true);
    JSONObject json = null;
    try {
      while ((json = readActivity(rs, false)) != null) {
        result.add(json);
      }
    } catch (Exception ignore) {
      // silent catch
    }
    return result;
  }
  
  public synchronized List<JSONObject> getAllActivities() {
    List<JSONObject> result = new ArrayList<JSONObject>();
    ResultSet rs = executeQuery("SELECT * FROM " + RUNS_TABLE_NAME, true);
    JSONObject json = null;
    try {
      while ((json = readActivity(rs, true)) != null) {
        result.add(json);
      }
    } catch (Exception ignore) {
      // silent catch
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
    executePreparedQuery("DELETE FROM " + RUNS_TABLE_NAME + " WHERE genby=?", fileName);
    executePreparedQuery("DELETE FROM " + SECURED_TABLE_NAME + " WHERE id=?", fileName);
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
    conn.createStatement().executeUpdate("DROP TABLE IF EXISTS " + BEST_TABLE_NAME);
    conn = null;
  }

  public synchronized boolean isSecured(String fileName) {
    try {
      ResultSet rs = executePreparedQuery("SELECT * FROM " + SECURED_TABLE_NAME + " WHERE id=?", fileName);
      return rs != null && rs.next();
    } catch (SQLException se) {
      System.out.println("Error checking secured flag");
    }
    return true;
  }

  public synchronized void setSecureFlag(String fileName, boolean flag) throws SQLException {
    boolean isSecured = isSecured(fileName);
    if (flag) {
      if (!isSecured) {
        executePreparedQuery("INSERT INTO " + SECURED_TABLE_NAME + " VALUES(?)", fileName);
      }
    } else if (isSecured) {
      executePreparedQuery("DELETE FROM " + SECURED_TABLE_NAME + " WHERE id=?", fileName);
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
      return readActivity(executePreparedQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE genby=?", fileName), true);
    } catch (Exception e) {
      System.out.println("Error reading activity " + fileName);
      e.printStackTrace();
    }
    return null;
  }

  public synchronized boolean hasActivity(String fileName) {
    try {
      return executePreparedQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE genby=?", fileName).next();
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
      ResultSet rs = executeQuery("SELECT genby, date, timeTotal, timeTotalRaw, isExt, parent FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distMin + " AND distRaw <= " + distMax + " AND type!='" + Constants.OTHER + "') ORDER BY timeTotalRaw", true);
      double bestTime = Double.MAX_VALUE;
      while (rs != null && rs.next()) {
        if (rs.getInt("isExt") == 1 || rs.getString("parent").length() > 0) {
          continue;
        }
        JSONObject result = new JSONObject();
        result.put("date", rs.getString("date"));
        result.put("genby", rs.getString("genby"));
        result.put("timeTotal", rs.getString("timeTotal"));
        best = result;
        bestTime = rs.getDouble("timeTotalRaw");
        break;
      }
      if (bestTime != Double.MAX_VALUE) {
        return best;
      }
      rs = executeQuery("SELECT genby, date, splits, isExt, parent FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distActual + " AND type!='" + Constants.OTHER + "')", true);
      int distFloor = (int) distActual;
      while (rs != null && rs.next()) {
        if (rs.getInt("isExt") == 1 || rs.getString("parent").length() > 0) {
          continue;
        }
        if (best != null && best.getString("genby").equals(rs.getString("genby"))) {
          continue;
        }
        JSONArray splits = new JSONArray(JsonSanitizer.sanitize(rs.getString("splits")));
        for (int i = Math.max(distFloor - 2, 0); i < Math.min(splits.length(), distFloor + 2); ++i) {
          JSONObject split = splits.getJSONObject(i);
          double dist = split.getDouble("totalRaw");
          if (dist >= distFloor) {
            double timeTotalEst = split.getDouble("timeTotalRaw") * (distActual / dist);
            if (timeTotalEst < bestTime) {
              bestTime = timeTotalEst;
              JSONObject result = new JSONObject();
              result.put("date", rs.getString("date"));
              result.put("genby", rs.getString("genby"));
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
    ResultSet rs = executeQuery("SELECT name, date, splits, genby, isExt, parent FROM " + RUNS_TABLE_NAME +
        " WHERE (type='Running' OR type='Trail')", true);
    try {
      while (rs.next()) {
        if (rs.getInt("isExt") == 1 || rs.getString("parent").length() > 0) {
          continue;
        }
        JSONObject crnt = new JSONObject();
        crnt.put("name", rs.getString(1));
        crnt.put("date", rs.getString(2));
        crnt.put("splits", new JSONArray(JsonSanitizer.sanitize(rs.getString(3))));
        crnt.put("genby", rs.getString(4));
        result.put(crnt);
      }
    } catch (SQLException e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return result;
  }

  public synchronized void addToDashboard(String activity, String dashboard) throws SQLException {
    ResultSet rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", dashboard);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard not found");
    }
    rs = executePreparedQuery("SELECT dashboards FROM " + RUNS_TABLE_NAME + " WHERE genby=?", activity);
    if (!rs.next()) {
      throw new IllegalArgumentException("Activity not found");
    }
    JSONArray dashboards = new JSONArray(JsonSanitizer.sanitize(rs.getString(1)));
    if (CommonUtils.find(dashboards, dashboard) != -1) {
      throw new IllegalArgumentException("Activity already in dashboard");
    }
    dashboards.put(dashboard);
    executePreparedQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards=? WHERE genby=?",
        dashboards.toString(), activity);
  }

  public synchronized void removeFromDashboard(String activity, String dashboard) throws SQLException {
    ResultSet rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", dashboard);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard not found");
    }
    rs = executePreparedQuery("SELECT dashboards FROM " + RUNS_TABLE_NAME + " WHERE genby=?", activity);
    if (!rs.next()) {
      throw new IllegalArgumentException("Activity not found");
    }
    JSONArray dashboards = new JSONArray(JsonSanitizer.sanitize(rs.getString(1)));
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
    executePreparedQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES(?)", name);
  }

  public synchronized void renameDashboard(String name, String newName) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (Constants.MAIN_DASHBOARD.equals(name) || Constants.MAIN_DASHBOARD.equals(newName)) {
      throw new IllegalArgumentException("Cannot rename main dashboard");
    }
    ResultSet rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", newName);
    if (rs.next()) {
      throw new IllegalArgumentException("Dashboard " + newName + " already exists");
    }
    rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", name);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard " + name + " doest not exist");
    }

    executePreparedQuery("UPDATE " + DASHBOARDS_TABLE_NAME + " SET name=? WHERE name=?", newName, name);
    rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
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
    executePreparedQuery("DELETE FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", name);
    ResultSet rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
    while (rs.next()) {
      String dash = rs.getString(2);
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
        String genby = rs.getString(1);
        executePreparedQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards=? WHERE genby=?",
            arr.toString(), genby);
      }
    }
    executePreparedQuery(
        "UPDATE " + PRESETS_TABLE_NAME + " SET dashboard='" + Constants.MAIN_DASHBOARD + "' WHERE dashboard=?",
        name);
  }

  public synchronized Map<Integer, BestSplitAch> retrieveBestSplits() throws SQLException {
    Map<Integer, BestSplitAch> result = new HashMap<Integer, BestSplitAch>();
    ResultSet rs = executePreparedQuery("SELECT * FROM " + BEST_TABLE_NAME);
    while (rs.next()) {
      BestSplitAch ach = new BestSplitAch(rs.getString("genby"), rs.getDouble("startpoint"), rs.getLong("time"));
      result.put(Integer.valueOf(rs.getString("dist")), ach);
    }
    return result;
  }

  public synchronized void updateBestSplits(Map<Integer, BestSplitAch> best, boolean reset) throws SQLException {
    ResultSet rs = null;
    if (reset) {
      executeQuery("DELETE FROM " + BEST_TABLE_NAME, false);
    }
    for (Entry<Integer, BestSplitAch> entry : best.entrySet()) {
      BestSplitAch ach = entry.getValue();
      rs = executePreparedQuery("SELECT dist FROM " + BEST_TABLE_NAME + " WHERE dist=?", entry.getKey());
      if (rs == null || !rs.next()) {
        executePreparedQuery("INSERT INTO " + BEST_TABLE_NAME + " VALUES(?, ?, ?, ?)", entry.getKey(),
            ach.getId(), ach.getTime(), ach.getStartPoint());
      } else {
        executePreparedQuery("UPDATE " + BEST_TABLE_NAME + " SET genby=?, time=?, startpoint=? WHERE dist=?",
            ach.getId(), ach.getTime(), ach.getStartPoint(), entry.getKey());
      }
    }
  }

  public synchronized JSONObject getDashboards() {
    JSONArray arr = new JSONArray();
    try {
      ResultSet rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME, true);
      if (rs == null) {
        executeQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES('" + Constants.MAIN_DASHBOARD + "')", false);
        rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME, true);
      }
      while (rs.next()) {
        arr.put(rs.getString(1));
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
      ResultSet rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME +
          " WHERE name=?", dashboard);
      return rs != null && rs.next();
    } catch (Exception e) {
      System.out.println("Error saving cookie");
      e.printStackTrace();
    }
    return false;
  }

  public synchronized boolean saveCookie(String uid, Calendar expires) {
    try {
      boolean hasCookie = executePreparedQuery("SELECT uid FROM " + COOKIES_TABLE_NAME + " WHERE uid=?", uid)
          .next();
      if (!hasCookie) {
        executePreparedQuery("INSERT INTO " + COOKIES_TABLE_NAME + " VALUES (?, ?)",
            uid, expires.get(Calendar.YEAR) + "-" + expires.get(Calendar.MONTH) + "-" + expires.get(Calendar.DATE));
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
      ResultSet rs = executePreparedQuery("SELECT expires FROM " + COOKIES_TABLE_NAME + " WHERE uid=?", uid);
      if (!rs.next()) {
        return false;
      }
      if (isCookieValid(rs.getString(1))) {
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
    executePreparedQuery("DELETE FROM " + COOKIES_TABLE_NAME + " WHERE uid=?", uid);
  }

  public synchronized void checkForExpiredCookies() {
    ResultSet rs = executeQuery("SELECT * FROM " + COOKIES_TABLE_NAME, true);
    try {
      while (rs.next()) {
        String uid = rs.getString(1);
        if (!isCookieValid(rs.getString(2))) {
          deleteCookie(uid);
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
      ResultSet rs = executePreparedQuery(
          "SELECT dashboards, type, distRaw, avgSpeedRaw FROM " + RUNS_TABLE_NAME + " WHERE genby=?",
          activity);
      if (rs == null || !rs.next()) {
        return null;
      }
      type = rs.getString("type");
      dist = rs.getDouble("distRaw");
      speed = rs.getDouble("avgSpeedRaw");
      JSONArray arr = new JSONArray(JsonSanitizer.sanitize(rs.getString("dashboards")));
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
      rs = executeQueryExc("SELECT genby, dashboards, type, distRaw, avgSpeedRaw FROM " + RUNS_TABLE_NAME, true);
      while (rs != null && rs.next()) {
        if (activity.equals(rs.getString("genby"))) {
          continue;
        }
        if (!areTypesCompatible(type, rs.getString("type"))) {
          continue;
        }
        double sspeed = rs.getDouble("avgSpeedRaw");
        if (dist < 25 && (0.66 * speed > sspeed || 1.5 * speed < sspeed)) {
          continue;
        }
        arr = new JSONArray(JsonSanitizer.sanitize(rs.getString("dashboards")));
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
        entries.add(new DataEntry(rs.getString("genby"), Math.abs(dist - rs.getDouble("distRaw"))));
      }
      Collections.sort(entries);
      int len = searchOnlyExt ? entries.size() : Math.min(10, entries.size());
      arr = new JSONArray();
      for (int i = 0; i < len; ++i) {
        String id = entries.get(i).getId();
        rs = executePreparedQuery("SELECT name, type, date, dist FROM " + RUNS_TABLE_NAME + " WHERE genby=?", id);
        if (rs != null && rs.next()) {
          JSONObject cr = new JSONObject();
          cr.put("id", id);
          cr.put("text", rs.getString("name") + ' ' + rs.getString("type") + ", " + rs.getString("date")
              + "| " + rs.getString("dist") + "km");
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
      if (connDB2 != null) {
        connDB2.close();
      }
    } catch (SQLException ignore) {
      // silent catch
    }
    conn = null;
    connDB2 = null;
  }

  public synchronized void cleanupReliveCCBefore(Calendar cal) {
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int month = cal.get(Calendar.MONTH);
    int year = cal.get(Calendar.YEAR);
    StringBuffer whereClause = new StringBuffer();
    whereClause.append("(YEAR < " + year + ") OR ");
    whereClause.append("(YEAR = " + year + " AND MONTH < " + month + ") OR ");
    whereClause.append("(YEAR = " + year + " AND MONTH = " + month + " AND DAY <= " + day + ")");
    executeQuery("UPDATE " + RUNS_TABLE_NAME + " SET ccLink='none' WHERE " + whereClause.toString(), false);
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
