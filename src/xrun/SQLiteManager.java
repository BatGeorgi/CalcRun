package xrun;

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
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SQLiteManager {
  
  private static final String RUNS_TABLE_NAME = "runs";
  private static final String COOKIES_TABLE_NAME = "cookies";
  private static final String DASHBOARDS_TABLE_NAME = "dashboards";
  private static final String PRESETS_TABLE_NAME = "presets";
  private static final String COORDS_TABLE_NAME = "coords";
  private static final String FEATURES_TABLE_NAME = "features";
  private static final String SECURED_TABLE_NAME = "secured";
  
  static final String EXTERNAL_DASHBOARD = "External";
  static final String MAIN_DASHBOARD = "Main";
  
  private static final String[] KEYS = new String[] {
      "genby", "name", "type", "date", "year", "month", "day", "dist", "distRaw",
      "starttime", "timeTotal", "timeTotalRaw", "timeRawMs", "timeRunning", "timeRest",
      "avgSpeed", "avgSpeedRaw", "avgPace", "distRunning", "distRunningRaw",
      "eleTotalPos", "eleTotalNeg", "eleRunningPos", "eleRunningNeg",
      "garminLink", "ccLink", "photosLink",
      "dashboards", "isExt",
      "speedDist", "splits",
      "origData"
  };
  private static final String[] TYPES = new String[] {
      "text", "text", "text", "text", "integer", "integer", "integer", "text", "real",
      "text", "text", "real", "integer", "text", "text",
      "text", "real", "text", "text", "real",
      "integer", "integer", "integer", "integer",
      "text", "text", "text",
      "text", "integer",
      "text", "text",
      "text"
  };
  private static final String DB_FILE_PREF = "activities";
  
  private static final String CREATE_STATEMENT_COOKIES_TABLE = "CREATE TABLE IF NOT EXISTS " + COOKIES_TABLE_NAME + 
      "(uid PRIMARY KEY NOT NULL, expires NOT NULL)";
  private static final String CREATE_STATEMENT_DASHBOARDS_TABLE = "CREATE TABLE IF NOT EXISTS " + DASHBOARDS_TABLE_NAME + 
      "(name PRIMARY KEY NOT NULL)";
  private static final String CREATE_STATEMENT_PRESETS_TABLE = "CREATE TABLE IF NOT EXISTS " + PRESETS_TABLE_NAME + 
      "(name text NOT NULL, types text NOT NULL, pattern text NOT NULL, startDate text NOT NULL, endDate text NOT NULL, "
      + "minDist integer NOT NULL, maxDist integer NOT NULL, top integer NOT NULL, dashboard text NOT NULL)";
  private static final String CREATE_STATEMENT_FEATURES_TABLE = "CREATE TABLE IF NOT EXISTS " + FEATURES_TABLE_NAME + 
      "(id text NOT NULL, descr text not null, links text NOT NULL)";
  private static final String CREATE_STATEMENT_SECURED_TABLE = "CREATE TABLE IF NOT EXISTS " + SECURED_TABLE_NAME + 
      "(id text NOT NULL)";

	private File dbActivities;
	private File dbCoords;
	private String createStatementRunsTable;
	private Connection conn = null;
	private Connection connDB2 = null;

	SQLiteManager(File base) {
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
		cr.append("CREATE TABLE IF NOT EXISTS "+ RUNS_TABLE_NAME + " (");
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
	
  private void addCoordsData(String id, JSONArray lats, JSONArray lons, JSONArray times, JSONArray markers) {
    try {
      synchronized (dbCoords) {
        ensureCoordsInit();
        JSONObject json = new JSONObject();
        json.put("lats", lats);
        json.put("lons", lons);
        json.put("times", times);
        json.put("markers", markers);
        String str = json.toString();
        ResultSet rs = connDB2.createStatement().executeQuery("SELECT * FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
        if (!rs.next()) {
          connDB2.createStatement().executeUpdate("INSERT INTO " + COORDS_TABLE_NAME + " VALUES ('" + id + "', '" + str + "')");
        } else {
          connDB2.createStatement().executeUpdate("UPDATE " + COORDS_TABLE_NAME + " SET data='" + str + "' WHERE id='" + id + "'");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  JSONObject getCoordsData(String id) {
    JSONObject json = null;
    try {
      synchronized (dbCoords) {
        ensureCoordsInit();
        ResultSet rs = connDB2.createStatement().executeQuery("SELECT data FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
        if (rs.next()) {
          json = new JSONObject((String) rs.getString(1));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return json;
  }

  private void removeCoordsData(String id) {
    try {
      synchronized (dbCoords) {
        ensureCoordsInit();
        connDB2.createStatement().executeUpdate("DELETE FROM " + COORDS_TABLE_NAME + " WHERE id='" + id + "'");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  synchronized JSONArray getWeeklyTotals() {
    JSONArray result = new JSONArray();
    try {
      ResultSet rs = executeQuery("SELECT DISTINCT year FROM " + RUNS_TABLE_NAME, true);
      List<Integer> years = new ArrayList<Integer>();
      while (rs.next()) {
        years.add(rs.getInt(1));
      }
      rs = executeQuery("SELECT * FROM " + RUNS_TABLE_NAME, true);
      Collections.sort(years);
      Calendar current = new GregorianCalendar(TimeZone.getDefault());
      int currentYear = current.get(Calendar.YEAR);
      for (int i = years.size() - 1; i >= 0; --i) {
        rs = executeQuery("SELECT timeRawMs, type, distRaw, eleTotalPos, isExt FROM " + RUNS_TABLE_NAME + " WHERE year=" + years.get(i), true);
        Map<Integer, JSONObject> weekly = new HashMap<Integer, JSONObject>();
        int maxWeek = 100;
        if (years.get(i) == currentYear) {
          maxWeek = WeekCalc.identifyWeek(current.get(Calendar.DAY_OF_MONTH), current.get(Calendar.MONTH) + 1, current.get(Calendar.YEAR), new String[1])[0];
        } else {
        	maxWeek = WeekCalc.getWeekCount(years.get(i));
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
          if (rs.getInt("isExt") == 1) {
            continue;
          }
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(rs.getLong("timeRawMs"));
          String[] formatted = new String[1];
          int[] idf = WeekCalc.identifyWeek(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR), formatted);
          int week = idf[0];
          JSONObject data = weekly.get(week);
          if ("Empty week".equals(data.getString("info"))) {
            data.put("info", "W" + week + " " + formatted[0]);
          }
          String type = rs.getString("type");
          double dist = rs.getDouble("distRaw");
          if (RunCalcUtils.RUNNING.equals(type)) {
            data.put("r", data.getDouble("r") + dist);
            data.put("rt", data.getDouble("rt") + dist);
            data.put("countr", data.getInt("countr") + 1);
            data.put("countrt", data.getInt("countrt") + 1);
          } else if (RunCalcUtils.TRAIL.equals(type)) {
            data.put("t", data.getDouble("t") + dist);
            data.put("rt", data.getDouble("rt") + dist);
            data.put("countt", data.getInt("countt") + 1);
            data.put("countrt", data.getInt("countrt") + 1);
          } else if (RunCalcUtils.UPHILL.equals(type)) {
            data.put("u", data.getDouble("u") + dist);
            data.put("countu", data.getInt("countu") + 1);
          } else if (RunCalcUtils.HIKING.equals(type)) {
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
  
  private static boolean isExternal(String dashboards) {
    JSONArray arr = new JSONArray(dashboards);
    for (int i = 0; i < arr.length(); ++i) {
      if (EXTERNAL_DASHBOARD.equals(arr.getString(i))) {
        return true;
      }
    }
    return false;
  }
	
	synchronized JSONArray getMonthlyTotals() {
	  JSONArray result = new JSONArray();
	  try {
	    ResultSet rs = executeQuery("SELECT DISTINCT year FROM " + RUNS_TABLE_NAME, true);
	    List<Integer> years = new ArrayList<Integer>();
	    while (rs.next()) {
        years.add(rs.getInt(1));
      }
	    Collections.sort(years);
	    String[] filters = new String[] {"type='" + RunCalcUtils.RUNNING + '\'',
	        "type='" + RunCalcUtils.TRAIL + '\'',
	        "type='" + RunCalcUtils.UPHILL + '\'',
	        "type='" + RunCalcUtils.HIKING + '\'',
	        "type='" + RunCalcUtils.RUNNING + "' OR type='" + RunCalcUtils.TRAIL + '\'',
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
	        months[j].put("name", CalcDist.MONTHS[j] + ' ' + year);
	        for (int k = 0; k < acms.length; ++k) {
	          months[j].put(acms[k], "0");
	          months[j].put("count" + acms[k], 0);
	        }
	      }
        for (int j = 0; j < filters.length; ++j) {
          String typeFilter = (filters[j] != null ? ("(" + filters[j] + ") AND ") : "");
          rs = executeQuery("SELECT month, SUM(distRaw) FROM " + RUNS_TABLE_NAME + " WHERE " + typeFilter +
              "year=" + years.get(i) + " AND isExt=0 GROUP BY month", true);
	        while (rs.next()) {
	          months[rs.getInt(1)].put(acms[j], String.format("%.3f", rs.getDouble(2)));
	          months[rs.getInt(1)].remove("emp");
	        }
					rs = executeQuery("SELECT month, COUNT(genby) FROM "
							+ RUNS_TABLE_NAME + " WHERE " + typeFilter + " year="
							+ years.get(i) + " AND isExt=0 GROUP BY month", true);
					while (rs.next()) {
						months[rs.getInt(1)].put("count" + acms[j], rs.getInt(2));
						months[rs.getInt(1)].put("totalPositiveEl", 0);
						months[rs.getInt(1)].remove("emp");
					}
	      }
        rs = executeQuery("SELECT month, SUM(eleTotalPos) FROM " + RUNS_TABLE_NAME + " WHERE year=" +
                years.get(i) + " AND isExt=0 GROUP BY month", true);
				while (rs.next()) {
					int totalPositiveEl = rs.getInt(2);
					if (totalPositiveEl > 0) {
						months[rs.getInt(1)].put("totalPositiveEl", totalPositiveEl);
					}
				}
        for (int j = 0; j < months.length; ++j) {
          JSONObject monthData = new JSONObject();
          monthData.put("month", CalcDist.MONTHS[j]);
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
	
	synchronized void ensureActivitiesInit() throws SQLException {
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
	}
	
	synchronized private void fillInFeatures(JSONObject activity) {
	  activity.put("descr", "");
	  ResultSet rs = executeQuery("SELECT * FROM " + FEATURES_TABLE_NAME + " WHERE id='" + activity.getString("genby") + "'", true);
    try {
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
	
	synchronized void setFeatures(String id, String descr, List<String> links) throws SQLException {
	  JSONArray arr = new JSONArray();
	  for (String link : links) {
	    arr.put(link);
	  }
	  ResultSet rs = executePreparedQuery("SELECT * FROM " + FEATURES_TABLE_NAME + " WHERE id=?", new Object[] {id});
	  if (rs == null || !rs.next()) {
	    executePreparedQuery("INSERT INTO " + FEATURES_TABLE_NAME + " VALUES(?, ?, ?)",
	    		new Object[] {id, descr, arr.toString()});
	  } else {
	    executePreparedQuery("UPDATE " + FEATURES_TABLE_NAME + " SET descr=?, links=? WHERE id=?",
	    		new Object[] {descr, arr.toString(), id});
	  }
	}
	
	synchronized private void removeFeatures(String id) throws SQLException {
	  executePreparedQuery("DELETE FROM " + FEATURES_TABLE_NAME + " WHERE id=?", new Object[] {id});
	}
	
	synchronized boolean addPreset(String name, String types, String pattern, String startDate, String endDate, int minDist, int maxDist,
	    int top, String dashboard) throws SQLException {
	  ResultSet rs = executeQuery("SELECT * FROM " + PRESETS_TABLE_NAME + " WHERE name='" + name + "'", true);
	  if (rs != null && rs.next()) {
	    updatePreset(name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard);
	    return false;
	  }
		executePreparedQuery("INSERT INTO " + PRESETS_TABLE_NAME + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
				new Object[] {name, types, pattern, startDate, endDate, minDist, maxDist, top, dashboard});
		return true;
	}
	
	private void updatePreset(String name, String types, String pattern, String startDate, String endDate, int minDist, int maxDist,
      int top, String dashboard) throws SQLException {
	  StringBuffer sb = new StringBuffer("UPDATE " + PRESETS_TABLE_NAME + " SET ");
    sb.append("types=?, pattern=?, startDate=?, endDate=?, minDist=?, maxDist=?, top=?, dashboard=? WHERE name=?");
    executePreparedQuery(sb.toString(), new Object[] {types, pattern, startDate, endDate, minDist, maxDist, top, dashboard, name});
	}
	
	synchronized void renamePreset(String name, String newName) throws SQLException {
	  ResultSet rs = executeQuery("SELECT * FROM " + PRESETS_TABLE_NAME + " WHERE name='" + newName + "'", true);
	  if (rs != null && rs.next()) {
	    throw new IllegalArgumentException("Preset " + newName + " already exists");
	  }
		executePreparedQuery("UPDATE " + PRESETS_TABLE_NAME + " SET name=? WHERE name=?", new Object[] {newName, name});
	}
	
	synchronized void removePreset(String name) throws SQLException {
		executePreparedQuery("DELETE FROM " + PRESETS_TABLE_NAME + " WHERE name=?", new Object[] {name});
	}
	
	synchronized JSONObject getPresetData(String name) {
		ResultSet rs = executeQuery("SELECT * FROM " + PRESETS_TABLE_NAME
				+ " WHERE name='" + name + "'", true);
		try {
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
	
	synchronized JSONArray getPresets(Map<String, JSONObject> out) {
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
	
	synchronized void reorderPresets(List<String> presets) throws SQLException {
	  Map<String, JSONObject> current = new LinkedHashMap<String, JSONObject>();
	  getPresets(current);
	  executeQueryExc("DELETE FROM " + PRESETS_TABLE_NAME, false);
	  for (String preset : presets) {
	    JSONObject data = current.remove(preset);
	    if (data != null) {
	      addPreset(preset, data.getString("types"), data.getString("pattern"), data.getString("startDate"), data.getString("endDate"),
	          data.getInt("minDist"), data.getInt("maxDist"), data.getInt("top"), data.getString("dashboard"));
	    }
	  }
	  for (JSONObject data : current.values()) {
	    addPreset(data.getString("name"), data.getString("types"), data.getString("pattern"), data.getString("startDate"), data.getString("endDate"),
          data.getInt("minDist"), data.getInt("maxDist"), data.getInt("top"), data.getString("dashboard"));
	  }
	}
	
	synchronized void reorderDashboards(List<String> dashboards) throws SQLException {
    executeQueryExc("DELETE FROM " + DASHBOARDS_TABLE_NAME, false);
    for (String dashboard : dashboards) {
      executePreparedQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES(?)", new Object[] {dashboard});
    }
  }
	
	synchronized void addActivity(JSONObject entry) throws SQLException {
	  boolean isExt = isExternal(entry.getJSONArray("dashboards").toString());
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
      addCoordsData(entry.getString("genby"), entry.getJSONArray("lats"), entry.getJSONArray("lons"), entry.getJSONArray("times"),
      		entry.getJSONArray("markers"));
    }
	}
	
	private ResultSet executePreparedQuery(String statement, Object[] values)
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
	    activity.put(KEYS[i], rs.getObject(i + 1));
	  }
	  if (includeSplitsAndDistr) {
	    activity.put(KEYS[len - 3], new JSONArray(rs.getString(len - 2)));
	    activity.put(KEYS[len - 2], new JSONArray(rs.getString(len - 1)));
	  }
	  String origData = rs.getString(len);
	  activity.put(KEYS[len - 1], (origData != null ? new JSONObject(origData) : new JSONObject()));
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
	  long corr = TimeZone.getDefault().inDaylightTime(cal.getTime()) ? CalcDist.CORRECTION_BG_SUMMER : CalcDist.CORRECTION_BG_WINTER;
	  cal.setTimeInMillis(activity.getLong("timeRawMs") + corr);
	  activity.put("startAt", CalcDist.formatDate(cal, true));
	  fillInFeatures(activity);
	  return activity;
	}
	
	synchronized List<JSONObject> fetchActivities(boolean run, boolean trail, boolean uphill, boolean hike, boolean walk, boolean other,
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
        types.add(RunCalcUtils.RUNNING);
      }
      if (trail) {
        types.add(RunCalcUtils.TRAIL);
      }
      if (uphill) {
        types.add(RunCalcUtils.UPHILL);
      }
      if (hike) {
        types.add(RunCalcUtils.HIKING);
      }
      if (walk) {
        types.add(RunCalcUtils.WALKING);
      }
      if (other) {
        types.add(RunCalcUtils.OTHER);
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
	
	synchronized void updateActivity(String fileName, String newName, String newType, String newGarmin, String newCC, String newPhotos, boolean secure) throws SQLException {
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
	  sb.append("UPDATE " + RUNS_TABLE_NAME + " SET name = ?, type = ?, garminLink = ?, ccLink = ?, photosLink = ? WHERE genby = ?");
	  executePreparedQuery(sb.toString(), new Object[] {newName, newType, newGarmin, newCC, newPhotos, fileName});
	  setSecureFlag(fileName, secure);
	}
	
	synchronized void deleteActivity(String fileName) {
	  executeQuery("DELETE FROM " + RUNS_TABLE_NAME + " WHERE genby='" + fileName + '\'', false);
	  executeQuery("DELETE FROM " + SECURED_TABLE_NAME + " WHERE id='" + fileName + '\'', false);
	  try {
	    removeFeatures(fileName);
	  } catch (Exception e) {
	    System.out.println("Error removing features for " + fileName);
      e.printStackTrace();
	  }
	  removeCoordsData(fileName);
	}
	
	synchronized boolean isSecured(String fileName) {
		try {
			ResultSet rs = executeQuery("SELECT * FROM " + SECURED_TABLE_NAME + " WHERE id='" + fileName + "'", true);
			return rs != null && rs.next();
		} catch (SQLException se) {
			System.out.println("Error checking secured flag");
		}
		return true;
	}
	
	synchronized void setSecureFlag(String fileName, boolean flag) {
		boolean isSecured = isSecured(fileName);
		if (flag) {
			if (!isSecured) {
				executeQuery("INSERT INTO " + SECURED_TABLE_NAME + " VALUES('" + fileName + "')", false);
			}
		} else if (isSecured) {
			executeQuery("DELETE FROM " + SECURED_TABLE_NAME + " WHERE id='" + fileName + "'", false);
		}
	}
	
	File getActivitiesDBFile() {
	  return dbActivities;
	}
	
	File getCoordsDBFile() {
	  return dbCoords;
	}
	
	synchronized JSONObject getActivity(String fileName) {
	  try {
	    return readActivity(executeQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE genby='" + fileName + '\'', true), true);
	  } catch (Exception e) {
	    System.out.println("Error reading activity " + fileName);
	    e.printStackTrace();
	  }
	  return null;
	}
	
	synchronized boolean hasActivity(String fileName) {
	  try {
      return executeQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE genby='" + fileName + '\'', true).next();
    } catch (SQLException e) {
      return false;
    }
	}
	
	synchronized JSONObject getBestActivities(String columnName) {
	  JSONArray extArr = new JSONArray();
	  extArr.put(EXTERNAL_DASHBOARD);
	  String extS = extArr.toString();
    try {
      ResultSet rs = executeQuery("SELECT date, genby, " + columnName + " FROM " + RUNS_TABLE_NAME +
          " WHERE " + columnName +
          "=(SELECT MAX(" + columnName + ") FROM " + RUNS_TABLE_NAME +
          " WHERE dashboards!='" + extS + "')", true);
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
	
	synchronized JSONObject getBestActivities(double distMin, double distMax) {
	  try {
	    ResultSet rs = executeQuery("SELECT genby, date, timeTotal, isExt FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distMin + " AND distRaw <= " + distMax + ") ORDER BY timeTotalRaw", true);
	    if (rs == null) {
        return null;
      }
	    while (rs.next()) {
	      if (rs.getInt("isExt") == 1) {
	        continue;
	      }
	      JSONObject result = new JSONObject();
	      result.put("date", rs.getString("date"));
	      result.put("genby", rs.getString("genby"));
	      result.put("timeTotal", rs.getString("timeTotal"));
	      return result;
	    }
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
	  return null;
	}
	
	synchronized JSONArray getActivitySplits() {
	  JSONArray result = new JSONArray();
	  ResultSet rs = executeQuery("SELECT name, date, splits, genby, isExt FROM " + RUNS_TABLE_NAME +
	      " WHERE (type='Running' OR type='Trail')", true);
	  try {
      while (rs.next()) {
        if (rs.getInt("isExt") == 1) {
          continue;
        }
        JSONObject crnt = new JSONObject();
        crnt.put("name", rs.getString(1));
        crnt.put("date", rs.getString(2));
        crnt.put("splits", new JSONArray(rs.getString(3)));
        crnt.put("genby", rs.getString(4));
        result.put(crnt);
      }
    } catch (SQLException e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
	  return result;
	}
	
	synchronized void addToDashboard(String activity, String dashboard) throws SQLException {
	  ResultSet rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name='" + dashboard + "'", true);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard not found");
    }
	  rs = executeQuery("SELECT dashboards FROM " + RUNS_TABLE_NAME + " WHERE genby='" + activity + "'", true);
	  if (!rs.next()) {
	    throw new IllegalArgumentException("Activity not found");
	  }
	  JSONArray dashboards = new JSONArray(rs.getString(1));
	  if (RunCalcUtils.find(dashboards, dashboard) != -1) {
	    throw new IllegalArgumentException("Activity already in dashboard");
	  }
	  dashboards.put(dashboard);
	  executeQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards='" + dashboards.toString() + "' WHERE genby='" + activity + "'", false);
	}
	
	synchronized void removeFromDashboard(String activity, String dashboard) throws SQLException {
	  ResultSet rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name='" + dashboard + "'", true);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard not found");
    }
    rs = executeQuery("SELECT dashboards FROM " + RUNS_TABLE_NAME + " WHERE genby='" + activity + "'", true);
    if (!rs.next()) {
      throw new IllegalArgumentException("Activity not found");
    }
    JSONArray dashboards = new JSONArray(rs.getString(1));
    int ind = RunCalcUtils.find(dashboards, dashboard);
    if (ind == -1) {
      throw new IllegalArgumentException("Activity not in dashboard");
    }
    if (dashboards.length() == 1) {
      throw new IllegalArgumentException("Activity must be present in at least one dashboard");
    }
    dashboards.remove(ind);
    executeQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards='" + dashboards.toString() + "' WHERE genby='" + activity + "'", false);
  }
	
  synchronized void addDashboard(String name) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (MAIN_DASHBOARD.equals(name)) {
      throw new IllegalArgumentException("Cannot re-add main dashboard");
    }
    executePreparedQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES(?)", new Object[] {name});
  }

  synchronized void renameDashboard(String name, String newName) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (MAIN_DASHBOARD.equals(name) || MAIN_DASHBOARD.equals(newName)) {
      throw new IllegalArgumentException("Cannot rename main dashboard");
    }
    ResultSet rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name='" + newName + "'", true);
    if (rs.next()) {
      throw new IllegalArgumentException("Dashboard " + newName + " already exists");
    }
    rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME + " WHERE name='" + name + "'", true);
    if (!rs.next()) {
      throw new IllegalArgumentException("Dashboard " + name + " doest not exist");
    }
    
    executePreparedQuery("UPDATE " + DASHBOARDS_TABLE_NAME + " SET name=? WHERE name=?", new Object[] {newName, name});
    rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
    while (rs.next()) {
      String dash = rs.getString(2);
      JSONArray arr = new JSONArray(dash);
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
        executeQuery("UPDATE " + RUNS_TABLE_NAME + " SET dashboards='" + arr.toString() + "' WHERE genby='" + genby + "'", false);
      }
    }
    executeQuery("UPDATE " + PRESETS_TABLE_NAME + " SET dashboard='" + newName + "' WHERE dashboard='" + name + "'", false);
  }

  synchronized void removeDashboard(String name) throws SQLException {
    if (name == null) {
      throw new IllegalArgumentException("No name specified");
    }
    if (MAIN_DASHBOARD.equals(name)) {
      throw new IllegalArgumentException("Cannot remove main dashboard");
    }
    executePreparedQuery("DELETE FROM " + DASHBOARDS_TABLE_NAME + " WHERE name=?", new Object[] {name});
    ResultSet rs = executeQueryExc("SELECT genby, dashboards FROM " + RUNS_TABLE_NAME, true);
    while (rs.next()) {
      String dash = rs.getString(2);
      JSONArray arr = new JSONArray(dash);
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
        		new Object[] {arr.toString(), genby});
      }
    }
    executePreparedQuery("UPDATE " + PRESETS_TABLE_NAME + " SET dashboard='" + MAIN_DASHBOARD + "' WHERE dashboard=?",
    		new Object[] {name});
  }

  synchronized JSONObject getDashboards() {
    JSONArray arr = new JSONArray();
    try {
      ResultSet rs = executeQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME, true);
      if (rs == null) {
        executeQuery("INSERT INTO " + DASHBOARDS_TABLE_NAME + " VALUES('" + MAIN_DASHBOARD + "')", false);
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
  
  synchronized boolean dashboardExists(String dashboard) {
    if (dashboard == null) {
      return false;
    }
    try {
      ResultSet rs = executePreparedQuery("SELECT * FROM " + DASHBOARDS_TABLE_NAME +
          " WHERE name=?", new Object[] {dashboard});
      return rs != null && rs.next();
    } catch (Exception e) {
      System.out.println("Error saving cookie");
      e.printStackTrace();
    }
    return false;
  }
	
  synchronized boolean saveCookie(String uid, Calendar expires) {
    try {
      boolean hasCookie = executeQuery("SELECT uid FROM " + COOKIES_TABLE_NAME + " WHERE uid='" + uid + "'", true)
          .next();
      if (!hasCookie) {
        StringBuffer sb = new StringBuffer();
        sb.append("INSERT INTO " + COOKIES_TABLE_NAME + " VALUES ('" + uid + "', '");
        sb.append(expires.get(Calendar.YEAR) + "-" + expires.get(Calendar.MONTH) + "-" + expires.get(Calendar.DATE));
        sb.append("')");
        executeQuery(sb.toString(), false);
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
	
	synchronized boolean isValidCookie(String uid) {
	  try {
	    ResultSet rs = executeQuery("SELECT expires FROM " + COOKIES_TABLE_NAME + " WHERE uid='" + uid + "'", true);
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
	
	synchronized void deleteCookie(String uid) {
	  executeQuery("DELETE FROM "+ COOKIES_TABLE_NAME + " WHERE uid='" + uid + "'", false);
	}
	
	synchronized void checkForExpiredCookies() {
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
	  if (RunCalcUtils.TRAIL.equals(type1)) {
	    return RunCalcUtils.HIKING.equals(type2) || RunCalcUtils.UPHILL.equals(type2);
	  }
	  if (RunCalcUtils.HIKING.equals(type1)) {
	    return RunCalcUtils.TRAIL.equals(type2) || RunCalcUtils.UPHILL.equals(type2);
	  }
	  if (RunCalcUtils.UPHILL.equals(type1)) {
	    return RunCalcUtils.HIKING.equals(type2) || RunCalcUtils.TRAIL.equals(type2);
	  }
	  return false;
	}
	
	synchronized JSONObject getSplitsAndDist(String activity) {
	  JSONObject json = null;
	  try {
	    ResultSet rs = executePreparedQuery("SELECT splits, speedDist FROM " + RUNS_TABLE_NAME + " WHERE genby=?",
	    		new Object[] {activity});
	    if (rs != null && rs.next()) {
	      json = new JSONObject();
	      JSONArray splits = new JSONArray(rs.getString("splits"));
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
	      json.put("speedDist", new JSONArray(rs.getString("speedDist")));
	    }
	  } catch (SQLException e) {
      System.out.println("Error getting splits " + e);
      e.printStackTrace();
    }
    return json;
	}
	
  synchronized JSONObject getCompOptions(String activity, boolean searchOnlyExt) {
    JSONObject json = null;
    Map<String, Boolean> dashboards = new HashMap<String, Boolean>();
    String type = null;
    double dist = 0.0;
    double speed = 0.0;
    List<DataEntry> entries = new ArrayList<>();
    boolean isFromExt = false;
    try {
      ResultSet rs = executePreparedQuery("SELECT dashboards, type, distRaw, avgSpeedRaw FROM " + RUNS_TABLE_NAME + " WHERE genby=?",
      		new Object[] {activity});
      if (rs == null || !rs.next()) {
        return null;
      }
      type = rs.getString("type");
      dist = rs.getDouble("distRaw");
      speed = rs.getDouble("avgSpeedRaw");
      JSONArray arr = new JSONArray(rs.getString("dashboards"));
      for (int i = 0; i < arr.length(); ++i) {
        String dash = arr.getString(i);
        if (!MAIN_DASHBOARD.equals(dash)) {
          dashboards.put(dash, Boolean.TRUE);
        }
        if (EXTERNAL_DASHBOARD.equals(dash)) {
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
        arr = new JSONArray(rs.getString("dashboards"));
        boolean dashboardMatch = isFromExt;
				if (!dashboardMatch) {
					for (int i = 0; i < arr.length(); ++i) {
						String cdash = arr.getString(i);
						if (searchOnlyExt && EXTERNAL_DASHBOARD.equals(cdash)) {
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
        rs = executePreparedQuery("SELECT name, type, date, dist FROM " + RUNS_TABLE_NAME + " WHERE genby=?", new Object[] {id});
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
	
	synchronized void close() {
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
	
  synchronized void cleanupReliveCCBefore(Calendar cal) {
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

class DataEntry implements Comparable<DataEntry>{
  
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
