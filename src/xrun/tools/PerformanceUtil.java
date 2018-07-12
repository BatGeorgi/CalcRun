/*
 * Copyright (c) 1997-2018 by Bosch Software Innovations GmbH
 * http://www.bosch-si.com
 * All rights reserved,
 * also regarding any disposal, exploitation, reproduction,
 * editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch Software Innovations GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch Software Innovations.
 */
package xrun.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;

public class PerformanceUtil {
  
  private static final String RESULTS_DEF = "C:/GBV/Tracks/allparts.txt";
  private static final double[] SPLITS_DEF = new double[] {32, 59, 75, 98.4};
  private static final String TIME_MIN_DEF = "08:30:00";
  private static final String TIME_MAX_DEF = "09:30:00";
  private static final String TIME_TARGET_DEF = "13:40:00";
  
  private File results;
  private double[] splits;
  private long timeMin;
  private long timeMax;
  private long timeTarg;

  public static void main(String[] args) throws Exception {
    File results = new File(args.length > 0 ? args[0] : RESULTS_DEF);
    double[] splits = SPLITS_DEF;
    // TODO parse splits from args if available
    String timeMin = args.length > 2 ? args[2] : TIME_MIN_DEF;
    String timeMax = args.length > 3 ? args[3] : TIME_MAX_DEF;
    String timeTarg = args.length > 4 ? args[4] : TIME_TARGET_DEF;
    System.out.println("Processing input " + results + ", splits = " + Arrays.asList(splits) +
        " in range [" + timeMin + ", " + timeMax + "]");
    new PerformanceUtil(results, splits, timeMin, timeMax, timeTarg).calc();
  }
  
  PerformanceUtil(File results, double[] splits, String timeMin, String timeMax, String timeTarg) {
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
