package xrun.utils;

import java.io.Closeable;

import org.json.JSONArray;

public class CommonUtils {
  
  public static int find(JSONArray array, Object element) {
    if (element == null) {
      return -1;
    }
    for (int i = 0; i < array.length(); ++i) {
      if (element.equals(array.get(i))) {
        return i;
      }
    }
    return -1;
  }
  
  public static void silentClose(Closeable cl) {
    try {
      if (cl != null) {
        cl.close();
      }
    } catch (Exception ignore) {
    }
  }

}
