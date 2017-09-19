package xrun;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
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

  // 0 - port, 1 - tracks base, 2 - client secret path
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
    File clientSecret = null;
    if (args.length > 2) {
      clientSecret = new File(args[2]);
      if (!clientSecret.isFile()) {
        throw new IllegalArgumentException("Client secret file " + args[2] + " does not exist");
      }
    }
    System.out.println("Current time is " + new GregorianCalendar(TimeZone.getDefault()).getTime());
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirAllowed(false);
    resourceHandler.setDirectoriesListed(false);
    resourceHandler.setWelcomeFiles(new String[] { "runcalc" });
    resourceHandler.setResourceBase("www");
    final Server server = new Server(port);
    HandlerList handlers = new HandlerList();
    final CalcDistHandler cdHandler = new CalcDistHandler(tracksBase, clientSecret);
    handlers.setHandlers(new Handler[] { new MultipartConfigInjectionHandler(), resourceHandler, cdHandler });
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
  private TokenHandler tokenHandler;

  public CalcDistHandler(File tracksBase, File clientSecret) throws IOException {
    rcUtils = new RunCalcUtils(tracksBase, clientSecret);
    tokenHandler = new TokenHandler();
    System.out.println("Initialize finished!");
  }
  
  void dispose() {
    if (rcUtils != null) {
      rcUtils.dispose();
    }
    if (tokenHandler != null) {
    	tokenHandler.dispose();
    }
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
  
  private JSONObject getBest() {
    JSONObject result = new JSONObject();
    result.put("longest", rcUtils.getBest("distRaw", "km"));
    result.put("fastest", rcUtils.getBest("avgSpeedRaw", "km/h"));
    result.put("maxAsc", rcUtils.getBest("eleTotalPos", "m"));
    result.put("1K", rcUtils.getBest(0.99, 1.1));
    result.put("2K5", rcUtils.getBest(2.49, 2.55));
    result.put("5K", rcUtils.getBest(4.99, 5.2));
    result.put("10K", rcUtils.getBest(9.98, 10.3));
    result.put("21K", rcUtils.getBest(21, 21.8));
    result.put("30K", rcUtils.getBest(30, 31));
    result.put("42K", rcUtils.getBest(42, 43.5));
    return result;
  }
  
  private JSONObject filter(String nameFilter, boolean run, boolean trail, boolean hike, boolean walk, boolean other, int records,
      Calendar startDate, Calendar endDate, int minDistance, int maxDistance) {
    JSONObject result = new JSONObject();
    JSONArray activities = new JSONArray();
    List<JSONObject> matched = rcUtils.filter(run, trail, hike, walk, other, startDate, endDate,
        minDistance, maxDistance, records);
    Iterator<JSONObject> it = matched.iterator();
    JSONObject totals = null;
    if (it.hasNext()) {
      totals = it.next();
    }
    while(it.hasNext()) {
      JSONObject cr = it.next();
      if (!matchName(nameFilter, cr.getString("name"))) {
        it.remove();
        totals.put("totalDistance", totals.getDouble("totalDistance") - cr.getDouble("distRaw"));
        totals.put("totalTime", totals.getLong("totalTime") - cr.getLong("timeTotalRaw"));
        totals.put("elePos", totals.getLong("elePos") - cr.getLong("eleTotalPos"));
        totals.put("eleNeg", totals.getLong("eleNeg") - cr.getLong("eleTotalNeg"));
        totals.put("totalRunDist", totals.getDouble("totalRunDist") - cr.getDouble("distRunningRaw"));
      }
    }
    if (totals != null) {
    	totals.put("avgSpeed", String.format("%.3f", totals.getDouble("totalDistance") / (totals.getLong("totalTime") / 3600.0)));
    	totals.put("totalTime", CalcDist.formatTime(totals.getLong("totalTime"), true));
    }
    for (int i = 1; i < matched.size(); ++i) {
      activities.put(matched.get(i));
    }
    result.put("activities", activities);
    if (activities.length() > 0) {
      for (String key : totals.keySet()) {
      	Object value = totals.get(key);
      	if (value instanceof Double) {
      		result.put(key, String.format("%.3f", (Double) value));
      	} else {
      		result.put(key, totals.get(key));
      	}
      }
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
  
	private boolean handleFileUpload(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		ServletFileUpload upload = new ServletFileUpload();
		try {
			FileItemIterator iter = upload.getItemIterator(request);
			while (iter.hasNext()) {
				FileItemStream item = iter.next();
				if (!item.isFormField()) {
					return rcUtils.addActivity(item.getName(), item.openStream());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

  public synchronized void handle(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
  	System.out.println(target + " " + baseRequest.getMethod());
    if (!"POST".equals(baseRequest.getMethod()) || target.length() < 2) {
      return;
    }
    if (target.startsWith("/upload") && target.length() > 7) {
    	String token = target.substring(7);
    	if (tokenHandler.isAuthorized(token)) {
    		tokenHandler.removeToken(token);
    		boolean result = handleFileUpload(baseRequest, request, response);
    		response.setContentType("text/html");
    		if (result) {
    			response.getWriter().println("<h2>Upload finished!</h2>");
    			response.getWriter().println("<a href=\"runcalc\"><h2>Go to main page</h2></a>");
    			response.getWriter().flush();
    			response.setStatus(HttpServletResponse.SC_OK);
    		} else {
    			response.getWriter().println("<h2>Upload failed!</h2>");
    			response.getWriter().println("<a href=\"runcalc\"><h2>Go to main page</h2></a>");
    			response.getWriter().flush();
    			response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
    		}
    	} else { // just in case
    		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    		response.setContentType("text/html");
    		response.getWriter().println("Not authorized!");
        response.getWriter().flush();
    	}
    	baseRequest.setHandled(true);
    }
    if ("/authorize".equalsIgnoreCase(target)) {
    	response.setContentType("application/json");
    	if (!CalcDistServer.isAuthorized(baseRequest.getHeader("Password"))) {
    		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    	} else {
    		JSONObject json = new JSONObject();
    		json.put("token", tokenHandler.getToken());
    		response.getWriter().println(json.toString());
    		response.getWriter().flush();
    		response.setStatus(HttpServletResponse.SC_OK);
    	}
      baseRequest.setHandled(true);
    }
    if ("/best".equalsIgnoreCase(target)) {
      response.setContentType("application/json");
      response.getWriter().println(getBest());
      response.getWriter().flush();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
    if ("/bestSplits".equalsIgnoreCase(target)) {
      response.setContentType("application/json");
      response.getWriter().println(rcUtils.getBestSplits());
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
      rcUtils.rescan();
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
        response.setStatus(HttpServletResponse.SC_OK);
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
      baseRequest.setHandled(true);
    }
    if ("/deleteActivity".equalsIgnoreCase(target)) {
      String fileName = baseRequest.getHeader("File");
      String pass = baseRequest.getHeader("Password");
      if (!CalcDistServer.isAuthorized(pass)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        baseRequest.setHandled(true);
        return;
      }
      if (fileName != null && fileName.length() > 0) {
        rcUtils.deleteActivity(fileName);
        response.setStatus(HttpServletResponse.SC_OK);
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
      baseRequest.setHandled(true);
    }
    if ("/compare".equalsIgnoreCase(target)) {
      JSONObject item1 = rcUtils.getActivity(baseRequest.getHeader("file1"));
      JSONObject item2 = rcUtils.getActivity(baseRequest.getHeader("file2"));
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
