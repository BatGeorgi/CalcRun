package xrun.utils;

import java.util.Calendar;
import java.util.StringTokenizer;

import xrun.common.Constants;

public class TimeUtils {

  public static String formatTime(long seconds) {
    return formatTime(seconds, true, false);
  }

  public static String formatTime(long seconds, boolean includeHours) {
    return formatTime(seconds, includeHours, false);
  }

  public static String formatTime(long seconds, boolean includeHours, boolean includeDays) {
    int hours = (int) (seconds / 3600);
    int minutes = (int) ((seconds % 3600) / 60);
    seconds = (int) seconds % 60;
    StringBuffer sb = new StringBuffer();
    if (includeDays && hours >= 24) {
      int days = hours / 24;
      hours %= 24;
      sb.append(days + "d, ");
    }
    if (includeHours || hours > 0) {
      if (hours < 10) {
        sb.append('0');
      }
      sb.append(hours);
      sb.append(':');
    }
    if (minutes < 10) {
      sb.append('0');
    }
    sb.append(minutes);
    sb.append(':');
    if (seconds < 10) {
      sb.append('0');
    }
    sb.append(seconds);
    if (includeDays) {

    }
    return sb.toString();
  }

  public static long getRealTime(String formattedTime) {
    StringTokenizer st = new StringTokenizer(formattedTime, ":", false);
    long total = 0;
    long[] mults = new long[] {
        3600, 60, 1
    };
    int it = 0;
    if (st.countTokens() < 3) {
      ++it;
    }
    while (st.hasMoreTokens()) {
      total += mults[it++] * (long) Integer.parseInt(st.nextToken());
    }
    return total;
  }

  public static String formatPace(double pace) {
    int minutes = (int) pace;
    int seconds = (int) ((pace - minutes) * 60.0);
    StringBuffer sb = new StringBuffer();
    sb.append(minutes);
    sb.append(':');
    if (seconds < 10) {
      sb.append('0');
    }
    sb.append(seconds);
    return sb.toString();
  }

  public static int[] formatPaceRaw(double pace) {
    int minutes = (int) pace;
    int seconds = (int) ((pace - minutes) * 60.0);
    return new int[] {
        minutes, seconds
    };
  }

  private static void appUnit(StringBuffer sb, int unit) {
    if (unit < 10) {
      sb.append('0');
    }
    sb.append(unit);
  }

  public static String formatDate(Calendar cal, boolean startTime) {
    StringBuffer sb = new StringBuffer();
    appUnit(sb, cal.get(Calendar.DAY_OF_MONTH));
    sb.append(' ');
    sb.append(Constants.MONTHS[cal.get(Calendar.MONTH)]);
    sb.append(' ');
    sb.append(cal.get(Calendar.YEAR));
    sb.append(' ');
    if (startTime) {
      appUnit(sb, cal.get(Calendar.HOUR_OF_DAY));
      sb.append(':');
      appUnit(sb, cal.get(Calendar.MINUTE));
    } else {
      sb.append(Constants.DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
    }
    return sb.toString();
  }

  public static String speedToPace(double speed/*km/h*/) {
    double pace = 60.0 / speed;
    int mins = (int) pace;
    double seconds = (pace - mins) * 60.0;
    if (seconds < 0) {
      seconds = 0;
    }
    int s = (int) seconds;
    String ss = (s < 10 ? "0" + s : String.valueOf(s));
    return String.format("%d:%s", mins, ss);
  }

  public static double distance(double lat1, double lat2, double lon1,
      double lon2) {
    final int R = 6371; // Radius of the earth
    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c * 1000.0; // convert to meters
  }
  
  public static String formatNoSeconds(int seconds) {
    int minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m";
    } else {
      String label = String.valueOf((int) (minutes / 60.0)) + 'h';
      if (minutes % 60 > 0) {
        label += (minutes % 60) + "m";
      }
      return label;
    }
  }
}
