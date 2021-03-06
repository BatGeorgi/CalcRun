package xrun.storage;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.servlet.http.Cookie;

public class CookieHandler extends TimerTask {

  private static final String COOKIE_NAME    = "xruncalc";
  private static final int    MONTH_SEC      = 3600 * 24 * 31;
  private static final int    CLEANUP_PERIOD = 3600 * 1000;   // one hour

  private DBStorage           storage;
  private Timer               timer          = new Timer();

  public CookieHandler(DBStorage sqLite) {
    this.storage = sqLite;
    timer.scheduleAtFixedRate(this, CLEANUP_PERIOD, CLEANUP_PERIOD);
  }

  public void dispose() {
    if (timer != null) {
      timer.cancel();
    }
  }

  public boolean isAuthorized(Cookie cookie) {
    return storage.isValidCookie(cookie.getValue());
  }

  public void removeCookie(Cookie cookie) {
    if (COOKIE_NAME.equals(cookie.getName())) {
      try {
        storage.deleteCookie(cookie.getValue());
      } catch (SQLException e) {
        System.out.println("DB error deleting cookie");
      }
    }
  }

  public Cookie generateCookie() {
    String uid = null;
    Calendar cal = new GregorianCalendar(TimeZone.getDefault());
    cal.add(Calendar.DATE, 31);
    for (int i = 0; i < 1000; ++i) {
      uid = UUID.randomUUID().toString();
      if (storage.saveCookie(uid, cal)) {
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
    storage.checkForExpiredCookies();
  }

}
