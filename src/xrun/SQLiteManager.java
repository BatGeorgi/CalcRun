package xrun;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SQLiteManager {
  
  private static final String TABLE_NAME = "runs";
  private static final String[] KEYS = new String[] {
      "genby", "name", "type", "date", "year", "month", "day", "dist", "distRaw",
      "starttime", "timeTotal", "timeTotalRaw", "timeRawMs", "timeRunning", "timeRest",
      "avgSpeed", "avgSpeedRaw", "avgPace", "distRunning",
      "eleTotalPos", "eleTotalNeg", "eleRunningPos", "eleRunningNeg",
      "speedDist", "splits"
  };
  private static final String[] TYPES = new String[] {
      "text", "text", "text", "text", "integer", "integer", "integer", "text", "real",
      "text", "text", "real", "integer", "text", "text",
      "text", "real", "text", "text",
      "integer", "integer", "integer", "integer",
      "text", "text"
  };

	private File db;
	private String createStatement;
	private Connection conn = null;

	SQLiteManager(File base) {
		db = new File(base, "activities.db");
		StringBuffer cr = new StringBuffer();
		cr.append("CREATE TABLE IF NOT EXISTS "+ TABLE_NAME + " (");
		cr.append(KEYS[0] + ' ' + TYPES[0] + " PRIMARY KEY NOT NULL, ");
		int len = KEYS.length;
		for (int i = 1; i < len - 1; ++i) {
		  cr.append(KEYS[i] + ' ' + TYPES[i] + " NOT NULL, ");
		}
		cr.append(KEYS[len - 1] + ' ' + TYPES[len - 1] + " NOT NULL)");
		createStatement = cr.toString();
	}
	
	synchronized void ensureInit() throws SQLException {
	  if (conn != null) {
	    return;
	  }
	  conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath().replace('\\', '/'));
	  createTableIfNotExists();
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
	
	void dropExistingDB() {
	  executeQuery("DROP TABLE " + TABLE_NAME, false);
	  createTableIfNotExists();
	}

	void createTableIfNotExists() {
	  executeQuery(createStatement, false);
	}
	
	void addEntry(JSONObject entry) {
	  StringBuffer sb = new StringBuffer();
	  sb.append("INSERT INTO " + TABLE_NAME + " VALUES (");
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
    createTableIfNotExists();
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
	
  JSONArray retrieveAll() {
    JSONArray arr = new JSONArray();
    ResultSet rs = executeQuery("SELECT * FROM " + TABLE_NAME, true);
    try {
      while (rs.next()) {
        arr.put(readActivity(rs));
      }
    } catch (Exception ignore) {
      // silent catch
    }
    return arr;
  }
	
	List<JSONObject> filter(boolean run, boolean trail, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
	  List<JSONObject> result = new ArrayList<JSONObject>();
	  StringBuffer sb = new StringBuffer();
	  sb.append("SELECT * FROM " + TABLE_NAME + " WHERE ");
	  sb.append("(distRaw >= " + minDistance + " AND distRaw <= " + maxDistance + ')');
	  if (run || trail || hike || walk || other) {
	    sb.append(" AND ");
	    sb.append('(');
	    List<String> types = new ArrayList<String>();
	    if (run) {
        types.add("Running");
      }
      if (trail) {
        types.add("Trail");
      }
      if (hike) {
        types.add("Hiking");
      }
      if (walk) {
        types.add("Walking");
      }
      if (other) {
        types.add("Other");
      }
      for (int i = 0; i < types.size(); ++i) {
        sb.append("type = '" + types.get(i) + '\'');
        if (i < types.size() - 1) {
          sb.append(" OR ");
        }
      }
	    sb.append(')');
	  }
	  if (startDate != null) {
	    int yr = startDate.get(Calendar.YEAR);
	    int mt = startDate.get(Calendar.MONTH);
	    int d = startDate.get(Calendar.DAY_OF_MONTH);
	    sb.append(" AND (");
	    sb.append("(YEAR > " + yr + ") OR ");
	    sb.append("(YEAR = " + yr + " AND MONTH > " + mt + ") OR ");
	    sb.append("(YEAR = " + yr + " AND MONTH = " + mt + " AND DAY >= " + d + ")");
	    sb.append(')');
	  }
	  if (endDate != null) {
      int yr = endDate.get(Calendar.YEAR);
      int mt = endDate.get(Calendar.MONTH);
      int d = endDate.get(Calendar.DAY_OF_MONTH);
      sb.append(" AND (");
      sb.append("(YEAR < " + yr + ") OR ");
      sb.append("(YEAR = " + yr + " AND MONTH < " + mt + ") OR ");
      sb.append("(YEAR = " + yr + " AND MONTH = " + mt + " AND DAY <= " + d + ")");
      sb.append(')');
    }
	  ResultSet rs = executeQuery(sb.toString(), true);
	  try {
	    JSONObject json = null;
      while ((json = readActivity(rs)) != null) {
        result.add(json);
      }
	  } catch (Exception ignore) {
      // silent catch
    }
	  return result;
  }
	
	void updateEntry(String fileName, String newName, String newType) {
	  StringBuffer sb = new StringBuffer();
	  sb.append("UPDATE " + TABLE_NAME + " SET name = " + newName + ", type = " + newType);
	  sb.append(" WHERE genby='" + fileName + '\'');
	  executeQuery(sb.toString(), false);
	}
	
	JSONObject getActivity(String fileName) {
	  try {
	    return readActivity(executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE genby='" + fileName + '\'', true));
	  } catch (Exception e) {
	    System.out.println("Error reading activity " + fileName);
	    e.printStackTrace();
	  }
	  return null;
	}
	
	boolean hasRecord(String fileName) {
	  try {
      return executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE genby='" + fileName + '\'', true).next();
    } catch (SQLException e) {
      return false;
    }
	}
	
	JSONObject getBest(String columnName) {
    try {
      return readActivity(executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + columnName +
          "=(SELECT MAX(" + columnName + ") FROM " + TABLE_NAME + ')', true));
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
    return null;
	}
	
	JSONObject getBest(double distMin, double distMax) {
	  try {
      return readActivity(executeQuery("SELECT * FROM " + TABLE_NAME +
          " WHERE (distRaw >= " + distMin + " AND distRaw <= " + distMax + ") ORDER BY timeTotalRaw LIMIT 1", true));
    } catch (Exception e) {
      System.out.println("Error reading activities");
      e.printStackTrace();
    }
	  return null;
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
