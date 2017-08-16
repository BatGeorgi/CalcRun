package xrun;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class RunCalcUtils {
  
  private File base;
  private File gpxBase;
  private Storage storage;
  
  Map<String, JSONObject> cache = new HashMap<String, JSONObject>();
  
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
    new RunCalcUtils(base).rescan();
  }
  
  void rescan() {
    Map<String, Boolean> available = new HashMap<String, Boolean>();
    List<JSONObject> runs = new ArrayList<JSONObject>();
    String[] all = gpxBase.list();
    File jsonBase = new File(base, "reports_json");
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx")) {
        continue;
      }
      File targ = new File(gpxBase, fileName);
      if (!targ.isFile()) {
        continue;
      }
      available.put(targ.getName(), Boolean.TRUE);
      long fmod = targ.lastModified();
      long lastMod = storage.getLastMod(targ.getName());
      if (fmod > 0 && fmod == lastMod) {
        File source = new File(jsonBase, targ.getName());
        if (source.isFile()) {
          JSONObject json = readActivity(source);
          if (json != null) {
            runs.add(json);
            continue;
          }
        }
      }
      try {
        JSONObject current = new JSONObject();
        CalcDist.run(targ, 9, 100, 1, current); // default values
        storage.setLastMod(targ.getName(), targ.lastModified());
        String genby = current.getString("genby");
        String realName = storage.getName(genby);
        if (realName != null) {
          current.put("name", realName);
        }
        String realType = storage.getType(genby);
        current.put("type", realType != null ? realType : "Running");
        runs.add(current);
      } catch (Exception e) {
        System.out.println("Error processing " + targ);
        e.printStackTrace();
        available.remove(targ.getName());
      }
    }
    all = jsonBase.list();
    if (all != null) {
      for (String name : all) {
        File f = new File(jsonBase, name);
        int ind = name.lastIndexOf('.');
        if (ind != -1) {
          name = name.substring(0, ind) + ".gpx";
        }
        if (!available.containsKey(name)) {
          if (!f.delete()) {
            f.deleteOnExit();
          }
        }
      }
    }
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
      JSONObject json = readActivity(f);
      if (json != null) {
        runs.add(json);
      }
    }
    Collections.sort(runs, new RunDateComparator());
    for (JSONObject run : runs) {
      arr.put(run);
    }
    result.put("activities", arr);
    return result;
  }
  
  private JSONObject readActivity(File source) {
    byte[] buff = new byte[8192];
    InputStream is = null;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int rd = 0;
    try {
      is = new FileInputStream(source);
      while ((rd = is.read(buff)) != -1) {
        baos.write(buff, 0, rd);
      }
      JSONObject json = new JSONObject(new String(baos.toByteArray()));
      String genby = json.getString("genby");
      String realName = storage.getName(genby);
      if (realName != null) {
        json.put("name", realName);
      }
      String realType = storage.getType(genby);
      json.put("type", realType != null ? realType : "Running");
      return json;
    } catch (IOException ioe) {
      System.out.println("Error reading " + source);
      ioe.printStackTrace();
    } finally {
      try {
        if (is != null) {
          is.close();
        }
      } catch (IOException ignore) {
        // silent catch
      }
    }
    return null;
  }
  
  static void silentClose(Closeable cl) {
  	try {
  		if (cl != null) {
  			cl.close();
  		}
  	} catch (Exception ignore) {
  	}
  }
  
  JSONObject compare(JSONObject run1, JSONObject run2) {
    JSONObject result = new JSONObject();
    JSONObject general = new JSONObject();
    general.put("name1", run1.get("name"));
    general.put("name2", run2.get("name"));
    general.put("date1", run1.get("date"));
    general.put("date2", run2.get("date"));
    general.put("speed1", run1.get("avgSpeed"));
    general.put("speed2", run2.get("avgSpeed"));
    general.put("dist1", run1.get("dist"));
    general.put("dist2", run2.get("dist"));
    general.put("elePos1", run1.get("eleTotalPos"));
    general.put("elePos2", run2.get("eleTotalPos"));
    general.put("eleNeg1", run1.get("eleTotalNeg"));
    general.put("eleNeg2", run2.get("eleTotalNeg"));
    general.put("time1", run1.get("timeTotal"));
    general.put("time2", run2.get("timeTotal"));
    JSONArray splits1 = run1.getJSONArray("splits");
    JSONArray splits2 = run2.getJSONArray("splits");
    JSONArray diffsByTime = new JSONArray();
    long totalDiff = 0;
    for (int i = 0; i < Math.min(splits1.length(), splits2.length()); ++i) {
      JSONObject sp1 = splits1.getJSONObject(i);
      JSONObject sp2 = splits2.getJSONObject(i);
      double total1 = sp1.getDouble("totalRaw");
      double total2 = sp2.getDouble("totalRaw");
      if (Math.abs(total1 - total2) > 1e-3) {
        break;
      }
      JSONObject diff = new JSONObject();
      diff.put("time1", sp1.getString("time"));
      diff.put("time2", sp2.getString("time"));
      diff.put("point", String.format("%.3f", total1));
      long currentDiff = sp1.getLong("timeRaw") - sp2.getLong("timeRaw");
      diff.put("currentDiff", (currentDiff > 0 ? "+" : (currentDiff < 0 ? "-" : "")) + CalcDist.formatTime(Math.abs(currentDiff), false));
      totalDiff += currentDiff;
      diff.put("totalDiff", (totalDiff > 0 ? "+" : (totalDiff < 0 ? "-" : "")) + CalcDist.formatTime(Math.abs(totalDiff), false));
      diffsByTime.put(diff);
    }
    result.put("general", general);
    result.put("times", diffsByTime);
    return result;
  }
  
  void editActivity(String fileName, String newName, String newType) {
    storage.mapName(fileName, newName);
    storage.mapType(fileName, newType);
  }
  
  void updateActivity(JSONObject activity, String fileName) {
    String realName = storage.getName(fileName);
    if (realName != null) {
      activity.put("name", realName);
    }
    String realType = storage.getType(fileName);
    activity.put("type", realType != null ? realType : "Running");
  }
  
  List<String> getActivityNames() {
    return new ArrayList<String>(cache.keySet());
  }
  
  File getBaseFolder() {
    return base;
  }
  
  void importActivity(DataInputStream dis) {
    File output = null;
    byte[] buff = new byte[8192];
    int rd = 0;
    OutputStream os = null;
    try {
      String name = dis.readUTF();
      output = new File(gpxBase, name);
      os = new FileOutputStream(output);
      int len = dis.readInt();
      while (len > 0 && (rd = dis.read(buff, 0, len)) != -1) {
        os.write(buff, 0, len);
        len -= rd;
      }
      os.flush();
    } catch (IOException ioe) {
      System.out.println("Error importing activity");
      ioe.printStackTrace();
      if (output != null && !output.delete()) {
        output.deleteOnExit();
      }
    } finally {
      try {
        if (os != null) {
          os.close();
        }
      } catch (IOException ignore) {
        // silent catch
      }
    }
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
