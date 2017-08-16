package xrun;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.json.JSONArray;
import org.json.JSONObject;

public class CalcDistServer {

  private static final byte[] CODE = new byte[] { -55, -85, 122, -120, -106, -44, 81, -78, 90, 79, -73, -54, 34, -42,
      -110, -67, 6, 56, -102, -7 };

  static boolean isAuthorized(String password) {
    if (password == null) {
      return false;
    }
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      return false;
    }
    byte[] arr = md.digest(password.getBytes());
    if (arr == null || arr.length != CODE.length) {
      return false;
    }
    for (int i = 0; i < arr.length; ++i) {
      if (arr[i] != CODE[i]) {
        return false;
      }
    }
    return true;
  }

  // 0 - port, 1 - tracks base
  public static void main(String[] args) throws Exception {
    if (args == null || args.length < 2) {
      throw new IllegalArgumentException("Not enough args");
    }
    int port = Integer.parseInt(args[0]);
    File tracksBase = new File(args[1]);
    if (!tracksBase.exists()) {
      tracksBase.mkdirs();
    }
    if (!tracksBase.isDirectory()) {
      throw new IllegalArgumentException("Not a folder " + tracksBase);
    }
    System.out.println("Current time is " + new GregorianCalendar(TimeZone.getDefault()).getTime());
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirAllowed(false);
    resourceHandler.setDirectoriesListed(false);
    resourceHandler.setWelcomeFiles(new String[] { "runcalc" });
    resourceHandler.setResourceBase("www");
    final Server server = new Server(port);
    HandlerList handlers = new HandlerList();
    final CalcDistHandler cdHandler = new CalcDistHandler(tracksBase);
    handlers.setHandlers(new Handler[] { resourceHandler, cdHandler });
    server.setHandler(handlers);
    server.start();
    final Scanner scanner = new Scanner(System.in);
    new Thread(new Runnable() {

      public void run() {
        while (true) {
          String line = scanner.nextLine();
          if ("q".equalsIgnoreCase(line) || "e".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)
              || "quit".equalsIgnoreCase(line)) {
            try {
              server.stop();
            } catch (Exception e) {
              e.printStackTrace();
            }
            scanner.close();
            cdHandler.dispose();
            break;
          }
        }
      }
    }, "XRun server watchdog").start();
    server.join();
  }

}

class CalcDistHandler extends AbstractHandler {

  private RunCalcUtils rcUtils;
  private ImportExportUtils ieUtils;

  public CalcDistHandler(File tracksBase) throws IOException {
    rcUtils = new RunCalcUtils(tracksBase);
    ieUtils = new ImportExportUtils(rcUtils, -1);
    scan();
    System.out.println("Initial scan finished!");
    ieUtils.activate();
  }
  
  void dispose() {
    if (ieUtils != null) {
      ieUtils.deactivate();
    }
  }
  
  private void scan() {
    rcUtils.cache.clear();
    JSONObject activities = rcUtils.retrieveAllActivities();
    JSONArray data = activities.getJSONArray("activities");
    for (int i = 0; i < data.length(); ++i) {
      JSONObject item = data.getJSONObject(i);
      rcUtils.cache.put(item.getString("genby"), item);
    }
  }
  
  private boolean matchGreater(Calendar matcher, JSONObject activity) {
    int year = activity.getInt("year");
    int month = activity.getInt("month");
    int day = activity.getInt("day");
    int myr = matcher.get(Calendar.YEAR);
    int mm = matcher.get(Calendar.MONTH);
    int md = matcher.get(Calendar.DAY_OF_MONTH);
    if (myr < year) {
      return true;
    } else if (myr == year) {
      if (mm < month) {
        return true;
      } else if (mm == month) {
        return md <= day;
      }
    }
    return false;
  }
  
