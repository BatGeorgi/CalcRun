package xrun;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
	
	private static final byte[] CODE = new byte[] {-34,
		120, 57, -104, 105, 87, 123, 12, -88, -80, -97, 16, 36, 125, 101, 32, 112, -97, 101, -106
	};
	
	static boolean isAuthorized(String password) {
		if (password == null) {
			return false;
		}
		MessageDigest md = null;
    try {
        md = MessageDigest.getInstance("SHA-1");
    } catch(NoSuchAlgorithmException e) {
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
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirAllowed(false);
    resourceHandler.setDirectoriesListed(false);
    resourceHandler.setWelcomeFiles(new String[] {"runcalc.html"});
    resourceHandler.setResourceBase("www");
    final Server server = new Server(port);
    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[] {resourceHandler, new CalcDistHandler(tracksBase)});
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

	public CalcDistHandler(File tracksBase) throws IOException {
		rcUtils = new RunCalcUtils(tracksBase);
	}

  public void handle(String target, Request baseRequest,
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    if (!"POST".equals(baseRequest.getMethod())) {
      return;
    }
    if ("/rescanActivities".equalsIgnoreCase(target)) {
      rcUtils.rescan();
    }
    if ("/loadActivities".equalsIgnoreCase(target) || "/rescanActivities".equalsIgnoreCase(target)) {
      response.setContentType("application/json");
      JSONObject activities = rcUtils.retrieveAllActivities();
      JSONArray data = activities.getJSONArray("activities");
      for (int i = 0; i < data.length(); ++i) {
      	JSONObject item = data.getJSONObject(i);
      	cache.put(item.getString("genby"), item);
      }
      response.getWriter().println(activities.toString());
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
      if (fileName != null && fileName.length() > 0 && name != null && name.length() > 0 && type != null && type.length() > 0) {
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
