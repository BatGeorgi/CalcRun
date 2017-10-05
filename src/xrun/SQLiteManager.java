package xrun;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SQLiteManager {
  
  private static final String RUNS_TABLE_NAME = "runs";
  private static final String COOKIES_TABLE_NAME = "cookies";
  
  private static final String[] KEYS = new String[] {
      "genby", "name", "type", "date", "year", "month", "day", "dist", "distRaw",
      "starttime", "timeTotal", "timeTotalRaw", "timeRawMs", "timeRunning", "timeRest",
      "avgSpeed", "avgSpeedRaw", "avgPace", "distRunning", "distRunningRaw",
      "eleTotalPos", "eleTotalNeg", "eleRunningPos", "eleRunningNeg",
      "garminLink",
      "speedDist", "splits"
  };
  private static final String[] TYPES = new String[] {
      "text", "text", "text", "text", "integer", "integer", "integer", "text", "real",
      "text", "text", "real", "integer", "text", "text",
      "text", "real", "text", "text", "real",
      "integer", "integer", "integer", "integer",
      "text",
      "text", "text"
  };
  private static final String DB_FILE_PREF = "activities";
  
  private static final String CREATE_STATEMENT_COOKIES_TABLE = "CREATE TABLE IF NOT EXISTS " + COOKIES_TABLE_NAME + 
      "(uid PRIMARY KEY NOT NULL, expires NOT NULL)";

	private File db;
	private String createStatementRunsTable;
	private Connection conn = null;

	SQLiteManager(File base) {
		db = new File(base, "activities.db");
		if (!db.isFile()) {
		  File[] children = base.listFiles();
		  if (children != null) {
		    for (File child : children) {
		      String name = child.getName();
		      if (name.startsWith(DB_FILE_PREF) && name.endsWith(".db")) {
		        db = child;
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
	        "type='" + RunCalcUtils.HIKING + '\'',
	        "type='" + RunCalcUtils.RUNNING + "' OR type='" + RunCalcUtils.TRAIL + '\''
	    };
	    String[] acms = new String[] {
	        "r", "t", "h", "rt"
	    };
	    for (int i = years.size() - 1; i >= 0; --i) {
	      int year = years.get(i);
	      JSONObject[] months = new JSONObject[12];
	      for (int j = 0; j < 12; ++j) {
	        months[j] = new JSONObject();
	        months[j].put("name", CalcDist.MONTHS[j] + ' ' + year);
	        for (int k = 0; k < 4; ++k) {
	          months[j].put(acms[k], "0");
	        }
	        months[j].put("emp", true);
	      }
        for (int j = 0; j < filters.length; ++j) {
          rs = executeQuery("SELECT month, SUM(distRaw) FROM " + RUNS_TABLE_NAME + " WHERE (" + filters[j] +
              ") AND year=" + years.get(i) + " GROUP BY month", true);
	        while (rs.next()) {
	          months[rs.getInt(1)].put(acms[j], String.format("%.3f", rs.getDouble(2)));
	          months[rs.getInt(1)].remove("emp");
	        }
	      }
        for (int j = months.length - 1; j >= 0; --j) {
          if (months[j].opt("emp") == null) {
            result.put(months[j]);
          }
        }
	    }
	  } catch (Exception e) {
	    e.printStackTrace();
	  }
	  return result;
	}
	
	synchronized void ensureInit() throws SQLException {
	  if (conn != null) {
	    return;
	  }
	  conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath().replace('\\', '/'));
	  createTablesIfNotExists();
	}
	
  private ResultSet executeQuery(String query, boolean returnResult) {
    try {
      ensureInit();
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
	}
	
	synchronized void addActivity(JSONObject entry) {
	  StringBuffer sb = new StringBuffer();
	  sb.append("INSERT INTO " + RUNS_TABLE_NAME + " VALUES (");
	  for (int i = 0; i < KEYS.length; ++i) {
	    String str = entry.get(KEYS[i]).toString();
	    if ("real".equals(TYPES[i])) {
	      str = str.replace(',', '.');
	    } else if ("text".equals(TYPES[i])) {
	      str = "'" + str + "'";
	    }
	    sb.append(str);
	    if (i < KEYS.length - 1) {
	      sb.append(", ");
	    }
	  }
	  sb.append(')');
    executeQuery(sb.toString(), false);
	}
	
	private JSONObject readActivity(ResultSet rs) throws JSONException, SQLException {
	  if (!rs.next()) {
	    return null;
	  }
	  JSONObject activity = new JSONObject();
	  int len = KEYS.length;
	  for (int i = 0; i < len - 2; ++i) {
	    activity.put(KEYS[i], rs.getObject(i + 1));
	  }
	  activity.put(KEYS[len - 2], new JSONArray(rs.getString(len - 1)));
	  activity.put(KEYS[len - 1], new JSONArray(rs.getString(len)));
	  return activity;
	}
	
	synchronized List<JSONObject> fetchActivities(boolean run, boolean trail, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance, int maxCount) {
	  List<JSONObject> result = new ArrayList<JSONObject>();
	  StringBuffer selectClause = new StringBuffer();
	  StringBuffer whereClause = new StringBuffer();
	  selectClause.append("SELECT * FROM " + RUNS_TABLE_NAME);
	  whereClause.append("WHERE ");
	  whereClause.append("(distRaw >= " + minDistance + " AND distRaw <= " + maxDistance + ") ");
	  if (run || trail || hike || walk || other) {
	    whereClause.append(" AND ");
	    whereClause.append('(');
	    List<String> types = new ArrayList<String>();
	    if (run) {
        types.add(RunCalcUtils.RUNNING);
      }
      if (trail) {
        types.add(RunCalcUtils.TRAIL);
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
    if (maxCount != Integer.MAX_VALUE) {
      selectClause.append(" LIMIT " + maxCount);
    }
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
      while ((json = readActivity(rs)) != null) {
        result.add(json);
      }
	  } catch (Exception ignore) {
      // silent catch
    }
	  return result;
  }
	
	synchronized void updateActivity(String fileName, String newName, String newType) {
	  StringBuffer sb = new StringBuffer();
	  sb.append("UPDATE " + RUNS_TABLE_NAME + " SET name ='" + newName + "', type ='" + newType);
	  sb.append("' WHERE genby='" + fileName + '\'');
	  executeQuery(sb.toString(), false);
	}
	
	synchronized void deleteActivities(String fileName) {
	  executeQuery("DELETE FROM " + RUNS_TABLE_NAME + " WHERE genby='" + fileName + '\'', false);
	}
	
	File getDBFile() {
	  return db;
	}
	
	synchronized JSONObject getActivity(String fileName) {
	  try {
	    return readActivity(executeQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE genby='" + fileName + '\'', true));
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
    try {
      return readActivity(executeQuery("SELECT * FROM " + RUNS_TABLE_NAME + " WHERE " + columnName +
          "=(SELECT MAX(" + columnName + ") FROM " + RUNS_TABLE_NAME + ')', true));
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return null;
	}
	
	synchronized JSONObject getBestActivities(double distMin, double distMax) {
	  try {
      return readActivity(executeQuery("SELECT * FROM " + RUNS_TABLE_NAME +
          " WHERE (distRaw >= " + distMin + " AND distRaw <= " + distMax + ") ORDER BY timeTotalRaw LIMIT 1", true));
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
	  return null;
	}
	
	synchronized JSONArray getActivitySplits() {
	  JSONArray result = new JSONArray();
	  ResultSet rs = executeQuery("SELECT name, date, splits FROM " + RUNS_TABLE_NAME +
	      " WHERE (type='Running' OR type='Trail')", true);
	  try {
      while (rs.next()) {
        JSONObject crnt = new JSONObject();
        crnt.put("name", rs.getString(1));
        crnt.put("date", rs.getString(2));
        crnt.put("splits", new JSONArray(rs.getString(3)));
        result.put(crnt);
      }
    } catch (SQLException e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
	  return result;
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
      return hasCookie;
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
    String[] tokens = expires.split(" ");
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
	    if (!isCookieValid(rs.getString(1))) {
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
	
	synchronized void close() {
	  try {
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException ignore) {
      // silent catch
    }
	  conn = null;
	}

}
