package xrun;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class RunCalcUtils {
  
  private File base;
  private File gpxBase;
  private Storage storage;
  
  RunCalcUtils(File base) {
    this.base = base;
    gpxBase = new File(base, "gpx");
    gpxBase.mkdirs();
    storage = new Storage(base);
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      throw new IllegalArgumentException("Not enough arguments");
    }
    File base = new File(args[0]);
    if (!base.isDirectory()) {
      throw new IllegalArgumentException(base + " is not a valid folder path");
    }
    System.out.println("here");
    new RunCalcUtils(base).rescan();
  }
  
  JSONObject rescan() {
    List<JSONObject> runs = new ArrayList<JSONObject>();
    String[] all = gpxBase.list();
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx")) {
        continue;
      }
      File targ = new File(gpxBase, fileName);
      if (!targ.isFile()) {
        continue;
      }
      try {
        System.out.println("scan " + fileName);
        JSONObject current = new JSONObject();
        CalcDist.run(targ, "9", "100", "1", current); // default values
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
  
  JSONObject retrieveAllActivities() {
    JSONObject result = new JSONObject();
    File jsonBase = new File(base, "reports_json");
    if (!jsonBase.isDirectory()) {
      return result.put("activities", new JSONArray());
    }
    JSONArray arr = new JSONArray();
    String[] all = jsonBase.list();
    List<JSONObject> runs = new ArrayList<JSONObject>();
    for (String name : all) {
      if (!name.endsWith(".json")) {
        continue;
      }
      File f = new File(jsonBase, name);
      if (!f.isFile()) {
        continue;
      }
      int rd = 0;
      byte[] buff = new byte[8192];
      InputStream is = null;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        is = new FileInputStream(f);
        while ((rd = is.read(buff)) != -1) {
          baos.write(buff, 0, rd);
        }
        runs.add(new JSONObject(new String(baos.toByteArray())));
      } catch (Exception e) {
        System.out.println("Error processing " + name);
        e.printStackTrace();
      } finally {
        try {
          if (is != null) {
            is.close();
          }
        } catch (IOException ignore) {
          // silent catch
        }
      }
    }
    Collections.sort(runs, new RunDateComparator());
    for (JSONObject json : runs) {
      arr.put(json);
    }
    result.put("activities", arr);
    return result;
  }
  
  JSONObject addActivity(File file) { // fileName must be saved in gpx base folder
    JSONObject status = new JSONObject();
    try {
      CalcDist.run(file, "9", "100", "1", new JSONObject());
      status.put("status", "ok");
    } catch (Exception e) {
      status.put("status", "failed");
      status.put("error", e.getClass().getName() + ' ' + e.getMessage());
    }
    return status;
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
