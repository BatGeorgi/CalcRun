package xrun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Storage {
  
  private File storageBase;
  
  private File mappingsFile;
  private Map<String, String> mappings = new HashMap<String, String>();
  
  Storage(File base) {
    storageBase = new File(base, "storage");
    storageBase.mkdir();
    mappingsFile = new File(storageBase, "mappings");
    loadMappings();
  }
  
  private void loadMappings() {
    mappings = new HashMap<String, String>();
    if (!mappingsFile.isFile()) {
      return;
    }
    InputStream is = null;
    ObjectInputStream ois = null;
    try {
      is = new FileInputStream(mappingsFile);
      ois = new ObjectInputStream(is);
      mappings = (Map<String, String>) ois.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (ois != null) {
          ois.close();
        } else if (is != null) {
          is.close();
        }
      } catch (IOException ignore) {
      }
    }
  }
  
  synchronized void map(String fileName, String activityName) {
    mappings.put(fileName, activityName);
    saveMappings();
  }
  
  synchronized void unmap(String fileName)  {
    map(fileName, null);
  }
  
  synchronized String get(String fileName) {
    return mappings.get(fileName);
  }
  
  private void saveMappings() {
    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(mappingsFile));
      oos.writeObject(mappings);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (oos != null) {
          oos.close();
        }
      } catch (IOException ignore) {
      }
    }
  }

}
