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
  private File lastModFile;
  private Map<String, String> nameMappings = new HashMap<String, String>();
  private Map<String, String> typeMappings = new HashMap<String, String>();
  private Map<String, Long> lastMod = new HashMap<String, Long> ();
  
  Storage(File base) {
    storageBase = new File(base, "storage");
    storageBase.mkdir();
    mappingsFile = new File(storageBase, "mappings");
    lastModFile = new File(storageBase, "lastMod");
    loadMappings();
    loadMod();
  }
  
  @SuppressWarnings("unchecked")
	private void loadMappings() {
    nameMappings = new HashMap<String, String>();
    if (!mappingsFile.isFile()) {
      return;
    }
    InputStream is = null;
    ObjectInputStream ois = null;
    try {
      is = new FileInputStream(mappingsFile);
      ois = new ObjectInputStream(is);
      nameMappings = (Map<String, String>) ois.readObject();
      typeMappings = (Map<String, String>) ois.readObject();
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
  
  @SuppressWarnings("unchecked")
  private void loadMod() {
    if (!lastModFile.isFile()) {
      return;
    }
    InputStream is = null;
    ObjectInputStream ois = null;
    try {
      is = new FileInputStream(lastModFile);
      ois = new ObjectInputStream(is);
      lastMod = (Map<String, Long>) ois.readObject();
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
  
  synchronized void mapName(String fileName, String activityName) {
    nameMappings.put(fileName, activityName);
    saveMappings();
  }
  
  synchronized String getName(String fileName) {
    return nameMappings.get(fileName);
  }
  
  synchronized void mapType(String fileName, String activityType) {
    typeMappings.put(fileName, activityType);
    saveMappings();
  }
  
  synchronized String getType(String fileName) {
    return typeMappings.get(fileName);
  }
  
  private void saveMappings() {
    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(mappingsFile));
      oos.writeObject(nameMappings);
      oos.writeObject(typeMappings);
      oos.flush();
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
  
  synchronized void setLastMod(String filename, long mod) {
    lastMod.put(filename, mod);
    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(lastModFile));
      oos.writeObject(lastMod);
      oos.flush();
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
  
  synchronized long getLastMod(String filename) {
    Long val = lastMod.get(filename);
    return (val != null ? val.longValue() : -1);
  }

}
