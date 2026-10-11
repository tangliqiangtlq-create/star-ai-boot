package org.jeecg.common.util;

import cn.hutool.core.date.DateUtil;
import org.jeecg.common.constant.enums.DateRangeEnum;

import java.util.Calendar;
import java.util.Date;

/**
 * Date range tool class
 *
 * @author scott
 * @date 20230801
 */
public class DateRangeUtils {

    /**
     * Get date range by date range enum
     *
     * @param rangeEnum
     * @return Date[]
     */
    public static Date[] getDateRangeByEnum(DateRangeEnum rangeEnum) {
        if (rangeEnum == null) {
            return null;
        }
        Date[] ranges = new Date[2];
        switch (rangeEnum) {
            case TODAY:
                ranges[0] = getTodayStartTime();
                ranges[1] = getTodayEndTime();
                break;
            case YESTERDAY:
                ranges[0] = getYesterdayStartTime();
                ranges[1] = getYesterdayEndTime();
                break;
            case TOMORROW:
                ranges[0] = getTomorrowStartTime();
                ranges[1] = getTomorrowEndTime();
                break;
            case THIS_WEEK:
                ranges[0] = getThisWeekStartDay();
                ranges[1] = getThisWeekEndDay();
                break;
            case LAST_WEEK:
                ranges[0] = getLastWeekStartDay();
                ranges[1] = getLastWeekEndDay();
                break;
            case NEXT_WEEK:
                ranges[0] = getNextWeekStartDay();
                ranges[1] = getNextWeekEndDay();
                break;
            case LAST_7_DAYS:
                ranges[0] = getLast7DaysStartTime();
                ranges[1] = getLast7DaysEndTime();
                break;
            case THIS_MONTH:
                ranges[0] = getThisMonthStartDay();
                ranges[1] = getThisMonthEndDay();
                break;
            case LAST_MONTH:
                ranges[0] = getLastMonthStartDay();
                ranges[1] = getLastMonthEndDay();
                break;
            case NEXT_MONTH:
                ranges[0] = getNextMonthStartDay();
                ranges[1] = getNextMonthEndDay();
                break;
            default:
                return null;
        }
        return ranges;
    }

    /**
     * Get the first day of next month, Sunday 00:00:00
     */
    public static Date getNextMonthStartDay() {
        return DateUtil.beginOfMonth(DateUtil.nextMonth());
    }

    /**
     * Get the last day of next month 23:59:59
     */
    public static Date getNextMonthEndDay() {
        return DateUtil.endOfMonth(DateUtil.nextMonth());
    }

    /**
     * Get the first day of This month Sunday 00:00:00
     */
    public static Date getThisMonthStartDay() {
        return DateUtil.beginOfMonth(DateUtil.date());
    }

    /**
     * Get Last day of This month 23:59:59
     */
    public static Date getThisMonthEndDay() {
        return DateUtil.endOfMonth(DateUtil.date());
    }

    /**
     * Get the first day of the previous month, Sunday 00:00:00
     */
    public static Date getLastMonthStartDay() {
        return DateUtil.beginOfMonth(DateUtil.lastMonth());
    }

    /**
     * Get the last day of the previous month 23:59:59
     */
    public static Date getLastMonthEndDay() {
        return DateUtil.endOfMonth(DateUtil.lastMonth());
    }

    /**
     * Get the first day of last week Monday 00:00:00
     */
    public static Date getLastWeekStartDay() {
        return DateUtil.beginOfWeek(DateUtil.lastWeek());
    }

    /**
     * Get last day of last week Sunday 23:59:59
     */
    public static Date getLastWeekEndDay() {
        return DateUtil.endOfWeek(DateUtil.lastWeek());
    }

    /**
     * Get the first day of This week Monday 00:00:00
     */
    public static Date getThisWeekStartDay() {
        Date today = new Date();
        return DateUtil.beginOfWeek(today);
    }

    /**
     * Get the last day of This week Sunday 23:59:59
     */
    public static Date getThisWeekEndDay() {
        Date today = new Date();
        return DateUtil.endOfWeek(today);
    }

    /**
     * Get the first day of next week Monday 00:00:00
     */
    public static Date getNextWeekStartDay() {
        return DateUtil.beginOfWeek(DateUtil.nextWeek());
    }

    /**
     * Get the last day of next week Sunday 23:59:59
     */
    public static Date getNextWeekEndDay() {
        return DateUtil.endOfWeek(DateUtil.nextWeek());
    }

    /**
     * start time in the past seven days (excluding Today)
     *
     * @return
     */
    public static Date getLast7DaysStartTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -7);
        return DateUtil.beginOfDay(calendar.getTime());
    }

    /**
     * end time in the past seven days (excluding Today)
     *
     * @return
     */
    public static Date getLast7DaysEndTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(getLast7DaysStartTime());
        calendar.add(Calendar.DATE, 6);
        return DateUtil.endOfDay(calendar.getTime());
    }

    /**
     * Start time of yesterday
     *
     * @return
     */
    public static Date getYesterdayStartTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -1);
        return DateUtil.beginOfDay(calendar.getTime());
    }

    /**
     * End time of yesterday
     *
     * @return
     */
    public static Date getYesterdayEndTime() {
        return DateUtil.endOfDay(getYesterdayStartTime());
    }

    /**
     * Start time of tomorrow
     *
     * @return
     */
    public static Date getTomorrowStartTime() {
        return DateUtil.beginOfDay(DateUtil.tomorrow());
    }

    /**
     * End time of tomorrow
     *
     * @return
     */
    public static Date getTomorrowEndTime() {
        return DateUtil.endOfDay(DateUtil.tomorrow());
    }

    /**
     * Start of today.
     *
     * @return
     */
    public static Date getTodayStartTime() {
        return DateUtil.beginOfDay(new Date());
    }

    /**
     * End of today.
     *
     * @return
     */
    public static Date getTodayEndTime() {
        return DateUtil.endOfDay(new Date());
    }

}
