package xrun.items;

import org.json.JSONArray;
import org.json.JSONObject;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "coords")
public class ActivityCoords {

  @DatabaseField(id = true)
  private String id;

  @DatabaseField(canBeNull = false)
  private String data;

  public ActivityCoords() {
  }

  public ActivityCoords(String id, JSONArray lats, JSONArray lons, JSONArray times, JSONArray markers) {
    this.id = id;
    JSONObject json = new JSONObject();
    json.put("lats", lats);
    json.put("lons", lons);
    json.put("times", times);
    json.put("markers", markers);
    this.data = json.toString();
  }

  public String getData() {
    return data;
  }

}
