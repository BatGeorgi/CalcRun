package xrun.utils;

import xrun.Constants;

public class CalendarUtils {
	
	private static final int[] LENS = new int[] {
		31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
	};
	private static final int[] OFFS = new int[] {
		31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334
	};
	private static final int START_YEAR = 2000;
	private static final int[] STARTS = new int[] {
		5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2, 4, 5, 6, 0, 2, 3, 4, 5, 0, 1, 2, 3, 5, 6, 0, 1, 3, 4, 5, 6, 1, 2, 3, 4, 6, 0, 1, 2
	};
	
	public static final int[] identifyWeek(int day, int month, int year, String[] out) { // result - week number, week start day, week start month
	  if (year < 2000) {
	    year = 2000;
	  }
	  int firstMondayNumber = (7 - STARTS[year - START_YEAR]) % 7 + 1;
		if (month == 1 && day < firstMondayNumber) {
		  out[0] = "1-" + (firstMondayNumber - 1) + " Jan";
			return new int[] {1, 1, 1};
		}
		boolean isLeap = year % 4 == 0 && year != 2100;
		int dayNumber = (month > 1 ? OFFS[month - 2] : 0) + day;
		if (isLeap && month > 2) {
			++dayNumber;
		}
		int weekNumber = (firstMondayNumber > 1 ? 2 : 1);
		int diff = dayNumber - firstMondayNumber;
		weekNumber += diff / 7;
		diff = diff % 7;
		if (day > diff) {
		  out[0] = formatWeek(day - diff, month, year);
		  return new int[] {weekNumber, day - diff, month};
		}
		int[] res = new int[] {weekNumber, (isLeap && month == 3 ? 29 : LENS[month - 2]) - (diff - day), month - 1};
		out[0] = formatWeek(res[1], res[2], year);
		return res;
	}
	
	private static String formatWeek(int dayBegin, int month, int year) {
	  int len = month == 2 && year % 4 == 0 && year != 2100 ? 29 : LENS[month - 1];
	  if (dayBegin + 7 <= len) {
	    return dayBegin + "-" + (dayBegin + 6) + " " + Constants.MONTHS[month - 1];
	  }
	  if (month == 12) {
	    return dayBegin + "-31 Dec";
	  }
	  return dayBegin + " " + Constants.MONTHS[month - 1] + "-" + (6 - len + dayBegin) + " " + Constants.MONTHS[month];
	}

	public static int getWeekCount(int year) {
		int firstMondayNumber = (7 - STARTS[year - START_YEAR]) % 7 + 1;
		boolean isLeap = year % 4 == 0 && year != 2100;
		return isLeap && firstMondayNumber == 2 ? 54 : 53;
	}

}
