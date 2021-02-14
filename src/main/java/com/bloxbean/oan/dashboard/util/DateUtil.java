package com.bloxbean.oan.dashboard.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {

    public static String currentTimeInGMT() {
        SimpleDateFormat formatter= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }
}
