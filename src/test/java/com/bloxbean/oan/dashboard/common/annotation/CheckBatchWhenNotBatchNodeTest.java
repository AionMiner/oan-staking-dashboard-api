package com.bloxbean.oan.dashboard.common.annotation;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@MicronautTest
public class CheckBatchWhenNotBatchNodeTest {

    @Inject
    private CheckBatchSample checkBatchSample;

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @BeforeEach
    public void setup() {
        checkBatchSample.batchRunCounter = 0;
    }

    @Test
    @Property(name = "batch.node", value = "false")
    public void whenBatchModeFalse_DontRun() {
        doReturn(false).when(taskScheduleTracker).isDisableBatch();

        checkBatchSample.checkBatchAndRun();

        Assertions.assertEquals(0, checkBatchSample.batchRunCounter);
    }

    @MockBean(TaskScheduleTracker.class)
    TaskScheduleTracker taskSchedulerTracker() {
        return mock(TaskScheduleTracker.class);
    }

}
