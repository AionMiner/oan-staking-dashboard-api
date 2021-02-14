package com.bloxbean.oan.dashboard.processor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class TimeTest {
    public static Integer getDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        DateFormat df = new SimpleDateFormat("yyyyMM");

        String date = df.format(calendar.getTime());
        int time = 24 * 60 * 60 * 30;
        System.out.println(time);
        return Integer.parseInt(date);
    }

    public static void main(String[] args) {
        System.out.println(getDate());
    }
}
