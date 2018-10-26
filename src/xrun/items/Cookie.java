package xrun.items;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "cookie")
public class Cookie {

  @DatabaseField(id = true)
  private String uid;

  @DatabaseField(canBeNull = false)
  private String expires;
  
  public Cookie() {
  }

  public Cookie(String uid, String expires) {
    this.uid = uid;
    this.expires = expires;
  }

  public String getUid() {
    return uid;
  }

  public String getExpires() {
    return expires;
  }
}
