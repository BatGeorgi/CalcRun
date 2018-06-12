package xrun.parser;

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

import xrun.common.Constants;
import xrun.utils.TimeUtils;

public class TrackParser {
  
  private static final long     SPLIT_DISTANCE_UNIT        = 300;                               // 5 mins
  private static final int      SPLIT_DISTANCE_SKIP_MIN    = 2;                                 // 10 mins
  private static final int      SPLIT_DISTANCE_SKIP_SMALL  = 3;                                 // 15 mins
  private static final int      SPLIT_DISTANCE_SKIP_MEDIUM = 6;                                 // 30 mins
  private static final int      SPLIT_DISTANCE_SKIP_BIG    = 12;                                // hour

  private static final double   DEFAULT_MIN_SPEED      = 9;
  private static final double   DEFAULT_INTERVAL       = 100;
  private static final double   DEFAULT_SPLIT_DISTANCE = 1;

  private static final double   COEF              = 5.0 / 18.0;                                 // km/h to m/s

  private static final double[] BOUNDS            = new double[] {
      0.0, 6.0 * COEF, 7.0 * COEF, 8.0 * COEF, 9.0 * COEF, 10.0 * COEF, 11.0 * COEF, 12.0 * COEF
  };                                                                                            // m/s

  private File                  file;
  private double                minRunningSpeed;                                                // m/s
  private double                interval;                                                       // meters
  private double                splitM;                                                         // meters

  private double[]              histDist                   = new double[BOUNDS.length];
  private double[]              histElePos                 = new double[BOUNDS.length];
  private double[]              histEleNeg                 = new double[BOUNDS.length];
  private double[]              histTime                   = new double[BOUNDS.length];
  private List<Double>          splitTimes                 = new ArrayList<Double>();
  private List<Double>          splitEle                   = new ArrayList<Double>();
  private List<Double>          splitAccElePos             = new ArrayList<Double>();
  private List<Double>          splitAccEleNeg             = new ArrayList<Double>();
  private List<Double>          splitDists                 = new ArrayList<Double>();
  private double                splitRem;
  private boolean               isGarminTrack              = false;

  private JSONArray             lats                       = new JSONArray();
  private JSONArray             lons                       = new JSONArray();
  private JSONArray             times                      = new JSONArray();
  private JSONArray             markers                    = new JSONArray();

  private TrackParser(File file, double minRunningSpeed, double interval, double splitM) {
    this.file = file;
    this.minRunningSpeed = (minRunningSpeed * 5.0) / 18.0; // convert to m/s
    this.interval = interval;
    this.splitM = splitM;
  }

