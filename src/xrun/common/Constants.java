package xrun.common;

public class Constants {

  public static final String   RUNNING              = "Running";
  public static final String   TRAIL                = "Trail";
  public static final String   UPHILL               = "Uphill";
  public static final String   HIKING               = "Hiking";
  public static final String   WALKING              = "Walking";
  public static final String   OTHER                = "Other";

  public static final String   FILE_SUFF            = "_-REV-_";

  public static final String[] MONTHS               = new String[] {
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  public static final String[] DAYS                 = new String[] {
      "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
  };

  public static final long     CORRECTION_BG_WINTER = 2 * 3600 * 1000; // 2 hours
  public static final long     CORRECTION_BG_SUMMER = 3 * 3600 * 1000; // 3 hours

  public static final String   EXTERNAL_DASHBOARD   = "External";
  public static final String   MAIN_DASHBOARD       = "Main";

}
