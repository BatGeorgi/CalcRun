package xrun.app;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

public class BestSplitsCalc {

  private static final double        SAFE_DEV     = 0.02;                                // 20 metres
  private DBStorage                  storage;
  private Map<Integer, BestSplitAch> achievements = new TreeMap<Integer, BestSplitAch>();

  BestSplitsCalc(DBStorage storage) {
    this.storage = storage;
    try {
      achievements = storage.retrieveBestSplits();
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (achievements.isEmpty()) {
      scanAll();
    }
  }

  public Map<Integer, BestSplitAch> getBest() {
    return new HashMap<Integer, BestSplitAch>(achievements);
  }

  public void addInfo(String id, JSONObject coords) {
    if (calcAchievements(id, coords)) {
      try {
        storage.updateBestSplits(achievements, false);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  public void removeInfo(String genby) {
    boolean found = false;
    for (BestSplitAch ach : achievements.values()) {
      if (ach.id.equals(genby)) {
        found = true;
      }
    }
    if (found) {
      achievements.clear();
      scanAll();
    }
  }

  private void scanAll() {
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
      storage.updateBestSplits(achievements, true);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean calcAchievements(String id, JSONObject coords) {
    if (coords == null || !coords.has("lats")) {
      return false; // must not happen
    }
    boolean modified = false;
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
      double allDev = i == 0 ? SAFE_DEV : 0;
      for (int dist = 1; dist <= (int) (totalDistance - current + allDev); ++dist) {
        double target = current + (double) dist;
        BestSplitAch best = achievements.get(dist);
        BestSplitAch candidate = new BestSplitAch(id, current);
        int pos = Collections.binarySearch(accDist, target);
        if (pos >= 0) {
          candidate.time = times.getLong(pos) - startTime;
        } else {
          pos = -pos - 1;
          if (pos >= len) {
            pos = len - 1; // safe deviation
            candidate.time = times.getLong(pos) - startTime;
          }
          if (pos == 0) {
            continue; // must not be possible
          }
          if (candidate.time == 0) {
            double distLow = accDist.get(pos - 1);
            double distHigh = accDist.get(pos);
            double coef = (target - distLow) / (distHigh - distLow);
            double timeLow = times.getLong(pos - 1);
            double timeHigh = times.getLong(pos);
            candidate.time = (long) (timeLow + coef * (timeHigh - timeLow)) - startTime;
          }
        }
        if (best == null || candidate.time < best.time) {
          achievements.put(dist, candidate);
          modified = true;
        }
      }
    }
    return modified;
  }

}
