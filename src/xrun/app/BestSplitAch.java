package xrun.app;

public class BestSplitAch {

  String id;
  long   time;      // milliseconds
  double startPoint;

  public BestSplitAch(String id, double startPoint) {
    this(id, startPoint, 0);
  }

  public BestSplitAch(String id, double startPoint, long time) {
    this.id = id;
    this.startPoint = startPoint;
    this.time = time;
  }

  public String getId() {
    return id;
  }

  public long getTime() {
    return time;
  }

  public double getStartPoint() {
    return startPoint;
  }

}