  private boolean matchLess(Calendar matcher, JSONObject activity) {
    int year = activity.getInt("year");
    int month = activity.getInt("month");
    int day = activity.getInt("day");
    int myr = matcher.get(Calendar.YEAR);
    int mm = matcher.get(Calendar.MONTH);
    int md = matcher.get(Calendar.DAY_OF_MONTH);
    if (myr > year) {
      return true;
    } else if (myr == year) {
      if (mm > month) {
        return true;
      } else if (mm == month) {
        return md >= day;
      }
    }
    return false;
  }
  
  private boolean matchName(String namePattern, String name) {
    if (namePattern == null) {
      return true;
    }
    namePattern = namePattern.trim();
    if (namePattern.length() == 0) {
      return true;
    }
    String[] splits = namePattern.split("%7C");
    for (String str : splits) {
      if (name.contains(str)) {
        return true;
      }
    }
    return false;
  }
  
  private void checkAch(JSONObject achObj, JSONObject activity) {
    double currentTime = activity.getDouble("timeTotalRaw");
    double fkt = Double.MAX_VALUE;
    if (achObj.has("raw")) {
      fkt = achObj.optDouble("raw");
    }
    if (currentTime < fkt) {
      achObj.put("raw", currentTime);
      achObj.put("ach", activity.get("timeTotal"));
      achObj.put("when", activity.get("date"));
    }
  }
  
  private JSONObject getBest() {
    JSONObject result = new JSONObject();
    JSONObject longestRun = new JSONObject();
    JSONObject fastestRun = new JSONObject();
    JSONObject maxAscent = new JSONObject();
    JSONObject fastest1K = new JSONObject();
    JSONObject fastest2K5 = new JSONObject();
    JSONObject fastest5K = new JSONObject();
    JSONObject fastest10K = new JSONObject();
    JSONObject fastest21K = new JSONObject();
    JSONObject fastest30K = new JSONObject();
    JSONObject fastest42K = new JSONObject();
    double maxDist = 0.0;
    double maxSpeed = 0.0;
    long maxEle = 0;
    for (JSONObject activity : rcUtils.cache.values()) {
      double dist = Double.parseDouble(activity.getString("dist").replace(',', '.'));
      double speed = Double.parseDouble(activity.getString("avgSpeed").replace(',', '.'));
      long ele = activity.getLong("eleTotalPos");
      if (dist > maxDist) {
        maxDist = dist;
        longestRun.put("ach", dist + " km");
        longestRun.put("when", activity.get("date"));
      }
      if (speed > maxSpeed) {
        maxSpeed = speed;
        fastestRun.put("ach", speed + " km/h");
        fastestRun.put("when", activity.get("date"));
      }
      if (ele > maxEle) {
        maxEle = ele;
        maxAscent.put("ach", ele + " m");
        maxAscent.put("when", activity.get("date"));
      }
      if (dist >= 0.99 && dist < 1.1) {
        checkAch(fastest1K, activity);
      }
      if (dist >= 2.49 && dist < 2.55) {
        checkAch(fastest2K5, activity);
      }
      if (dist >= 4.99 && dist < 5.2) {
        checkAch(fastest5K, activity);
      }
      if (dist >= 9.98 && dist < 10.3) {
        checkAch(fastest10K, activity);
      }
      if (dist >= 21 && dist < 21.8) {
        checkAch(fastest21K, activity);
      }
      if (dist >= 30 && dist < 31) {
        checkAch(fastest30K, activity);
      }
      if (dist >= 42 && dist < 43.5) {
        checkAch(fastest42K, activity);
      }
    }
    result.put("longest", longestRun);
    result.put("fastest", fastestRun);
    result.put("maxAsc", maxAscent);
    result.put("1K", fastest1K);
    result.put("2K5", fastest2K5);
    result.put("5K", fastest5K);
    result.put("10K", fastest10K);
    result.put("21K", fastest21K);
    result.put("30K", fastest30K);
    result.put("42K", fastest42K);
    return result;
  }
  
