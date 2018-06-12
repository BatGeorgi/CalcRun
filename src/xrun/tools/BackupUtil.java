package xrun.tools;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.json.JSONObject;

import xrun.app.RunCalcApplication;
import xrun.utils.JsonSanitizer;

public class BackupUtil {
  
  private RunCalcApplication runCalcApplication;
  private File backupFile;

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println("DB folder not specified");
      return;
    }
    boolean backup = false;
    if ("backup".equals(args[1])) {
      backup = true;
    } else if ("!restore".equals(args[1])) {
      System.out.println("Invalid action argument " + args[1] + " - must be backup or restore");
    }
    BackupUtil backupUtil = new BackupUtil(args[0]);
    if (backup) {
      backupUtil.backup();
    } else {
      backupUtil.restore();
    }
  }
  
  private BackupUtil(String base) {
    File mainDir = new File(base);
    if (!mainDir.isDirectory()) {
      throw new IllegalArgumentException("Not a folder - " + base);
    }
    backupFile = new File(mainDir, "data.bak");
    runCalcApplication = new RunCalcApplication(null, mainDir, null);
  }
  
  private void backup() throws Exception {
    OutputStream os = null;
    List<JSONObject> activities = runCalcApplication.getAllActivities();
    try {
      os = new FileOutputStream(backupFile);
      ObjectOutputStream oos = new ObjectOutputStream(os);
      oos.writeInt(activities.size());
      for (JSONObject json : activities) {
        oos.writeObject(json.toString());
      }
      oos.flush();
    } finally {
      silentClose(os);
    }
  }
  
  private void restore() throws Exception {
    InputStream is = null;
    try {
      is = new FileInputStream(backupFile);
      ObjectInputStream ois = new ObjectInputStream(is);
      int len = ois.readInt();
      for (int i = 0; i < len; ++i) {
        JSONObject json = new JSONObject(JsonSanitizer.sanitize((String) ois.readObject()));
        runCalcApplication.importActivity(json);
      }
    } finally {
      silentClose(is);
    }
  }
  
  private void silentClose(Closeable cl) {
    try {
      if (cl != null) {
        cl.close();
      }
    } catch (Exception ignore) {
      // silent catch
    }
  }

}
