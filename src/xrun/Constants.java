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
package xrun;

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

}
