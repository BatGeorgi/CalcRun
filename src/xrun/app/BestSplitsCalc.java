package xrun.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import xrun.common.Constants;
import xrun.storage.DBStorage;
import xrun.utils.JsonSanitizer;
import xrun.utils.TimeUtils;

public class BestSplitsCalc implements Runnable {

  private DBStorage storage;
  private Map<Integer, BestSplitAch> achievements = new TreeMap<Integer, BestSplitAch>();

  BestSplitsCalc(DBStorage storage) {
    this.storage = storage;
  }

  @Override
  public void run() {
    Map<String, JSONObject> coords = null;
    try {
      coords = storage.getAllCoords();
      for (Entry<String, JSONObject> entry : coords.entrySet()) {
        JSONObject data = storage.getActivity(entry.getKey());
        if (DBStorage.isExternal(new JSONArray(JsonSanitizer.sanitize(data.getString("dashboards"))))) {
          continue;
        }
        if (Constants.OTHER.equals(data.getString("type"))) {
          continue;
        }
        calcAchievements(entry.getKey(), entry.getValue());
      }
      for (Entry<Integer, BestSplitAch> entry : achievements.entrySet()) {
        BestSplitAch ach = entry.getValue();
        System.out.println(entry.getKey() + "km " + ach.id + " " + ach.startPoint + " "
            + TimeUtils.formatTime((long) (ach.time / 1000.0), true));
        JSONObject data = storage.getActivity(ach.id);
        System.out.println(data.get("date"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void calcAchievements(String id, JSONObject coords) {
    JSONArray lats = coords.getJSONArray("lats");
    JSONArray lons = coords.getJSONArray("lons");
    JSONArray times = coords.getJSONArray("times");
    int len = lats.length();
    List<Double> accDist = new ArrayList<Double>(len);
    accDist.add(0.0);
    for (int i = 1; i < len; ++i) {
      accDist.add(accDist.get(i - 1) + TimeUtils.distance(lats.getDouble(i - 1), lats.getDouble(i),
          lons.getDouble(i - 1), lons.getDouble(i)) / 1000.0);
    }
    double totalDistance = accDist.get(len - 1);
    for (int i = 0; i < len; ++i) {
      double current = accDist.get(i);
      long startTime = times.getLong(i);
      for (int dist = 1; dist <= (int) (totalDistance - current); ++dist) {
        BestSplitAch best = achievements.get(dist);
        BestSplitAch candidate = new BestSplitAch(id, current);
        int pos = Collections.binarySearch(accDist, current + (double) dist);
        if (pos >= 0) {
          candidate.time = times.getLong(pos) - startTime;
        } else {
          pos = -pos - 1;
          if (pos >= len) {
            break; // must not be possible
          }
          if (pos == 0) {
            continue;  // must not be possible
          }
          double distLow = accDist.get(pos - 1) - current;
          double distHigh = accDist.get(pos) - current;
          double coef = (dist - distLow) / (distHigh - distLow);
          double timeLow = times.getLong(pos - 1);
          double timeHigh = times.getLong(pos);
          candidate.time = (long) (timeLow + coef * (timeHigh - timeLow)) - startTime;
        }
        if (best == null || candidate.time < best.time) {
          achievements.put(dist, candidate);
        }
      }
    }
  }

}

class BestSplitAch {

  String id;
  long time; // milliseconds
  double startPoint;
  
  BestSplitAch (String id, double startPoint) {
    this.id = id;
    this.startPoint = startPoint;
  }

}