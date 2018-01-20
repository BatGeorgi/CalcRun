package xrun;

import java.lang.reflect.Array;
import java.util.StringTokenizer;

import org.json.JSONArray;
import org.json.JSONObject;

class ChartUtils {
	
	private static final String[] TYPES = new String[] { RunCalcUtils.RUNNING,
			RunCalcUtils.TRAIL, RunCalcUtils.UPHILL, RunCalcUtils.HIKING,
			RunCalcUtils.WALKING, RunCalcUtils.OTHER };
	private static final int[] BOUNDS_DIST = new int[] { 0, 4, 5, 6, 7, 8, 10,
			12, 15, 20, 30, 40, 50 };
	private static final double[] BOUNDS_SPEED = new double[] { 0, 4, 5, 6, 9,
			10, 11, 11.5, 12, 12.5, 13, 13.5, 14, 14.5, 15, 16 };
	private static final int[] BOUNDS_ELE = new int[] { 0, 250, 500, 1000, 1300,
			1500, 1800, 2000, 2500, 3000 };
	private static final int[] BOUNDS_RUNDIST = new int[] { 0, 4, 5, 6, 7, 8, 10,
			12, 15, 20, 30, 40, 50 };
	private static final double[] BOUNDS_DURATION = new double[] { 0, 0.25, 0.5,
			0.66, 0.83333, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10 };
	private static final int[] BOUNDS_HOUR = new int[] { 0, 8, 9, 10, 12, 15, 18,
			19, 20 };
	
	static JSONObject getResultCharts(JSONArray activities) {
		JSONObject result = new JSONObject();
    int[] typesCnt = new int[TYPES.length];
    int[] distCnt = new int[BOUNDS_DIST.length];
    int[] speedCnt = new int[BOUNDS_SPEED.length];
    int[] eleCnt = new int[BOUNDS_ELE.length];
    int[] rundistCnt = new int[BOUNDS_RUNDIST.length];
    int[] durationCnt = new int[BOUNDS_DURATION.length];
    int[] dayCnt = new int[7];
    int[] hourCnt = new int[BOUNDS_HOUR.length];
    for (int i = 0; i < activities.length(); ++i) {
      JSONObject activity = activities.getJSONObject(i);
      fillInType(typesCnt, activity.getString("type"));
      fillInDist(distCnt, activity.getDouble("distRaw"));
      fillInSpeed(speedCnt, activity.getDouble("avgSpeedRaw"));
      fillInEle(eleCnt, activity.getLong("eleTotalPos"));
      fillInRun(rundistCnt, activity.getDouble("distRunningRaw"));
      fillInDuration(durationCnt, activity.getDouble("timeTotalRaw"));
      fillInDay(dayCnt, activity.getString("date"));
      fillInHour(hourCnt, activity.getString("startAt"));
    }
    result.put("byType", toJSONArray(typesCnt, TYPES));
    result.put("byDist", toJSONArray(distCnt, BOUNDS_DIST));
    result.put("bySpeed", toJSONArray(speedCnt, BOUNDS_SPEED));
    result.put("byEle", toJSONArray(eleCnt, BOUNDS_ELE));
    result.put("byRun", toJSONArray(rundistCnt, BOUNDS_RUNDIST));
    JSONArray byDay = toJSONArray(dayCnt, CalcDist.DAYS, true);
    JSONObject sun = byDay.getJSONObject(0);
    for (int i = 0; i < 6; ++i) {
    	byDay.put(i, byDay.getJSONObject(i + 1));
    }
    byDay.put(6, sun);
    result.put("byDay", byDay);
    result.put("byHour", toJSONArray(hourCnt, BOUNDS_HOUR));
    String[] DUR = new String[BOUNDS_DURATION.length];
    for (int i = 0; i < BOUNDS_DURATION.length; ++i) {
    	double di = BOUNDS_DURATION[i];
    	double di1 = i < BOUNDS_DURATION.length - 1 ? BOUNDS_DURATION[i + 1] : -1;
    	DUR[i] = di1 >= 0 ? (getDuration(di) + "-" + getDuration(di1)) : (getDuration(di) + "+");
    }
    result.put("byDuration", toJSONArray(durationCnt, DUR));
    return result;
  }
	
