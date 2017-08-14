package xrun;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ImportExportUtils implements Runnable {
  
  private static final int DEFAULT_PORT = 7150;
  private int port;
  
  ImportExportUtils(int port) {
    this.port = port;
  }

  public static void main(String[] args) {
    int port = DEFAULT_PORT;
    if (args.length > 0) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (Exception ignore) {
        // silent catch
      }
    }
    Thread t = new Thread(new ImportExportUtils(port), "Import export util");
    t.start();
    try {
      t.join();
    } catch (InterruptedException ignore) {
      // silent catch
    }
  }

  public void run() {
    ServerSocket ss = null;
    try {
      ss = new ServerSocket(port);
      ss.setSoTimeout(0);
    } catch (Exception e) {
      System.out.println("Error opening server socket on port " + port);
      return;
    }
    try {
      while (true) {
        Socket client = ss.accept();
      }
    } catch (IOException ioe) {
      
    }
  }
  
}

class ImportExportClient implements Runnable {
  
  private Socket client;
  
  ImportExportClient (Socket client) {
    this.client = client;
  }

  public void run() {
    // TODO Auto-generated method stub
    
  }
  
}
