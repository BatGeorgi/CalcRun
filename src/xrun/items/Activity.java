package xrun.items;

import org.json.JSONObject;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "runs")
public class Activity {

  @DatabaseField(id = true)
  String genby;

  @DatabaseField(canBeNull = false)
  String name;

  @DatabaseField(canBeNull = false)
  String type;

  @DatabaseField(canBeNull = false)
  String date;

  @DatabaseField(canBeNull = false)
  int year;

  @DatabaseField(canBeNull = false)
  int month;

  @DatabaseField(canBeNull = false)
  int day;

  @DatabaseField(canBeNull = false)
  String dist;

  @DatabaseField(canBeNull = false)
  double distRaw;

  @DatabaseField(canBeNull = false)
  String starttime;

  @DatabaseField(canBeNull = false)
  String timeTotal;

  @DatabaseField(canBeNull = false)
  double timeTotalRaw;

  @DatabaseField(canBeNull = false)
  long timeRawMs;

  @DatabaseField(canBeNull = false)
  String timeRunning;

  @DatabaseField(canBeNull = false)
  String timeRest;

  @DatabaseField(canBeNull = false)
  String avgSpeed;

  @DatabaseField(canBeNull = false)
  double avgSpeedRaw;

  @DatabaseField(canBeNull = false)
  String avgPace;

  @DatabaseField(canBeNull = false)
  String distRunning;

  @DatabaseField(canBeNull = false)
  double distRunningRaw;

  @DatabaseField(canBeNull = false)
  int eleTotalPos;

  @DatabaseField(canBeNull = false)
  int eleTotalNeg;

  @DatabaseField(canBeNull = false)
  int eleRunningPos;

  @DatabaseField(canBeNull = false)
  int eleRunningNeg;

  @DatabaseField(canBeNull = false)
  String garminLink;

  @DatabaseField(canBeNull = false)
  String ccLink;

  @DatabaseField(canBeNull = false)
  String photosLink;

  @DatabaseField(canBeNull = false)
  String parent;

  @DatabaseField(canBeNull = false)
  String distByInterval;

  @DatabaseField(canBeNull = false)
  String distByIntervalLabels;

  @DatabaseField(canBeNull = false)
  String dashboards;

  @DatabaseField(canBeNull = false)
  int isExt;

  @DatabaseField(canBeNull = false)
  String speedDist;

  @DatabaseField(canBeNull = false)
  String splits;

  @DatabaseField(canBeNull = false)
  String origData;

  public Activity() {
  }

  public Activity(JSONObject json) {
    
  }

  /*private static final String[] KEYS                              = new String[] {
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
};*/
}
