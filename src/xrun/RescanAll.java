package xrun;

import java.io.File;

import org.json.JSONObject;

public class RescanAll {
  
  private File base;
  
  RescanAll(File base) {
    this.base = base;
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      throw new IllegalArgumentException("Not enough arguments");
    }
    File base = new File(args[0]);
    if (!base.isDirectory()) {
      throw new IllegalArgumentException(base + " is not a valid folder path");
    }
    new RescanAll(base).rescan();
  }
  
  void rescan() {
    String[] all = base.list();
    for (String fileName : all) {
      if (!fileName.endsWith(".gpx")) {
        continue;
      }
      File targ = new File(base, fileName);
      if (!targ.isFile()) {
        continue;
      }
      try {
        CalcDist.run(targ.getAbsolutePath(), "9", "100", "1", new JSONObject());
      } catch (Exception e) {
        System.out.println("Error processing " + targ);
        e.printStackTrace();
      }
    }
    
  }

}
