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
  private String genby;

  @DatabaseField(canBeNull = false)
  private String name;

  @DatabaseField(canBeNull = false)
  private String type;

  @DatabaseField(canBeNull = false)
  private String date;

  @DatabaseField(canBeNull = false)
  private int year;

  @DatabaseField(canBeNull = false)
  private int month;

  @DatabaseField(canBeNull = false)
  private int day;

  @DatabaseField(canBeNull = false)
  private String dist;

  @DatabaseField(canBeNull = false)
  private double distRaw;

  @DatabaseField(canBeNull = false)
  private String starttime;

  @DatabaseField(canBeNull = false)
  private String timeTotal;

  @DatabaseField(canBeNull = false)
  private double timeTotalRaw;

  @DatabaseField(canBeNull = false)
  private long timeRawMs;

  @DatabaseField(canBeNull = false)
  private String timeRunning;

  @DatabaseField(canBeNull = false)
  private String timeRest;

  @DatabaseField(canBeNull = false)
  private String avgSpeed;

  @DatabaseField(canBeNull = false)
  private double avgSpeedRaw;

  @DatabaseField(canBeNull = false)
  private String avgPace;

  @DatabaseField(canBeNull = false)
  private String distRunning;

  @DatabaseField(canBeNull = false)
  private double distRunningRaw;

  @DatabaseField(canBeNull = false)
  private int eleTotalPos;

  @DatabaseField(canBeNull = false)
  private int eleTotalNeg;

  @DatabaseField(canBeNull = false)
  private int eleRunningPos;

  @DatabaseField(canBeNull = false)
  private int eleRunningNeg;

  @DatabaseField(canBeNull = false)
  private String garminLink;

  @DatabaseField(canBeNull = false)
  private String ccLink;

  @DatabaseField(canBeNull = false)
  private String photosLink;

  @DatabaseField(canBeNull = false)
  private String parent;

  @DatabaseField(canBeNull = false)
  private String distByInterval;

  @DatabaseField(canBeNull = false)
  private String distByIntervalLabels;

  @DatabaseField(canBeNull = false)
  private String dashboards;

  @DatabaseField(canBeNull = false)
  private int isExt;

  @DatabaseField(canBeNull = false)
  private String speedDist;

  @DatabaseField(canBeNull = false)
  private String splits;

  @DatabaseField(canBeNull = false)
  private String origData;

  public Activity() {
  }

  public Activity(JSONObject json) {
    // TODO
  }

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

  public String getGenby() {
    return genby;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getDate() {
    return date;
  }

  public int getYear() {
    return year;
  }

  public int getMonth() {
    return month;
  }

  public int getDay() {
    return day;
  }

  public String getDist() {
    return dist;
  }

  public String getStarttime() {
    return starttime;
  }

  public String getTimeTotal() {
    return timeTotal;
  }

  public long getTimeRawMs() {
    return timeRawMs;
  }

  public String getTimeRunning() {
    return timeRunning;
  }

  public String getTimeRest() {
    return timeRest;
  }

  public String getAvgSpeed() {
    return avgSpeed;
  }

  public double getAvgSpeedRaw() {
    return avgSpeedRaw;
  }

  public String getAvgPace() {
    return avgPace;
  }

  public String getDistRunning() {
    return distRunning;
  }

  public int getEleRunningPos() {
    return eleRunningPos;
  }

  public int getEleRunningNeg() {
    return eleRunningNeg;
  }

  public String getGarminLink() {
    return garminLink;
  }

  public String getCcLink() {
    return ccLink;
  }

  public String getPhotosLink() {
    return photosLink;
  }

  public String getParent() {
    return parent;
  }

  public String getDistByInterval() {
    return distByInterval;
  }

  public String getDistByIntervalLabels() {
    return distByIntervalLabels;
  }

  public String getDashboards() {
    return dashboards;
  }

  public int getIsExt() {
    return isExt;
  }

  public String getSpeedDist() {
    return speedDist;
  }

  public String getSplits() {
    return splits;
  }

  public String getOrigData() {
    return origData;
  }

  public double getDistRaw() {
    return distRaw;
  }

  public double getTimeTotalRaw() {
    return timeTotalRaw;
  }

  public long getEleTotalPos() {
    return eleTotalPos;
  }

  public long getEleTotalNeg() {
    return eleTotalNeg;
  }

  public double getDistRunningRaw() {
    return distRunningRaw;
  }

}
