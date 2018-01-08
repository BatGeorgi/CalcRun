package xrun;

import org.json.JSONObject;


public class WeekCalc {
	
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
	
	static final int[] identifyWeek(int day, int month, int year) { // result - week number, week start day, week start month
		int firstMondayNumber = (7 - STARTS[year - START_YEAR]) % 7 + 1;
		if (month == 1 && day < firstMondayNumber) {
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
		while (diff > 0) {
			if (day == 1) {
				if (month == 1) {
					break;
				}
				--month;
				day = (isLeap && month == 2 ? 29 : LENS[month - 1]);
			} else {
				--day;
			}
			--diff;
		}
		return new int[] {weekNumber, day, month};
	}
	
	static int getWeekCount(int year) {
		int firstMondayNumber = (7 - STARTS[year - START_YEAR]) % 7 + 1;
		boolean isLeap = year % 4 == 0 && year != 2100;
		return isLeap && firstMondayNumber == 2 ? 54 : 53;
	}

}
