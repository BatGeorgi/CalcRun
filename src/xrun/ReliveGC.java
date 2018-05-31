package xrun;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class ReliveGC extends TimerTask {

  private RunCalcUtils     rcUtils;
  private DBStorage    sqLite;
  private Timer            timer          = new Timer();

  private static final int CLEANUP_PERIOD = 24 * 3600 * 1000; // one day

  ReliveGC(RunCalcUtils rcUtils, DBStorage sqLite) {
    this.rcUtils = rcUtils;
    this.sqLite = sqLite;
    run();
    timer.scheduleAtFixedRate(this, CLEANUP_PERIOD, CLEANUP_PERIOD);
  }

  public void run() {
    GregorianCalendar cal = new GregorianCalendar(TimeZone.getDefault());
    cal.add(Calendar.MONTH, -4);
    sqLite.cleanupReliveCCBefore(cal);
    rcUtils.resetHandlerCache();
  }

  void dispose() {
    if (timer != null) {
      timer.cancel();
    }
  }

}
