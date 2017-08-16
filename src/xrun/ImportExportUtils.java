package xrun;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ImportExportUtils implements Runnable {
  
  private static final int DEFAULT_PORT = 7150;
  
  private int port;
  private Object synch = new Object();
  private volatile boolean isTerminated = false;
  private ServerSocket ss;
  private List<ImportExportClient> clientConnections = Collections.synchronizedList(new ArrayList<ImportExportClient>());
  private RunCalcUtils rcUtils;
  
  ImportExportUtils(RunCalcUtils rcUtils, int port) {
    this.rcUtils = rcUtils;
    this.port = (port != -1 ? port : DEFAULT_PORT);
  }

  void activate() {
    new Thread(this, "ImportExport Util").start();
  }
  
  void deactivate() {
    synchronized (synch) {
      isTerminated = true;
      try {
        if (ss != null) {
          ss.close();
        }
      } catch (Exception ignore) {
        // silent catch
      }
    }
  }

  public void run() {
    synchronized (synch) {
      if (isTerminated) {
        return;
      }
      for (ImportExportClient ieClient : clientConnections) {
        ieClient.deactivate();
      }
      clientConnections.clear();
      try {
        ss = new ServerSocket(port);
        ss.setSoTimeout(0);
      } catch (Exception e) {
        System.out.println("Error opening server socket on port " + port);
        return;
      }
    }
    while (true) {
      synchronized (synch) {
        if (isTerminated) {
          break;
        }
      }
      try {
        Socket client = ss.accept();
        synchronized (synch) {
          ImportExportClient ieClient = new ImportExportClient(this, client, clientConnections);
          ieClient.activate();
        }
      } catch (Exception e) {
        if (!isTerminated) {
          System.out.println("Server socket error");
          e.printStackTrace();
        }
      }
    }
  }
  
  List<String> getActivities() {
    return rcUtils.getActivityNames();
  }
  
  File getBaseFolder() {
    return rcUtils.getBaseFolder();
  }
  
  void importActivity(DataInputStream dis) {
    rcUtils.importActivity(dis);
  }
  
}

class ImportExportClient implements Runnable {
  
  private Socket client;
  private List<ImportExportClient> clientConnections;
  private ImportExportUtils utils;
  
  ImportExportClient (ImportExportUtils utils, Socket client, List<ImportExportClient> clientConnections) {
    this.utils = utils;
    this.client = client;
    this.clientConnections = clientConnections;
    clientConnections.add(this);
  }
  
  synchronized void activate() {
    new Thread(this, "ImportExport client connection " + client.getInetAddress()).start();
  }
  
  synchronized void deactivate() {
    clientConnections.remove(this);
  }

  public void run() {
    try {
      DataInputStream dis = new DataInputStream(client.getInputStream());
      DataOutputStream dos = new DataOutputStream(client.getOutputStream());
      if (!CalcDistServer.isAuthorized(dis.readUTF())) {
        dos.writeInt(0);
        dos.flush();
        return;
      }
      dos.writeInt(1);
      dos.flush();
      int option = dis.readInt();
      if (option == 1) { // import
        List<String> activities = utils.getActivities();
        dos.writeInt(activities.size());
        for (String activity : activities) {
          dos.writeUTF(activity);
        }
        dos.flush();
        int len = dis.readInt();
        for (int i = 0; i < len; ++i) {
          utils.importActivity(dis);
        }
      } else { // 2 - export
        exportAll(utils.getBaseFolder(), dos);
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    synchronized (this) {
      clientConnections.remove(this);
    }
  }
  
  private void exportAll(File base, OutputStream os) {
    ZipOutputStream zipOut = null;
    try {
      zipOut = new ZipOutputStream(os);
      export(base, zipOut, "");
      zipOut.flush();
    } catch (IOException ioe) {
      System.out.println("Error writing zip backup");
      ioe.printStackTrace();
    }
  }
  
  private void export(File current, ZipOutputStream zipOut, String currentPath) throws IOException {
    if (current.isDirectory()) {
      String[] children = current.list();
      if (children == null) {
        return;
      }
      for (String child : children) {
        export(new File(current, child), zipOut, (currentPath.length() > 0 ? currentPath + '/' : "") + child);
      }
    } else if (current.isFile()) {
      zipOut.putNextEntry(new ZipEntry(currentPath));
      byte[] buff = new byte[8192];
      int rd = 0;
      InputStream is = null;
      try {
        is = new FileInputStream(current);
        while ((rd = is.read(buff)) != -1) {
          zipOut.write(buff, 0, rd);
        }
        zipOut.flush();
        zipOut.closeEntry();
      } finally {
        try {
          if (is != null) {
            is.close();
          }
        } catch (IOException ignore) {
          // silent catch
        }
      }
    }
  }
  
}
