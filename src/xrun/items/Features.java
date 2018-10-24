package xrun.items;

import java.util.List;

import org.json.JSONArray;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "features")
public class Features {

  @DatabaseField(id = true)
  private String id;

  @DatabaseField(canBeNull = false)
  private String descr;

  @DatabaseField(canBeNull = false)
  private String links;

  public Features() {
  }

  public Features(String id, String descr, List<String> links) {
    this.id = id;
    this.descr = descr;
    JSONArray arr = new JSONArray();
    for (String link : links) {
      arr.put(link);
    }
    this.links = arr.toString();
  }

  public String getDescr() {
    return descr;
  }

  public String getLinks() {
    return links;
  }
}
