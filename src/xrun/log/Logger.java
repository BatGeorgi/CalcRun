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
