package xrun;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class RescanAll {
  
  private File base;
  
  RescanAll(File base) {
    this.base = base;
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      throw new IllegalArgumentException("Not enough arguments");
    }
    File base = new File(args[0]);
    if (!base.isDirectory()) {
      throw new IllegalArgumentException(base + " is not a valid folder path");
    }
    System.out.println(new RescanAll(base).rescan());
  }
  
  JSONObject rescan() {
    List<JSONObject> runs = new ArrayList<JSONObject>();
    String[] all = base.list();
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx")) {
        continue;
      }
      File targ = new File(base, fileName);
      if (!targ.isFile()) {
        continue;
      }
      try {
        JSONObject current = new JSONObject();
        CalcDist.run(targ.getAbsolutePath(), "9", "100", "1", current);
        runs.add(current);
      } catch (Exception e) {
        System.out.println("Error processing " + targ);
        e.printStackTrace();
      }
    }
    Collections.sort(runs, new RunDateComparator());
    JSONObject result = new JSONObject();
    JSONArray arr = new JSONArray();
    for (JSONObject json : runs) {
      arr.put(json);
    }
    result.put("activities", arr);
    return result;
  }

}

class RunDateComparator implements Comparator<JSONObject> {

  public int compare(JSONObject o1, JSONObject o2) {
    long time1 = o1.getLong("timeRawMs");
    long time2 = o2.getLong("timeRawMs");
    if (time1 == time2) {
      return 0;
    }
    return time1 < time2 ? 1 : -1;
  }
  
}
