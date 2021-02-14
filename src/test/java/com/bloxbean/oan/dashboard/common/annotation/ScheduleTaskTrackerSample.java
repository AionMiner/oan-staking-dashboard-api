package com.bloxbean.oan.dashboard.common.annotation;

import javax.inject.Singleton;

@Singleton
public class ScheduleTaskTrackerSample {

    @ScheduleTaskTracker("myTracker")
    public void runScheduleTask() {
        System.out.println("Running scheduled tasks ..");
    }

    @ScheduleTaskTracker("myTracker1")
    public void runScheduleTaskThrowException() {
        throw new IllegalArgumentException("Some exception");
    }

    @ScheduleTaskTracker
    public void runScheduleTaskNoValue() {
        System.out.println("Run scheduler but annotation has no value...");
    }
}
