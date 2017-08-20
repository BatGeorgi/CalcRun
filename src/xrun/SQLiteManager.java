package xrun;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager {

	private File db;
	
	private static final String CREATE_SQL_MAIN = "CREATE TABLE IF NOT EXISTS runs (\n"
			+ " date text,\n"
			+ " splits text,\n"
			+ " year integer,\n"
			+ " timeTotal text,\n"
			+ " dist text,\n"
			+ " eleTotalPos integer,\n"
			+ " starttime text,\n"
			+ " eleRunningNeg integer,\n"
			+ " avgPace text,\n"
			+ " type text,\n"
			+ " timeRest text,\n"
			+ " eleTotalNeg integer,\n"
			+ " timeRawMs integer,\n"
			+ " avgSpeed text,\n"
			+ " timeRunning text,\n"
			+ " timeTotalRaw real,\n"
			+ " month integer,\n"
			+ " eleRunningPos integer,\n"
			+ " name text,\n"
			+ " distRunning text,\n"
			+ " genby text,\n"
			+ " day integer,\n"
			+ " speedDist text,\n";

	SQLiteManager(File base) {
		db = new File(base, "activities.db");
	}

	public void createTableIfNotExists() {
		Connection conn = null;
		try {
			String url = "jdbc:sqlite:" + db.getAbsolutePath();
			conn = DriverManager.getConnection(url);
			System.out.println("Connection to SQLite has been established.");
			Statement stmt = conn.createStatement();
			stmt.executeQuery(CREATE_SQL_MAIN);
			System.out.println("Table created");
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
