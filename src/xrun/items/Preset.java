package xrun.items;

import java.util.StringTokenizer;

import org.json.JSONObject;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "presets")
public class Preset {
  
  @DatabaseField(id = true)
  private String name;

  @DatabaseField(canBeNull = false)
  private String types;

  @DatabaseField(canBeNull = false)
  private String pattern;

  @DatabaseField(canBeNull = false)
  private String startDate;

  @DatabaseField(canBeNull = false)
  private String endDate;

  @DatabaseField(canBeNull = false)
  private int minDist;

  @DatabaseField(canBeNull = false)
  private int maxDist;

  @DatabaseField(canBeNull = false)
  private int top;

  @DatabaseField(canBeNull = false)
  private String dashboard;

  public Preset() {
  }

  public Preset(String name, String types, String pattern, String startDate, String endDate,
      int minDist, int maxDist, int top, String dashboard) {
    this.name = name;
    this.types = types;
    this.pattern = pattern;
    this.startDate = startDate;
    this.endDate = endDate;
    this.minDist = minDist;
    this.maxDist = maxDist;
    this.top = top;
    this.dashboard = dashboard;
  }

  public JSONObject exportToJson() {
    JSONObject json = new JSONObject();
    json.put("name", name);
    json.put("pattern", pattern);
    json.put("startDate", startDate);
    json.put("endDate", endDate);
    json.put("minDist", minDist);
    json.put("maxDist", maxDist);
    json.put("top", top);
    json.put("dashboard", dashboard);
    StringTokenizer st = new StringTokenizer(types, ",", false);
    while (st.hasMoreTokens()) {
      String next = st.nextToken().trim();
      if (next.length() > 0) {
        json.put(next, true);
      }
    }
    return json;
  }
  
  public String getName() {
    return name;
  }

  public String getTypes() {
    return types;
  }

  public String getPattern() {
    return pattern;
  }

  public String getStartDate() {
    return startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public int getMinDist() {
    return minDist;
  }

  public int getMaxDist() {
    return maxDist;
  }

  public int getTop() {
    return top;
  }

  public String getDashboard() {
    return dashboard;
  }
}