  private JSONObject filter(String nameFilter, boolean run, boolean trail, boolean hike, boolean walk, boolean other, int records,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    JSONObject result = new JSONObject();
    JSONArray activities = new JSONArray();
    List<JSONObject> matched = new ArrayList<JSONObject>();
    for (JSONObject activity : rcUtils.cache.values()) {
      String type = activity.getString("type");
      if ("Running".equals(type) && !run) {
        continue;
      }
      if ("Trail".equals(type) && !trail) {
        continue;
      }
      if ("Hiking".equals(type) && !hike) {
        continue;
      }
      if ("Walking".equals(type) && !walk) {
        continue;
      }
      if ("Other".equals(type) && !other) {
        continue;
      }
      if (startDate != null && !matchGreater(startDate, activity)) {
        continue;
      }
      if (endDate != null && !matchLess(endDate, activity)) {
        continue;
      }
      if (!matchName(nameFilter, activity.getString("name"))) {
        continue;
      }
      double dist = Double.parseDouble(activity.getString("dist").replace(',', '.'));
      if (dist < minDistance || dist > maxDistance) {
        continue;
      }
      matched.add(activity);
    }
    Collections.sort(matched, new RunDateComparator());
    double totalDistance = 0.0;
    double totalTime = 0.0;
    double totalRunDist = 0.0;
    long elePos = 0;
    long eleNeg = 0;
    for (int i = 0; i < Math.min(records, matched.size()); ++i) {
      JSONObject activity = matched.get(i);
      activities.put(activity);
      totalTime += (double) activity.getLong("timeTotalRaw");
      totalDistance += Double.parseDouble(activity.getString("dist").replace(',', '.'));
      totalRunDist += Double.parseDouble(activity.getString("distRunning").replace(',', '.'));
      elePos += activity.getLong("eleTotalPos");
      eleNeg += activity.getLong("eleTotalNeg");
    }
    result.put("activities", activities);
    if (activities.length() > 0) {
      result.put("totalDistance", String.format("%.3f", totalDistance));
      result.put("totalTime", CalcDist.formatTime((long) totalTime, true));
      result.put("avgSpeed", String.format("%.3f", totalDistance / (totalTime / 3600.0)));
      result.put("elePos", elePos);
      result.put("eleNeg", eleNeg);
      result.put("totalRunDist", String.format("%.3f", totalRunDist));
    }
    return result;
  }
  
