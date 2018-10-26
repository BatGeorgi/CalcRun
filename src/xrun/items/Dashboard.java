package xrun.items;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "dashboards")
public class Dashboard {

  @DatabaseField(id = true)
  private String name;

  public Dashboard() {
  }

  public Dashboard(String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }
}
