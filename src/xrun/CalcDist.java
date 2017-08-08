package xrun;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CalcDist {
  
  private static final String[] MONTHS = new String[] {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };
  private static final String[] DAYS = new String[] {
    "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
  };
  
  private static final double COEF = 5.0 / 18.0; // km/h to m/s
  
  private static final double[] BOUNDS = new double[] {
      0.0, 6.0 * COEF, 7.0 * COEF, 8.0 * COEF, 9.0 * COEF, 10.0 * COEF, 11.0 * COEF, 12.0 * COEF
  }; // m/s
  
  private static final double DEFAULT_RUNNING = 9.0; // km/h
  private static final double DEFAULT_INTERVAL = 100; // meters
  
  private File file;
  private double minRunningSpeedKmh;
  private double minRunningSpeed; // m/s
  private double interval; // meters
  private double splitM; // meters
  
  private String userFriendlyName;
  private String suff = "";
  private double[] histDist = new double[BOUNDS.length];
  private double[] histElePos = new double[BOUNDS.length];
  private double[] histEleNeg = new double[BOUNDS.length];
  private double[] histTime = new double[BOUNDS.length];
  private List<Double> splitTimes = new ArrayList<Double> ();
  private List<Double> splitEle = new ArrayList<Double>();
  private double splitRem;
  private boolean isGarminTrack = false;
  
  private CalcDist(File file, double minRunningSpeed, double interval, double splitM) {
    this.file = file;
    minRunningSpeedKmh = minRunningSpeed;
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
  
  private double distance(double lat1, double lat2, double lon1,
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
    return formatTime(seconds, true);
  }
    
  static String formatTime(long seconds, boolean includeHours) {
    int hours = (int) (seconds / 3600);
    int minutes = (int) ((seconds % 3600) / 60);
    seconds = (int) seconds % 60;
    StringBuffer sb = new StringBuffer();
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
    return sb.toString();
  }
  
  private static String formatPace(double pace) {
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
  
  private static String speedToPace(double speed/*km/h*/) {
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
  
  private static String getUserFriendlyDate(String timeStart, Object[] ret) {
    StringTokenizer st = new StringTokenizer(timeStart, "-", false);
    String year = st.nextToken();
    String month = st.nextToken();
    String date = st.nextToken();
    StringBuffer sb = new StringBuffer();
    sb.append(date);
    sb.append(' ');
    sb.append(MONTHS[Integer.parseInt(month) - 1]);
    sb.append(' ');
    sb.append(year);
    Calendar cal = new GregorianCalendar();
    cal.set(Calendar.YEAR, Integer.parseInt(year));
    cal.set(Calendar.DATE, Integer.parseInt(date));
    cal.set(Calendar.MONTH, Integer.parseInt(month) - 1);
    ret[0] = cal;
    sb.append(' ');
    sb.append(DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
    return sb.toString();
  }
  
  private void process(StringBuffer sb, JSONObject data) throws Exception {
    String fileName = file.getName();
    int dott = fileName.lastIndexOf('.');
    if (dott != -1) {
      fileName = fileName.substring(0, dott);
      String tmp = fileName.length() > 2 ? fileName.substring(fileName.length() - 3) : fileName;
      try {
        Integer.parseInt(tmp);
        suff = '_' + tmp;
      } catch (Exception ignore) {
        // silent catch
      }
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
      
      for (int i = 0; i < list.getLength(); ++i) {
        Node node = list.item(i);
        String latS = node.getAttributes().getNamedItem("lat").getNodeValue();
        String lonS = node.getAttributes().getNamedItem("lon").getNodeValue();
        String eleS = getDirectChild(node, "ele").getTextContent();
        double lat = Double.parseDouble(latS);
        double lon = Double.parseDouble(lonS);
        double ele = Double.parseDouble(eleS);
        String timeS = getDirectChild(node, "time").getTextContent();
        if (i == 0) {
          timeStart = timeS.substring(0, timeS.indexOf('T'));
        }
        StringTokenizer st = new StringTokenizer(timeS, "-T:.Z", false);
        Calendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.MONTH, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.DATE, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.MINUTE, Integer.parseInt(st.nextToken()));
        cal.set(Calendar.SECOND, Integer.parseInt(st.nextToken()));
        if (st.hasMoreTokens()) {
          cal.set(Calendar.MILLISECOND, Integer.parseInt(st.nextToken()));
        }
        if (i == 0) {
          Object[] ret = new Object[1];
          data.put("date", getUserFriendlyDate(timeStart, ret));
          Calendar cc = (Calendar) ret[0];
          data.put("year", cc.get(Calendar.YEAR));
          data.put("month", cc.get(Calendar.MONTH));
          data.put("day", cc.get(Calendar.DAY_OF_MONTH));
          data.put("timeRawMs", cal.getTimeInMillis());
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
            double coef = splitM / currentDistSplits;
            double speed = currentDistSplits / currentTimeSplits;
            double ctime = splitM / speed;
            splitTimes.add(ctime);
            splitEle.add(currentEleSplits * coef);
            currentDistSplits -= splitM;
            currentTimeSplits -= ctime;
            currentEleSplits -= currentEleSplits * coef;
          } else if (lastOne) {
            splitRem = currentDistSplits;
            splitTimes.add(currentTimeSplits);
            splitEle.add(currentEleSplits);
          }
        }
        prev = new double[] {lat, lon, ele};
        lastTime = cal.getTimeInMillis();
      }
      sb.append("# Generated by " + file.getName() + "\r\n");
      data.put("genby", file.getName());
      if (isGarminTrack) {
        String fname = file.getName();
        int ind1 = fname.indexOf('_');
        int ind2 = fname.lastIndexOf('.');
        if (ind1 != -1 && ind1 < ind2) {
          data.put("garminLink", "https://connect.garmin.com/modern/activity/" + fname.substring(ind1 + 1, ind2));
        }
      }
      sb.append(name + "\r\n");
      data.put("name", convertName(name));
      sb.append(timeStart + "\r\n");
      data.put("starttime", timeStart);
      String distKm = String.format("%.3f", (distTotal / 1000.0));
      sb.append("Total distance is " + distKm + " km\r\n");
      data.put("dist", distKm);
      sb.append("Total running distance is " + String.format("%.3f", (distRunning / 1000.0)) + " km\r\n");
      data.put("distRunning", String.format("%.3f", (distRunning / 1000.0)));
      sb.append("Running(>=" + minRunningSpeedKmh + " km/h) in " + String.format("%.3f", (distRunning / distTotal) * 100.0) + "% of the path\r\n");
      sb.append("Running time " + formatTime((long) timeRunning) + "\r\n");
      data.put("timeRunning", formatTime((long) timeRunning));
      sb.append("Total time " + formatTime((long) timeTotal) + "\r\n");
      data.put("timeTotal", formatTime((long) timeTotal));
      data.put("timeTotalRaw", timeTotal);
      sb.append("Rest time " + formatTime((long) timeRest) + "\r\n");
      data.put("timeRest", formatTime((long) timeRest));
      sb.append("Average speed: " + String.format("%.3f km/h", (distTotal / timeTotal) / COEF) + "\r\n");
      data.put("avgSpeed", String.format("%.3f", (distTotal / timeTotal) / COEF));
      data.put("avgPace", speedToPace((distTotal / timeTotal) / COEF));
      data.put("avgPaceRaw", speedToPaceRaw((distTotal / timeTotal) / COEF));
      sb.append("Elevation running: " + String.format("+%dm, -%dm", (long) eleRunningPos, (long) eleRunningNeg) + "\r\n");
      data.put("eleRunningPos", (long) eleRunningPos);
      data.put("eleRunningNeg", (long) eleRunningNeg);
      sb.append("Elevation total: " + String.format("+%dm, -%dm", (long) eleTotalPos, (long) eleTotalNeg) + "\r\n");
      data.put("eleTotalPos", (long) eleTotalPos);
      data.put("eleTotalNeg", (long) eleTotalNeg);
      userFriendlyName = "Track_" + timeStart;
      userFriendlyName = userFriendlyName.replaceAll(" ", "") + "_" + distKm + "km";
    } finally {
      try {
        if (is != null) {
          is.close();
        }
      } catch (IOException ignore) {
      }
    }
  }
  
  static String run(File file, String minSpeed, String intR, String splitS, JSONObject data) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Input file not valid");
    }
    File inputBase = file.getParentFile().getParentFile();
    File outputBaseTxt = new File(inputBase, "reports_txt");
    outputBaseTxt.mkdir();
    File outputBaseJson = new File(inputBase, "reports_json");
    outputBaseJson.mkdir();
    double minR = DEFAULT_RUNNING;
    double intrv = DEFAULT_INTERVAL;
    try {
      minR = new Double(minSpeed).doubleValue();
    } catch (Exception ignore) {
    }
    try {
      intrv = new Double(intR).doubleValue();
    } catch (Exception ignore) {
    }
    double splitM = 1000.0;
    try {
      splitM = new Double(splitS).doubleValue();
    } catch (Exception ignore) {
    }
    StringBuffer sb = new StringBuffer();
    CalcDist cd = new CalcDist(file, minR, intrv, splitM * 1000.0);
    cd.process(sb, data);
    if (cd.userFriendlyName != null) {
      BufferedWriter writer = null;
      File targetTxt = new File(outputBaseTxt, cd.userFriendlyName.replace(',', '.') + "_report" + cd.suff + ".txt");
      try {
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetTxt), "UTF-8"));
        writer.write(sb.toString());
        writer.newLine();
        JSONArray arrSpeed = new JSONArray();
        writer.write("Speed distribution");
        writer.newLine();
        for (int i = 0; i < BOUNDS.length; ++i) {
          JSONObject sp = new JSONObject();
          String range = String.valueOf(Math.round(BOUNDS[i] / COEF)) +
              (i < BOUNDS.length - 1 ? "-" + String.valueOf(Math.round(BOUNDS[i + 1] / COEF)) + ""
              : "+");
          sp.put("range", range);
          writer.write(range + ": ");
          writer.write(String.format("%.3fkm", cd.histDist[i] / 1000.0) + " for " + formatTime((long) cd.histTime[i]));
          sp.put("dist", String.format("%.3f", cd.histDist[i] / 1000.0));
          sp.put("time", formatTime((long) cd.histTime[i]));
          sp.put("timeRaw", (long) cd.histTime[i]);
          writer.write(", elevation: " + String.format("+%dm, -%dm", (long) cd.histElePos[i], (long) cd.histEleNeg[i]));
          writer.newLine();
          sp.put("elePos", (long) cd.histElePos[i]);
          sp.put("eleNeg", (long) cd.histEleNeg[i]);
          arrSpeed.put(sp);
        }
        data.put("speedDist", arrSpeed);
        writer.newLine();
        JSONArray arrSplits = new JSONArray();
        writer.write("Splits");
        writer.newLine();
        double tot = 0.0;
        for (int i = 0; i < cd.splitTimes.size(); ++i) {
          JSONObject sp = new JSONObject();
          double currentLen = 0.0;
          if (i < cd.splitTimes.size() - 1 || cd.splitRem < 1e-6) {
            currentLen = cd.splitM / 1000.0;
            tot += currentLen;
            writer.write(String.format("%.3fkm: ", tot));
          } else {
            currentLen = cd.splitRem / 1000.0;
            tot += currentLen;
            writer.write(String.format("%.3fkm: ", tot));
          }
          sp.put("total", String.format("%.3f", tot));
          sp.put("totalRaw", tot);
          sp.put("len", String.format("%.3f", currentLen));
          String splitTime = formatTime((long) cd.splitTimes.get(i).doubleValue(), false);
          writer.write(splitTime);
          sp.put("time", splitTime);
          sp.put("timeRaw", (long) cd.splitTimes.get(i).doubleValue());
          double splitPace = (cd.splitTimes.get(i).doubleValue() / 60.0) / currentLen;
          sp.put("pace", formatPace(splitPace));
          sp.put("paceRaw", formatPaceRaw(splitPace));
          sp.put("speed", String.format("%.3f", 60.0 / splitPace));
          double ele = cd.splitEle.get(i);
          writer.write(", elevation: " + (ele > 0 ? "+" : "") + (long) ele + "m");
          sp.put("ele", (long) ele);
          writer.newLine();
          arrSplits.put(sp);
        }
        data.put("splits", arrSplits);
        writer.flush();
      } finally {
        try {
          if (writer != null) {
            writer.close();
          }
        } catch (IOException ignore) {
        }
      }
    }
    String fn = file.getName();
    int ind = fn.lastIndexOf('.');
    if (ind != -1) {
      fn = fn.substring(0, ind);
    }
    File jsonOut = new File(outputBaseJson, fn + ".json");
    FileWriter fwr = null;
    try {
      fwr = new FileWriter(jsonOut);
      fwr.write(data.toString());
      fwr.flush();
    } finally {
      try {
        if (fwr != null) {
          fwr.close();
        }
      } catch (IOException ignore) {
      }
    }
    return sb.toString();
  }

}