  private Calendar parseDate(String dt) {
    if (dt == null) {
      return null;
    }
    StringTokenizer st = new StringTokenizer(dt, "/,;", false);
    if (st.countTokens() != 3) {
      return null;
    }
    Calendar cal = new GregorianCalendar();
    try {
      cal.set(Calendar.MONTH, Integer.parseInt(st.nextToken()) - 1);
      cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(st.nextToken()));
      cal.set(Calendar.YEAR, Integer.parseInt(st.nextToken()));
      return cal;
    } catch (Exception ignore) {
      // silent catch
    }
    return null;
  }

  public synchronized void handle(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
    if (!"POST".equals(baseRequest.getMethod())) {
      return;
    }
    if ("/best".equalsIgnoreCase(target)) {
      response.setContentType("application/json");
      response.getWriter().println(getBest());
      response.getWriter().flush();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
    if ("/fetch".equalsIgnoreCase(target)) {
      boolean run = "true".equals(baseRequest.getHeader("run"));
      boolean trail = "true".equals(baseRequest.getHeader("trail"));
      boolean hike = "true".equals(baseRequest.getHeader("hike"));
      boolean walk = "true".equals(baseRequest.getHeader("walk"));
      boolean other = "true".equals(baseRequest.getHeader("other"));
      int records = Integer.MAX_VALUE;
      String rec = baseRequest.getHeader("records");
      try {
        if (rec != null) {
          records = Integer.parseInt(rec);
        }
      } catch (NumberFormatException ignore) {
        // silent catch
      }
      if (records < 0) {
        records = Integer.MAX_VALUE;
      }
      Calendar startDate = null;
      Calendar endDate = null;
      int dateOpt = -1;
      String opts = baseRequest.getHeader("dateOpt");
      try {
        if (opts != null) {
          dateOpt = Integer.parseInt(opts);
        }
      } catch (NumberFormatException ignore) {
        // silent catch
      }
      startDate = new GregorianCalendar(TimeZone.getDefault());
      int mt = 0;
      switch (dateOpt) {
        case 0: // this month
          startDate.set(Calendar.DAY_OF_MONTH, 1);
          break;
        case 1: // this year
          startDate.set(Calendar.DAY_OF_MONTH, 1);
          startDate.set(Calendar.MONTH, 0);
          break;
        case 2: // last 30
          mt = startDate.get(Calendar.MONTH);
          if (mt > 0) {
            startDate.set(Calendar.MONTH, mt - 1);
          } else {
            startDate.set(Calendar.MONTH, 11);
            startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
          }
          break;
        case 3: // last 3m
          mt = startDate.get(Calendar.MONTH);
          if (mt > 3) {
            startDate.set(Calendar.MONTH, mt - 3);
          } else {
            startDate.set(Calendar.MONTH, mt + 8);
            startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
          }
          break;
        case 4: // last y
          startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
          break;
        case 5: // all
          startDate = null;
          break;
        case 6: // custom
          startDate = parseDate(baseRequest.getHeader("dtStart"));
          endDate = parseDate(baseRequest.getHeader("dtEnd"));
      }
      String smin = baseRequest.getHeader("dmin");
      String smax = baseRequest.getHeader("dmax");
      int dmin = 0;
      int dmax = Integer.MAX_VALUE;
      boolean err = false;
      try {
        if (smin != null && smin.trim().length() > 0) {
          dmin = Integer.parseInt(smin);
        }
      } catch (NumberFormatException nfe) {
        err = true;
      }
      try {
        if (smax != null && smax.trim().length() > 0) {
          dmax = Integer.parseInt(smax);
        }
      } catch (NumberFormatException nfe) {
        err = true;
      }
      if (dmin > dmax || err) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        baseRequest.setHandled(true);
        return;
      }
      String result = filter(baseRequest.getHeader("nameFilter"), run, trail, hike, walk, other,
          records, startDate, endDate, dmin, dmax).toString();
      response.setContentType("application/json");
      response.getWriter().println(result);
      response.getWriter().flush();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
    if ("/rescanActivities".equalsIgnoreCase(target)) {
      String pass = baseRequest.getHeader("Password");
      if (!CalcDistServer.isAuthorized(pass)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        baseRequest.setHandled(true);
        return;
      }
      rcUtils.rescan();
      scan();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
    if ("/editActivity".equalsIgnoreCase(target)) {
      String fileName = baseRequest.getHeader("File");
      String name = baseRequest.getHeader("Name");
      String type = baseRequest.getHeader("Type");
      String pass = baseRequest.getHeader("Password");
      if (!CalcDistServer.isAuthorized(pass)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        baseRequest.setHandled(true);
        return;
      }
      if (fileName != null && fileName.length() > 0 && name != null && name.length() > 0 && type != null
          && type.length() > 0) {
        rcUtils.editActivity(fileName, name, type);
        JSONObject activity = rcUtils.cache.get(fileName);
        if (activity != null) {
          rcUtils.updateActivity(activity, fileName);
        }
        response.setStatus(HttpServletResponse.SC_OK);
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
      baseRequest.setHandled(true);
    }
    if ("/compare".equalsIgnoreCase(target)) {
      JSONObject item1 = rcUtils.cache.get(baseRequest.getHeader("file1"));
      JSONObject item2 = rcUtils.cache.get(baseRequest.getHeader("file2"));
      if (item1 == null || item2 == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      } else {
        response.setContentType("application/json");
        response.getWriter().println(rcUtils.compare(item1, item2));
        response.getWriter().flush();
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
      }
    }
  }
}
