package xrun;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
	
	private static final byte[] CODE = new byte[] {-55, -85, 122, -120, -106, -44, 81, -78, 90, 79, -73, -54, 34, -42, -110, -67, 6, 56, -102, -7
	};
	
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
	
	private void handleFileUpload(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		ServletFileUpload upload = new ServletFileUpload();
		try {
			FileItemIterator iter = upload.getItemIterator(request);
			while (iter.hasNext()) {
				FileItemStream item = iter.next();
				if (!item.isFormField()) {
					JSONObject status = rcUtils.addActivity(item.openStream(), item.getName());
					response.setContentType("application/json");
					PrintWriter writer = response.getWriter();
					if (status.opt("error") != null) {
						writer.println(status.get("error"));
						writer.flush();
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					} else {
						JSONObject xrun = status.getJSONObject("item");
						cache.put(xrun.getString("genby"), xrun);
						response.setStatus(HttpServletResponse.SC_OK);
					}
					baseRequest.setHandled(true);
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

  public synchronized void handle(String target, Request baseRequest,
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
  	if (!"POST".equals(baseRequest.getMethod())) {
      return;
    }
		if ("/addActivity".equalsIgnoreCase(target) && ServletFileUpload.isMultipartContent(request)) {
		  String pass = baseRequest.getHeader("Password");
		  if (!CalcDistServer.isAuthorized(pass)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        baseRequest.setHandled(true);
        return;
      }
			handleFileUpload(baseRequest, request, response);
			return;
		}
    if ("/rescanActivities".equalsIgnoreCase(target)) {
      String pass = baseRequest.getHeader("Password");
      if (!CalcDistServer.isAuthorized(pass)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        baseRequest.setHandled(true);
        return;
      }
      rcUtils.rescan();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
    if ("/loadActivities".equalsIgnoreCase(target)) {
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
