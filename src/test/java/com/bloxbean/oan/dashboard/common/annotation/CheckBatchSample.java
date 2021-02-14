package com.bloxbean.oan.dashboard.common.annotation;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class CheckBatchSample {
    public int batchRunCounter = 0;

    @ScheduleTaskTracker("myTracker")
    public void checkBatchAndRun() {
        System.out.println("Running check batch and run ...");
        batchRunCounter ++;
    }
}
