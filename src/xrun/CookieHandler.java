package xrun;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.servlet.http.Cookie;

class CookieHandler extends TimerTask {
  
  private static final String COOKIE_NAME = "xruncalc";
  private static final int MONTH_SEC = 3600 * 24 * 31;
  private static final int CLEANUP_PERIOD = 3600 * 1000; // one hour
  
  private SQLiteManager sqLite;
  private Timer timer = new Timer();
  
  CookieHandler(SQLiteManager sqLite) {
    this.sqLite = sqLite;
    timer.scheduleAtFixedRate(this, CLEANUP_PERIOD, CLEANUP_PERIOD);
  }
  
  void dispose() {
    if (timer != null) {
      timer.cancel();
    }
  }
  
  boolean isAuthorized(Cookie cookie) {
    return sqLite.isValidCookie(cookie.getValue());
  }
  
  Cookie generateCookie() {
    String uid = null;
    Calendar cal = new GregorianCalendar(TimeZone.getDefault());
    cal.add(Calendar.DATE, 31);
    for (int i = 0; i < 1000; ++i) {
      uid = UUID.randomUUID().toString();
      if (sqLite.saveCookie(uid, cal)) {
        break;
      }
      uid = null;
    }
    if (uid == null) {
      return null;
    }
    Cookie cookie = new Cookie(COOKIE_NAME, uid);
    cookie.setMaxAge(MONTH_SEC);
    cookie.setPath("/");
    return cookie;
  }

  public void run() {
    sqLite.checkForExpiredCookies();
  }

}