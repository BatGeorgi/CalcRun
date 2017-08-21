package xrun;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class RunCalcUtils {
  
  private File gpxBase;
  private SQLiteManager sqLite;
  
  RunCalcUtils(File base) {
    sqLite = new SQLiteManager(base);
    gpxBase = new File(base, "gpx");
    gpxBase.mkdirs();
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
    List<JSONObject> runs = new ArrayList<JSONObject>();
    String[] all = gpxBase.list();
    sqLite.dropExistingDB();
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx") || !sqLite.hasRecord(fileName)) {
        continue;
      }
      File targ = new File(gpxBase, fileName);
      if (!targ.isFile()) {
        continue;
      }
      try {
        JSONObject current = new JSONObject();
        CalcDist.run(targ, 9, 100, 1, current); // default values
        runs.add(current);
        sqLite.addEntry(current);
      } catch (Exception e) {
        System.out.println("Error processing " + targ);
        e.printStackTrace();
      }
    }
  }
  
  JSONObject retrieveAllActivities() {
    JSONObject result = new JSONObject();
    result.put("activities", sqLite.retrieveAll());
    return result;
  }
  
  List<JSONObject> filter(boolean run, boolean trail, boolean hike, boolean walk, boolean other,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    return sqLite.filter(run, trail, hike, walk, other, startDate, endDate, minDistance, maxDistance);
  }
  
  JSONObject getActivity(String fileName) {
    return sqLite.getActivity(fileName);
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
    sqLite.updateEntry(fileName, newName, newType);
  }
  
  void dispose() {
    if (sqLite != null) {
      sqLite.close();
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