  private Node getDirectChild(Node parent, String name) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (name.equals(child.getNodeName())) {
        return child;
      }
    }
    return null;
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

  private int[] speedToPaceRaw(double speed/*km/h*/) {
    double pace = 60.0 / speed;
    int mins = (int) pace;
    double seconds = (pace - mins) * 60.0;
    return new int[] {
        mins, (int) seconds
    };
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

  private Calendar getDateWithCorrection(long timeRawMs) {
    Calendar cal = new GregorianCalendar();
    cal.setTimeInMillis(timeRawMs);
    long corr = TimeZone.getDefault().inDaylightTime(cal.getTime()) ? Constants.CORRECTION_BG_SUMMER
        : Constants.CORRECTION_BG_WINTER;
    cal.setTimeInMillis(timeRawMs + corr);
    return cal;
  }

  private void process(JSONObject data) throws Exception {
    String fileName = file.getName();
    String garminName = fileName;
    int ind = garminName.indexOf(Constants.FILE_SUFF);
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
      
      double timeSplitCurrent = 0.0;
      double distSplitCurrent = 0.0;

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
          data.put("date", TimeUtils.formatDate(corrected, false));
          data.put("year", corrected.get(Calendar.YEAR));
          data.put("month", corrected.get(Calendar.MONTH));
          data.put("day", corrected.get(Calendar.DAY_OF_MONTH));
        }
        if (i > 0) {
          double tempDist = TimeUtils.distance(prev[0], lat, prev[1], lon);
          currentDist += tempDist;
          distSplitCurrent += tempDist;
          currentDistSplits += tempDist;
          double timeDiff = (cal.getTimeInMillis() - lastTime) / 1000.0;
          currentTime += timeDiff;
          timeTotal += timeDiff;
          timeSplitCurrent += timeDiff;
          currentTimeSplits += timeDiff;
          currentEle += ele - prev[2];
          currentEleSplits += ele - prev[2];
          if (currentDist / currentTime < 0.5) {
            timeRest += timeDiff;
          }
          boolean lastOne = i == list.getLength() - 1;
          if (timeSplitCurrent >= SPLIT_DISTANCE_UNIT) {
            double mult = SPLIT_DISTANCE_UNIT / timeSplitCurrent;
            splitDists.add(distSplitCurrent * mult);
            distSplitCurrent -= distSplitCurrent * mult;
            timeSplitCurrent -= SPLIT_DISTANCE_UNIT;
          }
          if (lastOne && distSplitCurrent > 0) {
            splitDists.add(distSplitCurrent);
          }
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
        prev = new double[] {
            lat, lon, ele
        };
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
      data.put("type", Constants.RUNNING);
      String distKm = String.format("%.3f", (distTotal / 1000.0));
      data.put("dist", distKm);
      data.put("distRaw", distTotal / 1000.0);
      data.put("distRunning", String.format("%.3f", (distRunning / 1000.0)));
      data.put("distRunningRaw", distRunning / 1000.0);
      data.put("timeRunning", TimeUtils.formatTime((long) timeRunning));
      data.put("timeTotal", TimeUtils.formatTime((long) timeTotal));
      data.put("timeTotalRaw", timeTotal);
      data.put("timeRest", TimeUtils.formatTime((long) timeRest));
      data.put("avgSpeed", String.format("%.3f", (distTotal / timeTotal) / COEF));
      data.put("avgSpeedRaw", (distTotal / timeTotal) / COEF);
      data.put("avgPace", TimeUtils.speedToPace((distTotal / timeTotal) / COEF));
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

  public static JSONObject parse(File file) throws Exception {
    JSONObject data = new JSONObject();
    parse(file, DEFAULT_MIN_SPEED, DEFAULT_INTERVAL, DEFAULT_SPLIT_DISTANCE, data);
    return data;
  }

  private static void parse(File file, double minSpeed, double intR, double splitS, JSONObject data) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Input file not valid");
    }
    TrackParser cd = new TrackParser(file, minSpeed, intR, splitS * 1000.0);
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
      sp.put("time", TimeUtils.formatTime((long) cd.histTime[i]));
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
      String splitTime = TimeUtils.formatTime((long) cd.splitTimes.get(i).doubleValue(), false);
      sp.put("time", splitTime);
      sp.put("timeRaw", Math.round(cd.splitTimes.get(i).doubleValue()));
      timeTotalRaw += cd.splitTimes.get(i).doubleValue();
      sp.put("timeTotalRaw", Math.round(timeTotalRaw));
      sp.put("timeTotal", TimeUtils.formatTime(Math.round(timeTotalRaw), true));
      double splitPace = (cd.splitTimes.get(i).doubleValue() / 60.0) / currentLen;
      sp.put("pace", TimeUtils.formatPace(splitPace));
      sp.put("paceRaw", TimeUtils.formatPaceRaw(splitPace));
      sp.put("speed", String.format("%.3f", 60.0 / splitPace));
      sp.put("accumSpeed", String.format("%.3f", tot / (timeTotalRaw / 3600.0)));
      double ele = cd.splitEle.get(i);
      sp.put("ele", (long) ele);
      sp.put("eleD", ele);
      arrSplits.put(sp);
    }
    data.put("splits", arrSplits);
    double ttRaw = data.getDouble("timeTotalRaw"); // seconds
    int skip = SPLIT_DISTANCE_SKIP_MIN;
    if (ttRaw >= 3600 && ttRaw < 5400) {
      skip = SPLIT_DISTANCE_SKIP_SMALL;
    } else if (ttRaw >= 5400 & ttRaw <= 7200) {
      skip = SPLIT_DISTANCE_SKIP_MEDIUM;
    } else if (ttRaw > 7200) {
      skip = SPLIT_DISTANCE_SKIP_BIG;
    }
    JSONArray distByInterval = new JSONArray();
    JSONArray distByIntervalLabels = new JSONArray();
    int len = cd.splitDists.size();
    int minutes = 0;
    for (int i = 0; i < len; i += skip) {
      double accDist = cd.splitDists.get(i);
      minutes += 5;
      for (int j = i + 1; j < Math.min(len, i + skip); ++j) {
        accDist += cd.splitDists.get(j);
        minutes += 5;
      }
      distByInterval.put(accDist / 1000.0);
      String label = null;
      if (i + skip < len) {
        label = TimeUtils.formatNoSeconds(minutes * 60);
      } else {
        label = TimeUtils.formatNoSeconds((int) ttRaw);
      }
      distByIntervalLabels.put(label);
    }
    data.put("distByInterval", distByInterval);
    data.put("distByIntervalLabels", distByIntervalLabels);
  }

}
