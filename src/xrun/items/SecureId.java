package xrun.items;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "secured")
public class SecureId {

  @DatabaseField(id = true)
  private String id;
  
  public SecureId() {
  }

  public SecureId(String id) {
    this.id = id;
  }

}
