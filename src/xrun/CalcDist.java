package xrun;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CalcDist {
  
  static final String FILE_SUFF = "_-REV-_";
  
  static final String[] MONTHS = new String[] {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };
  
  static final double DEFAULT_MIN_SPEED = 9;
  static final double DEFAULT_INTERVAL = 100;
  static final double DEFAULT_SPLIT = 1;
  
  static final String[] DAYS = new String[] {
    "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
  };
  
  private static final double COEF = 5.0 / 18.0; // km/h to m/s
  
  private static final double[] BOUNDS = new double[] {
      0.0, 6.0 * COEF, 7.0 * COEF, 8.0 * COEF, 9.0 * COEF, 10.0 * COEF, 11.0 * COEF, 12.0 * COEF
  }; // m/s
  
  static final long CORRECTION_BG_WINTER = 2 * 3600 * 1000; // 2 hours
  static final long CORRECTION_BG_SUMMER = 3 * 3600 * 1000; // 3 hours
  
  private File file;
  private double minRunningSpeed; // m/s
  private double interval; // meters
  private double splitM; // meters
  
  private double[] histDist = new double[BOUNDS.length];
  private double[] histElePos = new double[BOUNDS.length];
  private double[] histEleNeg = new double[BOUNDS.length];
  private double[] histTime = new double[BOUNDS.length];
  private List<Double> splitTimes = new ArrayList<Double> ();
  private List<Double> splitEle = new ArrayList<Double>();
  private List<Double> splitAccElePos = new ArrayList<Double>();
  private List<Double> splitAccEleNeg = new ArrayList<Double>();
  private double splitRem;
  private boolean isGarminTrack = false;
  
  private JSONArray lats = new JSONArray();
  private JSONArray lons = new JSONArray();
  private JSONArray times = new JSONArray();
  private JSONArray markers = new JSONArray();
  
  private CalcDist(File file, double minRunningSpeed, double interval, double splitM) {
    this.file = file;
    this.minRunningSpeed = (minRunningSpeed * 5.0) / 18.0; // convert to m/s
    this.interval = interval;
    this.splitM = splitM;
  }
  
  private Node getDirectChild(Node parent, String name) {
      for(Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
          if(name.equals(child.getNodeName())) {
            return child;
          }
      }
      return null;
  }
  
  static double distance(double lat1, double lat2, double lon1,
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
  
  private static String formatTime(long seconds) {
    return formatTime(seconds, true, false);
  }
  
  static String formatTime(long seconds, boolean includeHours) {
    return formatTime(seconds, includeHours, false);
  }
    
  static String formatTime(long seconds, boolean includeHours, boolean includeDays) {
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
  
  static long getRealTime(String formattedTime) {
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
  
  static String formatPace(double pace) {
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
  
  private static int[] formatPaceRaw(double pace) {
    int minutes = (int) pace;
    int seconds = (int) ((pace - minutes) * 60.0);
    return new int[] {minutes, seconds};
  }
  
  private String convertName(String name) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < name.length(); ++i) {
      char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        sb.append(c);
      } else if (Character.isWhitespace(c) || Character.isDigit(c)) {
        sb.append(c);
      }
    }
    return sb.toString().trim();
  }
  
  static String speedToPace(double speed/*km/h*/) {
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
  
  private static int[] speedToPaceRaw(double speed/*km/h*/) {
    double pace = 60.0 / speed;
    int mins = (int) pace;
    double seconds = (pace - mins) * 60.0;
    return new int[] {mins, (int) seconds};
  }
  
  private void hist(double speed, double dist, double time, double ele) {
    for (int i = BOUNDS.length - 1; i >= 0; --i) {
      if (speed >= BOUNDS[i]) {
        histDist[i] += dist;
        histTime[i] += time;
        if (ele > 0) {
          histElePos[i] += ele;
        } else {
          histEleNeg[i] -= ele;
        }
        break;
      }
    }
  }
  
  private static void appUnit(StringBuffer sb, int unit) {
    if (unit < 10) {
      sb.append('0');
    }
    sb.append(unit);
  }
  
  static String formatDate(Calendar cal, boolean startTime) {
    StringBuffer sb = new StringBuffer();
    appUnit(sb, cal.get(Calendar.DAY_OF_MONTH));
    sb.append(' ');
    sb.append(CalcDist.MONTHS[cal.get(Calendar.MONTH)]);
    sb.append(' ');
    sb.append(cal.get(Calendar.YEAR));
    sb.append(' ');
    if (startTime) {
      appUnit(sb, cal.get(Calendar.HOUR_OF_DAY));
      sb.append(':');
      appUnit(sb, cal.get(Calendar.MINUTE));
    } else {
      sb.append(CalcDist.DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
    }
    return sb.toString();
  }
  
  static Calendar getDateWithCorrection(long timeRawMs) {
    Calendar cal = new GregorianCalendar();
    cal.setTimeInMillis(timeRawMs);
    long corr = TimeZone.getDefault().inDaylightTime(cal.getTime()) ? CORRECTION_BG_SUMMER : CORRECTION_BG_WINTER;
    cal.setTimeInMillis(timeRawMs + corr);
    return cal;
  }
  
  private void process(JSONObject data) throws Exception {
    String fileName = file.getName();
    String garminName = fileName;
    int ind = garminName.indexOf(FILE_SUFF);
    if (ind != -1) {
      garminName = garminName.substring(0, ind);
    }
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    InputStream is = null;
    try {
      is = new FileInputStream(file);
      Document document = builder.parse(is);
      NodeList gpx = document.getElementsByTagName("gpx");
      if (gpx != null && gpx.getLength() > 0) {
        Node creator = gpx.item(0).getAttributes().getNamedItem("creator");
        if (creator != null) {
          String val = creator.getNodeValue();
          if (val != null && val.startsWith("Garmin")) {
            isGarminTrack = true;
          }
        }
      }
      String name = document.getElementsByTagName("name").item(0).getTextContent();
      String timeStart = null;
      NodeList list = document.getElementsByTagName("trkpt");
      long lastTime = Long.MIN_VALUE;
      double[] prev = null;
      double currentDist = 0.0;
      double currentTime = 0.0;
      double distRunning = 0.0;
      double timeRunning = 0.0;
      double distTotal = 0.0;
      double timeTotal = 0.0;
      double timeRest = 0.0;
      double currentDistSplits = 0.0;
      double currentTimeSplits = 0.0;
      double currentEleSplits = 0.0;
      
      double currentEle = 0.0;
      double eleRunningPos = 0.0;
      double eleRunningNeg = 0.0;
      double eleTotalPos = 0.0;
      double eleTotalNeg = 0.0;
      
      long stt = 0;
      for (int i = 0; i < list.getLength(); ++i) {
        Node node = list.item(i);
        String latS = node.getAttributes().getNamedItem("lat").getNodeValue();
        String lonS = node.getAttributes().getNamedItem("lon").getNodeValue();
        String eleS = getDirectChild(node, "ele").getTextContent();
        double lat = Double.parseDouble(latS);
        double lon = Double.parseDouble(lonS);
        lats.put(lat);
        lons.put(lon);
        double ele = Double.parseDouble(eleS);
        String timeS = getDirectChild(node, "time").getTextContent();
        if (i == 0) {
          timeStart = timeS.substring(0, timeS.indexOf('T'));
        }
        StringTokenizer st = new StringTokenizer(timeS, "-T:.Z", false);
        Calendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.MONTH, Integer.parseInt(st.nextToken()) - 1);
        cal.set(Calendar.DATE, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.MINUTE, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.SECOND, Integer.parseInt(st.nextToken()));
        if (st.hasMoreTokens()) {
          cal.set(Calendar.MILLISECOND, Integer.parseInt(st.nextToken()));
        }
        if (i == 0) {
          stt = cal.getTimeInMillis();
        }
        times.put(cal.getTimeInMillis() - stt);
        if (i == 0) {
          data.put("timeRawMs", cal.getTimeInMillis());
          Calendar corrected = getDateWithCorrection(cal.getTimeInMillis());
          data.put("date", formatDate(corrected, false));
          data.put("year", corrected.get(Calendar.YEAR));
          data.put("month", corrected.get(Calendar.MONTH));
          data.put("day", corrected.get(Calendar.DAY_OF_MONTH));
        }
        if (i > 0) {
          double tempDist = distance(prev[0], lat, prev[1], lon);
          currentDist += tempDist;
          currentDistSplits += tempDist;
          double timeDiff = (cal.getTimeInMillis() - lastTime) / 1000.0;
          currentTime += timeDiff;
          currentTimeSplits += timeDiff;
          currentEle += ele - prev[2];
          currentEleSplits += ele - prev[2];
          if (currentDist / currentTime < 0.5) {
            timeRest += timeDiff;
          }
          boolean lastOne = i == list.getLength() - 1;
          if (currentDist >= interval || lastOne) {
            double speed = currentDist / currentTime;
            hist(speed, currentDist, currentTime, currentEle);
            if (speed >= minRunningSpeed) {
              distRunning += currentDist;
              timeRunning += currentTime;
              if (currentEle > 0) {
                eleRunningPos += currentEle;
              } else {
                eleRunningNeg -= currentEle;
              }
            }
            distTotal += currentDist;
            timeTotal += currentTime;
            if (currentEle > 0) {
              eleTotalPos += currentEle;
            } else {
              eleTotalNeg -= currentEle;
            }
            currentDist = 0.0;
            currentTime = 0.0;
            currentEle = 0.0;
          }
          if (currentDistSplits >= splitM) {
          	markers.put(i);
          	splitAccElePos.add(eleTotalPos);
          	splitAccEleNeg.add(eleTotalNeg);
            double coef = splitM / currentDistSplits;
            double speed = currentDistSplits / currentTimeSplits;
            double ctime = splitM / speed;
            splitTimes.add(ctime);
            splitEle.add(currentEleSplits * coef);
            currentDistSplits -= splitM;
            currentTimeSplits -= ctime;
            currentEleSplits -= currentEleSplits * coef;
          } else if (lastOne) {
          	splitAccElePos.add(eleTotalPos);
          	splitAccEleNeg.add(eleTotalNeg);
            splitRem = currentDistSplits;
            splitTimes.add(currentTimeSplits);
            splitEle.add(currentEleSplits);
          }
        }
        prev = new double[] {lat, lon, ele};
        lastTime = cal.getTimeInMillis();
      }
      data.put("genby", file.getName());
      data.put("garminLink", "none");
      if (isGarminTrack) {
        String fname = garminName;
        int ind1 = fname.indexOf('_');
        int ind2 = fname.lastIndexOf('.');
        if (ind1 != -1 && ind1 < ind2) {
          data.put("garminLink", "https://connect.garmin.com/modern/activity/" + fname.substring(ind1 + 1, ind2));
        }
      }
      data.put("name", convertName(name));
      data.put("starttime", timeStart);
      data.put("type", RunCalcUtils.RUNNING);
      String distKm = String.format("%.3f", (distTotal / 1000.0));
      data.put("dist", distKm);
      data.put("distRaw", distTotal / 1000.0);
      data.put("distRunning", String.format("%.3f", (distRunning / 1000.0)));
      data.put("distRunningRaw", distRunning / 1000.0);
      data.put("timeRunning", formatTime((long) timeRunning));
      data.put("timeTotal", formatTime((long) timeTotal));
      data.put("timeTotalRaw", timeTotal);
      data.put("timeRest", formatTime((long) timeRest));
      data.put("avgSpeed", String.format("%.3f", (distTotal / timeTotal) / COEF));
      data.put("avgSpeedRaw", (distTotal / timeTotal) / COEF);
      data.put("avgPace", speedToPace((distTotal / timeTotal) / COEF));
      data.put("avgPaceRaw", speedToPaceRaw((distTotal / timeTotal) / COEF));
      data.put("eleRunningPos", (long) eleRunningPos);
      data.put("eleRunningNeg", (long) eleRunningNeg);
      data.put("eleTotalPos", (long) eleTotalPos);
      data.put("eleTotalNeg", (long) eleTotalNeg);
    } finally {
      try {
        if (is != null) {
          is.close();
        }
      } catch (IOException ignore) {
      }
    }
  }
  
  static JSONObject run(File file) throws Exception {
  	JSONObject data = new JSONObject();
  	run(file, DEFAULT_MIN_SPEED, DEFAULT_INTERVAL, DEFAULT_SPLIT, data);
  	return data;
  }
  
  static void run(File file, double minSpeed, double intR, double splitS, JSONObject data) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Input file not valid");
    }
    CalcDist cd = new CalcDist(file, minSpeed, intR, splitS * 1000.0);
    cd.process(data);
    data.put("lats", cd.lats);
    data.put("lons", cd.lons);
    data.put("times", cd.times);
    data.put("markers", cd.markers);
    JSONArray arrSpeed = new JSONArray();
    for (int i = 0; i < BOUNDS.length; ++i) {
      JSONObject sp = new JSONObject();
      String range = String.valueOf(Math.round(BOUNDS[i] / COEF)) +
          (i < BOUNDS.length - 1 ? "-" + String.valueOf(Math.round(BOUNDS[i + 1] / COEF)) + ""
              : "+");
      sp.put("range", range);
      sp.put("dist", String.format("%.3f", cd.histDist[i] / 1000.0));
      sp.put("time", formatTime((long) cd.histTime[i]));
      sp.put("timeRaw", (long) cd.histTime[i]);
      sp.put("elePos", (long) cd.histElePos[i]);
      sp.put("eleNeg", (long) cd.histEleNeg[i]);
      arrSpeed.put(sp);
    }
    data.put("speedDist", arrSpeed);
    JSONArray arrSplits = new JSONArray();
    double tot = 0.0;
    double timeTotalRaw = 0;
    for (int i = 0; i < cd.splitTimes.size(); ++i) {
      JSONObject sp = new JSONObject();
      double currentLen = 0.0;
      if (i < cd.splitTimes.size() - 1 || cd.splitRem < 1e-6) {
        currentLen = cd.splitM / 1000.0;
        tot += currentLen;
      } else {
        currentLen = cd.splitRem / 1000.0;
        tot += currentLen;
      }
      sp.put("accElePos", (long) cd.splitAccElePos.get(i).doubleValue());
      sp.put("accEleNeg", (long) cd.splitAccEleNeg.get(i).doubleValue());
      sp.put("total", String.format("%.3f", tot));
      sp.put("totalRaw", tot);
      sp.put("len", String.format("%.3f", currentLen));
      String splitTime = formatTime((long) cd.splitTimes.get(i).doubleValue(), false);
      sp.put("time", splitTime);
      sp.put("timeRaw", Math.round(cd.splitTimes.get(i).doubleValue()));
      timeTotalRaw += cd.splitTimes.get(i).doubleValue();
      sp.put("timeTotalRaw", Math.round(timeTotalRaw));
      sp.put("timeTotal", formatTime(Math.round(timeTotalRaw), true));
      double splitPace = (cd.splitTimes.get(i).doubleValue() / 60.0) / currentLen;
      sp.put("pace", formatPace(splitPace));
      sp.put("paceRaw", formatPaceRaw(splitPace));
      sp.put("speed", String.format("%.3f", 60.0 / splitPace));
      sp.put("accumSpeed", String.format("%.3f", tot / (timeTotalRaw / 3600.0)));
      double ele = cd.splitEle.get(i);
      sp.put("ele", (long) ele);
      sp.put("eleD", ele);
      arrSplits.put(sp);
    }
    data.put("splits", arrSplits);
  }

}
