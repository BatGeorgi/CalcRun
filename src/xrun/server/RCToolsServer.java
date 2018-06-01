package xrun.server;

import java.io.File;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

public class RCToolsServer {

  // args[0] - port, args[1] - tracks base, args[2] - client secret path
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
    List<String> allowedRefs = null;
    if (args.length > 3) {
    	allowedRefs = new LinkedList<String>();
    	StringTokenizer st = new StringTokenizer(args[3], ",", false);
    	while(st.hasMoreTokens()) {
    		allowedRefs.add(st.nextToken());
    	}
    }
    System.out.println("Current time is " + new GregorianCalendar(TimeZone.getDefault()).getTime());
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirAllowed(false);
    resourceHandler.setDirectoriesListed(false);
    resourceHandler.setWelcomeFiles(new String[] {"runcalc"});
    resourceHandler.setResourceBase("www");
    final Server server = new Server(port);
    HandlerList handlers = new HandlerList();
    final RequestHandler cdHandler = new RequestHandler(tracksBase, clientSecret, new File(resourceHandler.getBaseResource().getFile(), "activity.html"),
    		new File(resourceHandler.getBaseResource().getFile(), "comparison.html"), allowedRefs);
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

