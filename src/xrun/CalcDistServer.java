package xrun;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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


public class CalcDistServer {
	
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

	public CalcDistHandler(File tracksBase) throws IOException {
		rcUtils = new RunCalcUtils(tracksBase);
	}

  public void handle(String target, Request baseRequest,
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    if ("POST".equals(baseRequest.getMethod()) && "/loadActivities".equalsIgnoreCase(target)) {
      response.setContentType("application/json");
      response.getWriter().println(rcUtils.retrieveAllActivities().toString());
      response.getWriter().flush();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
  }
}
