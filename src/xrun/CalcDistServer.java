package xrun;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
		resourceHandler.setWelcomeFiles(new String[] { "runcalc.html" });
		resourceHandler.setResourceBase("www");
		final Server server = new Server(port);
		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { resourceHandler, new CalcDistHandler(tracksBase) });
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
	private Map<String, JSONObject> cache = new HashMap<String, JSONObject>();
	private String allActivitiesStr;

	public CalcDistHandler(File tracksBase) throws IOException {
		rcUtils = new RunCalcUtils(tracksBase);
    scan();
    System.out.println("Initial scan finished!");
	}
	
	private void scan() {
	  allActivitiesStr = "{}";
	  cache.clear();
	  JSONObject activities = rcUtils.retrieveAllActivities();
    JSONArray data = activities.getJSONArray("activities");
    for (int i = 0; i < data.length(); ++i) {
      JSONObject item = data.getJSONObject(i);
      cache.put(item.getString("genby"), item);
    }
    allActivitiesStr = activities.toString();
	}
	
	private boolean matchExact(Calendar matcher, JSONObject activity) {
    int year = activity.getInt("year");
    int month = activity.getInt("month");
    return matcher.get(Calendar.YEAR) == year && matcher.get(Calendar.MONTH) == month;
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
	
	private JSONObject filter(boolean run, boolean trail, boolean hike, boolean walk, boolean other, int records,
	    Calendar startDate, List<Calendar> matchers) {
	  JSONObject result = new JSONObject();
	  JSONArray activities = new JSONArray();
	  List<JSONObject> matched = new ArrayList<JSONObject>();
	  for (JSONObject activity : cache.values()) {
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
	    if (matchers != null) {
	      for (Calendar matcher : matchers) {
	        if (matchExact(matcher, activity)) {
	          matched.add(activity);
	          break;
	        }
	      }
	    } else if (startDate != null) {
	      if (matchGreater(startDate, activity)) {
	        matched.add(activity);
	      }
	    } else {
	      matched.add(activity);
	    }
	  }
    Collections.sort(matched, new RunDateComparator());
    for (int i = 0; i < Math.min(records, matched.size()); ++i) {
      activities.put(matched.get(i));
    }
    result.put("activities", activities);
    return result;
	}

	public synchronized void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		if (!"POST".equals(baseRequest.getMethod())) {
			return;
		}
		if ("/getInitInfo".equalsIgnoreCase(target)) {
		  JSONObject result = new JSONObject();
		  Map<Integer, List<Integer>> initInfo = new HashMap<Integer, List<Integer>>();
		  for (JSONObject activity : cache.values()) {
		    int year = activity.getInt("year");
		    int month = activity.getInt("month");
		    List<Integer> cr = initInfo.get(year);
		    if (cr == null) {
		      cr = new LinkedList<Integer>();
		      initInfo.put(year, cr);
		    }
		    if (!cr.contains(month)) {
		      cr.add(month);
		    }
		  }
		  List<Integer> sortedKeys = new ArrayList<Integer> (initInfo.keySet());
		  Collections.sort(sortedKeys);
		  for (int i = sortedKeys.size() - 1; i >= 0; --i) {
		    Integer key = sortedKeys.get(i);
		    List<Integer> val = initInfo.get(key);
		    Collections.sort(val);
		    result.put(key.toString(), val);
		  }
		  response.setContentType("application/json");
		  response.getWriter().println(result.toString());
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
		  List<Calendar> matchers = null;
		  int dateOpt = -1;
		  String opts = baseRequest.getHeader("dateOpt");
		  try {
		    if (opts != null) {
		      dateOpt = Integer.parseInt(opts);
		    }
		  } catch (NumberFormatException ignore) {
        // silent catch
      }
		  if (dateOpt < 0 || dateOpt > 4) {
		    String ymt = baseRequest.getHeader("ymt");
		    StringTokenizer st = new StringTokenizer(ymt, ",", false);
		    matchers = new LinkedList<Calendar>();
		    while (st.hasMoreTokens()) {
		      String el = st.nextToken();
		      StringTokenizer st2 = new StringTokenizer(el, ";", false);
		      try {
		        int yr = Integer.parseInt(st2.nextToken());
		        int mt = Integer.parseInt(st2.nextToken());
		        if (mt >= 0 && mt < 12) {
		          Calendar cal = new GregorianCalendar();
		          cal.set(Calendar.YEAR, yr);
		          cal.set(Calendar.MONTH, mt);
		          matchers.add(cal);
		        } else {
		          for (mt = 0; mt < 12; ++mt) {
		            Calendar cal = new GregorianCalendar();
		            cal.set(Calendar.YEAR, yr);
	              cal.set(Calendar.MONTH, mt);
	              matchers.add(cal);
		          }
		        }
		      } catch (Exception ignore) {
		        // silent
		      }
		    }
		  } else {
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
		    }
		  }
		  String result = filter(run, trail, hike, walk, other, records, startDate, matchers).toString();
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
		if ("/loadActivities".equalsIgnoreCase(target)) {
			response.setContentType("application/json");
			response.getWriter().println(allActivitiesStr);
			response.getWriter().flush();
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
		if ("/compare".equalsIgnoreCase(target)) {
			JSONObject item1 = cache.get(baseRequest.getHeader("file1"));
			JSONObject item2 = cache.get(baseRequest.getHeader("file2"));
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
