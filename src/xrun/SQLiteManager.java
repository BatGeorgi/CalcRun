package xrun;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SQLiteManager {
  
  private static final String TABLE_NAME = "runs";
  private static final String[] KEYS = new String[] {
      "genby", "name", "type", "date", "year", "month", "day", "dist",
      "starttime", "timeTotal", "timeTotalRaw", "timeRawMs", "timeRunning", "timeRest",
      "avgSpeed", "avgPace", "distRunning",
      "eleTotalPos", "eleTotalNeg", "eleRunningPos", "eleRunningNeg",
      "speedDist", "splits"
  };
  private static final String[] TYPES = new String[] {
      "text", "text", "text", "text", "integer", "integer", "integer", "text",
      "text", "text", "real", "integer", "text", "text",
      "text", "text", "text",
      "integer", "integer", "integer", "integer",
      "text", "text"
  };

	private File db;
	private String path;
	private String createStatement;

	SQLiteManager(File base) {
		db = new File(base, "activities.db");
		path = db.getAbsolutePath().replace('\\', '/');
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
	
	void dropExistingDB() {
	  if (db.isFile() && !db.delete()) {
	    Connection conn = null;
	    try {
	      String url = "jdbc:sqlite:" + db.getAbsolutePath();
	      conn = DriverManager.getConnection(url);
	      conn.createStatement().executeQuery("DROP TABLE " + TABLE_NAME);
	    } catch (SQLException e) {
	      e.printStackTrace();
	    } finally {
	      try {
	        if (conn != null) {
	          conn.close();
	        }
	      } catch (SQLException ignore) {
	        // silent catch
	      }
	    }
	  }
	}

	void createTableIfNotExists(Connection conn) throws SQLException {
	  conn.createStatement().executeUpdate(createStatement);
	}
	
	void addEntry(JSONObject entry) {
	  Connection conn = null;
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
	  System.out.println(sb.toString());
	  String insert = sb.toString();
    try {
      String url = "jdbc:sqlite:" + path;
      conn = DriverManager.getConnection(url);
      System.out.println("Connection to SQLite has been established.");
      createTableIfNotExists(conn);
      conn.createStatement().executeUpdate(insert);
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ignore) {
        // silent catch
      }
    }
	}
	
	private JSONObject readActivity(ResultSet rs) throws JSONException, SQLException {
	  JSONObject activity = new JSONObject();
	  int len = KEYS.length;
	  for (int i = 0; i < len - 2; ++i) {
	    activity.put(KEYS[i], rs.getObject(i + 1));
	  }
	  System.out.println(rs.getObject(len - 1));
	  activity.put(KEYS[len - 2], new JSONArray(rs.getString(len - 1)));
	  activity.put(KEYS[len - 1], new JSONArray(rs.getString(len)));
	  return activity;
	}
	
	JSONArray retrieveAll() {
	  JSONArray arr = new JSONArray();
	  Connection conn = null;
	  try {
      String url = "jdbc:sqlite:" + path;
      conn = DriverManager.getConnection(url);
      System.out.println("Connection to SQLite has been established.");
      ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM " + TABLE_NAME);
      while (rs.next()) {
        arr.put(readActivity(rs));
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ignore) {
        // silent catch
      }
    }
	  System.out.println(arr);
	  return arr;
	}

}
