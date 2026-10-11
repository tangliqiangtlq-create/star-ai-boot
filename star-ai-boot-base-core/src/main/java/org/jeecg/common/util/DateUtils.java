package org.jeecg.common.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jeecg.common.constant.SymbolConstant;
import org.springframework.util.StringUtils;

import java.beans.PropertyEditorSupport;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Class Description: time operation definition class
 *
 * @Author: Zhang Daihao
 * @Date:2012-12-8 12:15:03
 * @Version 1.0
 */
public class DateUtils extends PropertyEditorSupport {
  private static final Logger log = LoggerFactory.getLogger(DateUtils.class);


    public static ThreadLocal<SimpleDateFormat> date_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd");
        }
    };
    public static ThreadLocal<SimpleDateFormat> yyyyMMdd = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMdd");
        }
    };
    public static ThreadLocal<SimpleDateFormat> date_sdf_wz = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd");
        }
    };
    public static ThreadLocal<SimpleDateFormat> time_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm");
        }
    };
    public static ThreadLocal<SimpleDateFormat> yyyymmddhhmmss = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss");
        }
    };
    public static ThreadLocal<SimpleDateFormat> short_time_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm");
        }
    };
    public static ThreadLocal<SimpleDateFormat> datetimeFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    /**
     * Time in milliseconds.
     */
    private static final long DAY_IN_MILLIS = 24 * 3600 * 1000;
    private static final long HOUR_IN_MILLIS = 3600 * 1000;
    private static final long MINUTE_IN_MILLIS = 60 * 1000;
    private static final long SECOND_IN_MILLIS = 1000;

    /**
     * Time format for the specified pattern
     * @param pattern
     * @return
     */
    private static SimpleDateFormat getSdFormat(String pattern) {
        return new SimpleDateFormat(pattern);
    }

    /**
     * Current calendar, represented in China time
     *
     * @return system current calendar in the local time zone
     */
    public static Calendar getCalendar() {
        return Calendar.getInstance();
    }

    /**
     * Calendar represented by the specified milliseconds
     *
     * @param millis milliseconds
     * @return calendar for the specified milliseconds
     */
    public static Calendar getCalendar(long millis) {
        Calendar cal = Calendar.getInstance();
        // --------------------cal.setTimeInMillis(millis);
        cal.setTime(new Date(millis));
        return cal;
    }

    // ////////////////////////////////////////////////////////////////////////////
    // getDate
    // Date obtained in various ways
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Current date
     *
     * @return system current time
     */
    public static Date getDate() {
        return new Date();
    }
    
    
    /**
     * Current date
     *
     * @return system current date (without time)
     */
    public static LocalDate getLocalDate() {
        LocalDate today = LocalDate.now();
        return today;
    }

    /**
     * Date represented by the specified milliseconds
     *
     * @param millis milliseconds
     * @return date for the specified milliseconds
     */
    public static Date getDate(long millis) {
        return new Date(millis);
    }

    /**
     * Convert timestamp to string
     *
     * @param time
     * @return
     */
    public static String timestamptoStr(Timestamp time) {
        Date date = null;
        if (null != time) {
            date = new Date(time.getTime());
        }
        return date2Str(date_sdf.get());
    }

    /**
     * Convert string to timestamp
     *
     * @param str
     * @return
     */
    public static Timestamp str2Timestamp(String str) {
        Date date = str2Date(str, date_sdf.get());
        return new Timestamp(date.getTime());
    }

    /**
     * Convert string to date
     *
     * @param str
     * @param sdf
     * @return
     */
    public static Date str2Date(String str, SimpleDateFormat sdf) {
        if (null == str || "".equals(str)) {
            return null;
        }
        Date date = null;
        try {
            date = sdf.parse(str);
            return date;
        } catch (ParseException e) {
            log.error("operation failed", e);
        }
        return null;
    }

    /**
     * Convert date to string
     *
     * @param dateSdf date format
     * @return string
     */
    public static String date2Str(SimpleDateFormat dateSdf) {
        synchronized (dateSdf) {
            Date date = getDate();
            if (null == date) {
                return null;
            }
            return dateSdf.format(date);
        }
    }

    /**
     * Format date/time
     *
     * @param date
     * @param format
     * @return
     */
    public static String dateformat(String date, String format) {
        SimpleDateFormat sformat = new SimpleDateFormat(format);
        Date nowDate = null;
        try {
            nowDate = sformat.parse(date);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            log.error("operation failed", e);
        }
        return sformat.format(nowDate);
    }

    /**
     * Convert date to string
     *
     * @param date     date
     * @param dateSdf date format
     * @return string
     */
    public static String date2Str(Date date, SimpleDateFormat dateSdf) {
        synchronized (dateSdf) {
            if (null == date) {
                return null;
            }
            return dateSdf.format(date);
        }
    }

    /**
     * Convert date to string
     *
     * @param format date format
     * @return string
     */
    public static String getDate(String format) {
        Date date = new Date();
        if (null == date) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    /**
     * Timestamp for the specified milliseconds
     *
     * @param millis milliseconds
     * @return timestamp for the specified milliseconds
     */
    public static Timestamp getTimestamp(long millis) {
        return new Timestamp(millis);
    }

    /**
     * Timestamp represented as a string.
     *
     * @param time milliseconds
     * @return timestamp as a string
     */
    public static Timestamp getTimestamp(String time) {
        return new Timestamp(Long.parseLong(time));
    }

    /**
     * Current system timestamp
     *
     * @return current system timestamp
     */
    public static Timestamp getTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Current time in yyyy-MM-dd HH:mm:ss format
     *
     * @return current time as a standard string
     */
    public static String now() {
        return datetimeFormat.get().format(getCalendar().getTime());
    }

    /**
     * Timestamp of the specified date
     *
     * @param date specified date
     * @return timestamp of the specified date
     */
    public static Timestamp getTimestamp(Date date) {
        return new Timestamp(date.getTime());
    }

    /**
     * Timestamp of the specified calendar
     *
     * @param cal specified calendar
     * @return timestamp of the specified calendar
     */
    public static Timestamp getCalendarTimestamp(Calendar cal) {
        // ---------------------return new Timestamp(cal.getTimeInMillis());
        return new Timestamp(cal.getTime().getTime());
    }

    public static Timestamp gettimestamp() {
        Date dt = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowTime = df.format(dt);
        java.sql.Timestamp buydate = java.sql.Timestamp.valueOf(nowTime);
        return buydate;
    }

    // ////////////////////////////////////////////////////////////////////////////
    // getMillis
    // Millis obtained in various ways
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * System time in milliseconds
     *
     * @return system time in milliseconds
     */
    public static long getMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Milliseconds of the specified calendar
     *
     * @param cal specified calendar
     * @return milliseconds of the specified calendar
     */
    public static long getMillis(Calendar cal) {
        // --------------------return cal.getTimeInMillis();
        return cal.getTime().getTime();
    }

    /**
     * Milliseconds of the specified date
     *
     * @param date specified date
     * @return milliseconds of the specified date
     */
    public static long getMillis(Date date) {
        return date.getTime();
    }

    /**
     * Milliseconds of the specified timestamp
     *
     * @param ts specified timestamp
     * @return milliseconds of the specified timestamp
     */
    public static long getMillis(Timestamp ts) {
        return ts.getTime();
    }

    // ////////////////////////////////////////////////////////////////////////////
    // formatDate
    // Convert date to string with a given format
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * System Current date represented by default, specific format: year-month-day
     *
     * @return The default date is displayed in the format of "year-month-day"
     */
    public static String formatDate() {
        return date_sdf.get().format(getCalendar().getTime());
    }

    /**
     * System Current date represented by default, specific format: yyyy-MM-dd HH:mm:ss
     *
     * @return The default date is displayed in the format of "yyyy-MM-dd HH:mm:ss"
     */
    public static String formatDateTime() {
        return datetimeFormat.get().format(getCalendar().getTime());
    }

    /**
     * Get time string
     */
    public static String getDataString(SimpleDateFormat formatstr) {
        synchronized (formatstr) {
            return formatstr.format(getCalendar().getTime());
        }
    }

    /**
     * Default display for the specified calendar: yyyy-MM-dd
     *
     * @param cal specified calendar
     * @return date formatted as yyyy-MM-dd
     */
    public static String formatDate(Calendar cal) {
        return date_sdf.get().format(cal.getTime());
    }

    /**
     * Default display for the specified date: yyyy-MM-dd
     *
     * @param date specified date
     * @return date formatted as yyyy-MM-dd
     */
    public static String formatDate(Date date) {
        return date_sdf.get().format(date);
    }

    /**
     * Default display for the date represented by milliseconds: yyyy-MM-dd
     *
     * @param millis specified milliseconds
     * @return date formatted as yyyy-MM-dd
     */
    public static String formatDate(long millis) {
        return date_sdf.get().format(new Date(millis));
    }

    /**
     * The default date is displayed in the specified format
     *
     * @param pattern specified format
     * @return The default date is displayed in the specified format
     */
    public static String formatDate(String pattern) {
        return getSdFormat(pattern).format(getCalendar().getTime());
    }

    /**
     * Format the specified calendar with the given pattern
     *
     * @param cal     specified calendar
     * @param pattern format pattern
     * @return formatted date string
     */
    public static String formatDate(Calendar cal, String pattern) {
        return getSdFormat(pattern).format(cal.getTime());
    }

    /**
     * Format the specified date with the given pattern
     *
     * @param date    specified date
     * @param pattern format pattern
     * @return formatted date string
     */
    public static String formatDate(Date date, String pattern) {
        return getSdFormat(pattern).format(date);
    }

    // ////////////////////////////////////////////////////////////////////////////
    // formatTime
    // Convert date to string with a given format
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * System Current date represented by default, specific format: year-month-day hour: minute
     *
     * @return The default date is displayed in the format of "year-month-day hour: minute"
     */
    public static String formatTime() {
        return time_sdf.get().format(getCalendar().getTime());
    }

    /**
     * Default display for the date represented by milliseconds: yyyy-MM-dd HH:mm
     *
     * @param millis specified milliseconds
     * @return date formatted as yyyy-MM-dd HH:mm
     */
    public static String formatTime(long millis) {
        return time_sdf.get().format(new Date(millis));
    }

    /**
     * Default display for the specified calendar: yyyy-MM-dd HH:mm
     *
     * @param cal specified calendar
     * @return date formatted as yyyy-MM-dd HH:mm
     */
    public static String formatTime(Calendar cal) {
        return time_sdf.get().format(cal.getTime());
    }

    /**
     * Default display for the specified date: yyyy-MM-dd HH:mm
     *
     * @param date specified date
     * @return date formatted as yyyy-MM-dd HH:mm
     */
    public static String formatTime(Date date) {
        return time_sdf.get().format(date);
    }

    // ////////////////////////////////////////////////////////////////////////////
    // formatShortTime
    // Convert date to string with a given format
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * System Current date expressed in the default way, specific format: hours: minutes
     *
     * @return The default date is displayed in the format of "hour:minute"
     */
    public static String formatShortTime() {
        return short_time_sdf.get().format(getCalendar().getTime());
    }

    /**
     * Default display for the time represented by milliseconds: HH:mm
     *
     * @param millis specified milliseconds
     * @return time formatted as HH:mm
     */
    public static String formatShortTime(long millis) {
        return short_time_sdf.get().format(new Date(millis));
    }

    /**
     * Default display for the specified calendar: HH:mm
     *
     * @param cal specified calendar
     * @return time formatted as HH:mm
     */
    public static String formatShortTime(Calendar cal) {
        return short_time_sdf.get().format(cal.getTime());
    }

    /**
     * Default display for the specified date: HH:mm
     *
     * @param date specified date
     * @return time formatted as HH:mm
     */
    public static String formatShortTime(Date date) {
        return short_time_sdf.get().format(date);
    }

    // ////////////////////////////////////////////////////////////////////////////
    // parseDate
    // parseCalendar
    // parseTimestamp
    // Convert a string into a date or time in a certain format
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Convert string to Date by the specified format, e.g. input 2003-11-19 11:20:20 is parsed accordingly
     *
     * @param src     source string to convert
     * @param pattern matching format pattern
     * @return converted date if successful
     * @throws ParseException
     */
    public static Date parseDate(String src, String pattern) throws ParseException {
        return getSdFormat(pattern).parse(src);

    }

    /**
     * Convert string to Date by the specified format, e.g. input 2003-11-19 11:20:20 is parsed accordingly
     *
     * @param src     source string to convert
     * @param pattern matching format pattern
     * @return converted date if successful
     * @throws ParseException
     */
    public static Calendar parseCalendar(String src, String pattern) throws ParseException {

        Date date = parseDate(src, pattern);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public static String formatAddDate(String src, String pattern, int amount) throws ParseException {
        Calendar cal;
        cal = parseCalendar(src, pattern);
        cal.add(Calendar.DATE, amount);
        return formatDate(cal);
    }

    /**
     * Convert string to Date by the specified format, e.g. input 2003-11-19 11:20:20 is parsed accordingly
     *
     * @param src     source string to convert
     * @param pattern matching format pattern
     * @return converted timestamp if successful
     * @throws ParseException
     */
    public static Timestamp parseTimestamp(String src, String pattern) throws ParseException {
        Date date = parseDate(src, pattern);
        return new Timestamp(date.getTime());
    }

    // ////////////////////////////////////////////////////////////////////////////
    // dateDiff
    // Calculate the difference between two dates
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Calculates the difference between two times, varying depending on the flag
     *
     * @param flag Calculation flag, indicating calculation based on year/month/day/hour/minute/second, etc.
     * @param calSrc subtraction
     * @param calDes minuend
     * @return the difference between two dates
     */
    public static int dateDiff(char flag, Calendar calSrc, Calendar calDes) {

        long millisDiff = getMillis(calSrc) - getMillis(calDes);
        char year = 'y';
        char day = 'd';
        char hour = 'h';
        char minute = 'm';
        char second = 's';

        if (flag == year) {
            return (calSrc.get(Calendar.YEAR) - calDes.get(Calendar.YEAR));
        }

        if (flag == day) {
            return (int) (millisDiff / DAY_IN_MILLIS);
        }

        if (flag == hour) {
            return (int) (millisDiff / HOUR_IN_MILLIS);
        }

        if (flag == minute) {
            return (int) (millisDiff / MINUTE_IN_MILLIS);
        }

        if (flag == second) {
            return (int) (millisDiff / SECOND_IN_MILLIS);
        }

        return 0;
    }

    public static Long getCurrentTimestamp() {
        return Long.valueOf(DateUtils.yyyymmddhhmmss.get().format(new Date()));
    }

    /**
     * Converts a String to Date. Length 10 uses format "yyyy-MM-dd"; length 19 uses "yyyy-MM-dd
     * HH:mm:ss".
     * @param text String datetime value
     */
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        if (StringUtils.hasText(text)) {
            try {
                int length10 = 10;
                int length19 = 19;
                if (text.indexOf(SymbolConstant.COLON) == -1 && text.length() == length10) {
                    setValue(DateUtils.date_sdf.get().parse(text));
                } else if (text.indexOf(SymbolConstant.COLON) > 0 && text.length() == length19) {
                    setValue(DateUtils.datetimeFormat.get().parse(text));
                } else {
                    throw new IllegalArgumentException("Could not parse date, date format is error ");
                }
            } catch (ParseException ex) {
                IllegalArgumentException iae = new IllegalArgumentException("Could not parse date: " + ex.getMessage());
                iae.initCause(ex);
                throw iae;
            }
        } else {
            setValue(null);
        }
    }

    public static int getYear() {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(getDate());
        return calendar.get(Calendar.YEAR);
    }

    /**
     * Convert string to time
     * @param str
     * @return
     */
    public static Date parseDatetime(String str){
        try {
            return datetimeFormat.get().parse(str);
        }catch (Exception e){
        }
        return null;
    }

    /**
     * Check whether two dates fall on the same day.
     *
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        boolean isSameYear = calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR);
        boolean isSameMonth = isSameYear && calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH);
        return isSameMonth && calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Calculate the time difference from Current date
     *
     * @param targetDate
     * @return
     */
    public static long calculateTimeDifference(Date targetDate) {
        // Get current time
        LocalDateTime currentTime = LocalDateTime.now();

        // Convert java.util.Date to java.time.LocalDateTime
        LocalDateTime convertedTargetDate = targetDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        // Calculate time difference
        Duration duration = Duration.between(currentTime, convertedTargetDate);

        // Get the time difference in milliseconds
        long timeDifferenceInMillis = duration.toMillis();

        return timeDifferenceInMillis;
    }

    /**
     * Calculate the date difference from Current date
     *
     * @param targetDate
     * @return
     */
    public static long calculateDaysDifference(Date targetDate) {
        // Get Current date
        LocalDate currentDate = LocalDate.now();
        // Convert java.util.Date to java.time.LocalDate
        LocalDate convertedTargetDate = targetDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        // Calculate date difference
        long daysDifference = ChronoUnit.DAYS.between(currentDate, convertedTargetDate);
        return daysDifference;
    }

    /**
     * Check whether two dates fall in the same week.
     *
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isSameWeek(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        boolean isSameYear = calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR);
        return isSameYear && calendar1.get(Calendar.WEEK_OF_YEAR) == calendar2.get(Calendar.WEEK_OF_YEAR);
    }

    /**
     * Check whether two dates fall in the same month.
     *
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isSameMonth(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        boolean isSameYear = calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR);
        return isSameYear && calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH);
    }

    /**
     * Check whether two dates fall in the same year.
     *
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isSameYear(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR);
    }

}