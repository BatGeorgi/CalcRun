package xrun.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class PerformanceUtil {
  
  /*
    PERFORMANCE SUBSETS:
     elite: below 10:00
     very fast: 10:00 - 12:00
     fast: 12:00 - 14:30
     regular: 14:30 - 17:00
     tourists: over 17:00  
     all: completed race
     
     TARGETS:
      fiction: 11:00
      machine: 11:30
      great: 12:00
      optimistic: 12:30
      realistic: 13:00
      current: 13:40
   */
  
  private static final String RESULTS_DEF = "C:/GBV/Tracks/allparts.txt";
  private static final Double[] SPLITS_DEF = new Double[] {32.0, 59.0, 75.0, 98.4};
  private static final String TIME_MIN_DEF = "11:30:00";
  private static final String TIME_MAX_DEF = "14:00:00";
  private static final String TIME_TARGET_DEF = "13:00:00";
  
  private File results;
  private Double[] splits;
  private long timeMin;
  private long timeMax;
  private long timeTarg;

  public static void main(String[] args) throws Exception {
    populateData();
    /*File results = new File(args.length > 0 ? args[0] : RESULTS_DEF);
    Double[] splits = SPLITS_DEF;
    if (args.length > 1) {
      List<Double> sp = new LinkedList<>();
      String[] tokens = args[1].split("\\s+");
      for (String token : tokens) {
        sp.add(Double.parseDouble(token));
      }
      splits = new Double[sp.size()];
      sp.toArray(splits);
    }
    String timeMin = args.length > 2 ? args[2] : TIME_MIN_DEF;
    String timeMax = args.length > 3 ? args[3] : TIME_MAX_DEF;
    String timeTarg = args.length > 4 ? args[4] : TIME_TARGET_DEF;
    new PerformanceUtil(results, splits, timeMin, timeMax, timeTarg).calc();*/
  }
  
  private static void populateData() throws Exception {
    String[] targets = new String[] {
        "11:00:00",
        "11:30:00",
        "12:00:00",
        "12:30:00",
        "13:00:00",
        "13:40:00",
    };
    for (String target : targets) {
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "00:00:00", "10:00:00", target).calc();
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "10:00:01", "12:00:00", target).calc();
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "12:00:01", "14:30:00", target).calc();
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "14:30:01", "17:00:00", target).calc();
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "17:00:01", "20:00:00", target).calc();
      new PerformanceUtil(new File(RESULTS_DEF), SPLITS_DEF, "00:00:00", "20:00:00", target).calc();
    }
  }
  
  PerformanceUtil(File results, Double[] splits, String timeMin, String timeMax, String timeTarg) {
    System.out.println("Processing input " + results + ", splits = " + Arrays.asList((Object[]) splits) +
        " in range [" + timeMin + ", " + timeMax + "] for target time " + timeTarg);
    this.results = results;
    this.splits = splits;
    this.timeMin = getTimeInSeconds(timeMin);
    this.timeMax = getTimeInSeconds(timeMax);
    this.timeTarg = getTimeInSeconds(timeTarg);
  }
  
  private void calc() throws Exception {
    BufferedReader reader = null;
    String line = null;
    double[] percTot = new double[splits.length - 1];
    int matchingEntries = 0;
    try {
      reader = new BufferedReader(new FileReader(results));
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        String[] current = line.split("\\s+");
        if (current.length != splits.length) {
          System.out.println("[WARN] Invalid line " + line);
        } else {
          long totalTime = getTimeInSeconds(current[current.length - 1]);
          if (totalTime < timeMin || totalTime > timeMax) {
            continue;
          }
          ++matchingEntries;
          for (int i = 0; i < percTot.length; ++i) {
            long splitTime = getTimeInSeconds(current[i]);
            percTot[i] += splitTime / (double) totalTime;
          }
        }
      }
      System.out.println("Found " + matchingEntries + " matching entries");
      for (int i = 0; i < percTot.length; ++i) {
        double avgPerc = percTot[i] / (double) matchingEntries;
        long avgTime = (long) (avgPerc * timeTarg);
        System.out.println("Average time at " + splits[i] + "km " + getTimeAsString(avgTime));
      }
    } finally {
      try {
        if (reader != null) {
          reader.close();
        }
      } catch (IOException ignore) {
        // silent catch
      }
    }
  }
  
  private static long getTimeInSeconds(String time) {
    time = time.trim();
    StringTokenizer st = new StringTokenizer(time, ":", false);
    int h = 0;
    int m = 0;
    int s = 0;
    if (st.hasMoreTokens()) {
      h = Integer.parseInt(st.nextToken());
    }
    if (st.hasMoreTokens()) {
      m = Integer.parseInt(st.nextToken());
    }
    if (st.hasMoreTokens()) {
      String last = st.nextToken();
      if (last.endsWith("PM") && h < 12) {
        h += 12;
      }
      int to = last.length();
      for (int i = 0; i < last.length(); ++i) {
        if (!Character.isDigit(last.charAt(i))) {
          to = i;
          break;
        }
      }
      s = Integer.parseInt(last.substring(0, to));
    }
    return 3600 * h + 60 * m + s;
  }
  
  private static String convertTimeUnit(int t) {
    String tt = String.valueOf(t);
    return t < 10 ? "0" + tt : tt;
  }
  
  private static String getTimeAsString(long seconds) {
    int h = (int) (seconds / 3600.0);
    int m = (int) ((seconds - 3600 * h) / 60.0);
    int s = (int) (seconds % 60);
    return convertTimeUnit(h) + ':' + convertTimeUnit(m) + ':' + convertTimeUnit(s);
  }

}