	private static String formatBounds(Number fr, Number to) {
		StringBuffer sb = new StringBuffer();
		if (Math.abs(fr.doubleValue() - Math.round(fr.doubleValue())) < 1e-3) {
			sb.append(Math.round(fr.doubleValue()));
		} else {
			sb.append(String.format("%.1f", fr.doubleValue()));
		}
		if (to == null) {
			sb.append("+");
		} else if (Math.abs(to.doubleValue() - Math.round(to.doubleValue())) < 1e-3) {
			sb.append('-');
			sb.append(Math.round(to.doubleValue()));
		} else {
			sb.append('-');
			sb.append(String.format("%.1f", to.doubleValue()));
		}
		return sb.toString();
	}
	
	private static JSONArray toJSONArray(int[] cnt, Object bounds) {
		return toJSONArray(cnt, bounds, false);
	}
	
	private static JSONArray toJSONArray(int[] cnt, Object bounds, boolean includeEmpty) {
		boolean isInt = bounds instanceof int[];
		boolean isString = bounds instanceof String[];
		JSONArray result = new JSONArray();
		int len = cnt.length;
		for (int i = 0; i < len; ++i) {
			if (!includeEmpty && cnt[i] == 0) {
				continue;
			}
			String key = null;
			if (isString) {
				key = (String) Array.get(bounds, i);
			} else {
				Number fr = (isInt ? Array.getInt(bounds, i) : Array.getDouble(bounds, i));
				Number to = (i < len - 1 ? (isInt ? Array.getInt(bounds, i + 1) : Array.getDouble(bounds, i + 1)) : null);
				key = formatBounds(fr, to);
			}
			JSONObject current = new JSONObject();
			current.put("info", key);
			current.put("data", cnt[i]);
			result.put(current);
		}
		return result;
	}
  
  private static void fillInType(int[] cnt, String type) {
    for (int i = 0; i < TYPES.length; ++i) {
      if (TYPES[i].equals(type)) {
        ++cnt[i];
        break;
      }
    }
  }

  private static void fillInDist(int[] cnt, double dist) {
    for (int i = BOUNDS_DIST.length - 1; i >= 0; --i) {
      if (dist >= BOUNDS_DIST[i]) {
        ++cnt[i];
        break;
      }
    }
  }

  private static void fillInSpeed(int[] cnt, double speed) {
    for (int i = BOUNDS_SPEED.length - 1; i >= 0; --i) {
      if (speed >= BOUNDS_SPEED[i]) {
        ++cnt[i];
        break;
      }
    }
  }

  private static void fillInEle(int[] cnt, double ele) {
    for (int i = BOUNDS_ELE.length - 1; i >= 0; --i) {
      if (ele >= BOUNDS_ELE[i]) {
        ++cnt[i];
        break;
      }
    }
  }

  private static void fillInRun(int[] cnt, double run) {
    for (int i = BOUNDS_RUNDIST.length - 1; i >= 0; --i) {
      if (run >= BOUNDS_RUNDIST[i]) {
       ++cnt[i];
        break;
      }
    }
  }

  private static void fillInDuration(int[] cnt, double duration) {
    double toHours = duration / 3600;
    for (int i = BOUNDS_DURATION.length - 1; i >= 0; --i) {
      if (toHours >= BOUNDS_DURATION[i]) {
        ++cnt[i];
        break;
      }
    }
  }
  
  private static void fillInDay(int[] cnt, String date) {
    StringTokenizer st = new StringTokenizer(date, " " , false);
    st.nextToken(); // day
    st.nextToken(); // month
    st.nextToken(); // year
    String day = st.nextToken();
    for (int i = 0; i < CalcDist.DAYS.length; ++i) {
    	if (CalcDist.DAYS[i].equals(day)) {
    		++cnt[i];
    		break;
    	}
    }
  }
  
  private static void fillInHour(int[] cnt, String startAt) {
    StringTokenizer st = new StringTokenizer(startAt, " :", false);
    st.nextToken(); // day
    st.nextToken(); // month
    st.nextToken(); // year
    int hour = Integer.valueOf(st.nextToken());
    for (int i = BOUNDS_HOUR.length - 1; i >= 0; --i) {
      if (hour >= BOUNDS_HOUR[i]) {
        ++cnt[i];
        break;
      }
    }
  }
  
  private static String getDuration(double hours) {
    return (hours >= 1.0 ? (hours + "h") : (Math.round(hours * 60) + "m"));
  }

}
