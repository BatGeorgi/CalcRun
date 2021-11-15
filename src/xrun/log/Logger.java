package xrun.log;

public class Logger {
  
  public static void log(String msg) {
    log(msg, null);
  }
  
  public static void log(Throwable t) {
    log(t.getMessage(), t);
  }
  
  public static void log(String msg, Throwable t) {
    System.out.println(msg);
    if (t != null) {
      t.printStackTrace();
    }
  }

}
