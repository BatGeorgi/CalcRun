package xrun.items;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import xrun.common.Constants;
import xrun.utils.JsonSanitizer;
import xrun.utils.TimeUtils;

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
  "origData" */

  public JSONObject exportToJSON(boolean includeSplitsAndDistr) {
    JSONObject result = new JSONObject();
    result.put("genby", genby);
    result.put("name", name);
    result.put("type", type);
    result.put("date", date);
    result.put("year", year);
    result.put("month", month);
    result.put("day", day);
    result.put("dist", dist);
    result.put("distRaw", distRaw);
    result.put("starttime", starttime);
    result.put("timeTotal", timeTotal);
    result.put("timeTotalRaw", timeTotalRaw);
    result.put("timeRawMs", timeRawMs);
    result.put("timeRunning", timeRunning);
    result.put("timeRest", timeRest);
    result.put("avgSpeed", avgSpeed);
    result.put("avgSpeedRaw", avgSpeedRaw);
    result.put("avgPace", avgPace);
    result.put("distRunning", distRunning);
    result.put("distRunningRaw", distRunningRaw);
    result.put("eleTotalPos", eleTotalPos);
    result.put("eleTotalNeg", eleTotalNeg);
    result.put("eleRunningPos", eleRunningPos);
    result.put("eleRunningNeg", eleRunningNeg);
    result.put("garminLink", garminLink);
    result.put("ccLink", ccLink);
    result.put("photosLink", photosLink);
    result.put("parent", parent);
    result.put("dashboards", dashboards);
    result.put("isExt", isExt);
    if (includeSplitsAndDistr) {
      result.put("speedDist", new JSONArray(JsonSanitizer.sanitize(speedDist)));
      result.put("splits", new JSONArray(JsonSanitizer.sanitize(splits)));
      result.put("distByInterval", new JSONArray(JsonSanitizer.sanitize(distByInterval)));
      result.put("distByIntervalLabels", new JSONArray(JsonSanitizer.sanitize(distByIntervalLabels)));
      JSONArray splits = result.getJSONArray("splits");
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
    result.put("origData", origData != null ? new JSONObject(JsonSanitizer.sanitize(origData)) : new JSONObject());
    Calendar cal = new GregorianCalendar();
    cal.setTimeInMillis(timeRawMs);
    long corr = TimeZone.getDefault().inDaylightTime(cal.getTime()) ? Constants.CORRECTION_BG_SUMMER
        : Constants.CORRECTION_BG_WINTER;
    cal.setTimeInMillis(timeRawMs + corr);
    result.put("startAt", TimeUtils.formatDate(cal, true));
    return result;
  }
  
  /*
   * 
    
   */

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
}*/
}
