package com.bloxbean.oan.dashboard.common.annotation;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.mockito.Mockito.*;

@MicronautTest
public class ScheduleTaskTrackerTest {

    @Inject
    ScheduleTaskTrackerSample scheduleTaskTrackerSample;

    @Inject
    TaskScheduleTracker taskScheduleTracker;

    @BeforeEach
    public void setup() {
//        Logger root = (Logger)LoggerFactory.getLogger(ScheduleTaskTrackerInterceptor.class);
//        root.setLevel(Level.DEBUG);
    }

    @Test
    @Property(name = "batch.node", value = "true")
    public void whenAnnotated_thenStoreRunningTaskName() {
        doReturn(false).when(taskScheduleTracker).isDisableBatch();
        doReturn("t1").when(taskScheduleTracker).taskStarted(anyString());

        scheduleTaskTrackerSample.runScheduleTask();
        verify(taskScheduleTracker, times(1)).taskStarted("myTracker");
        verify(taskScheduleTracker, times(1)).taskFinished(eq("myTracker"), eq("t1"), anyLong());
    }

    @Test
    @Property(name = "batch.node", value = "true")
    public void whenAnnotatedAndThrowException_thenClearRunningTaskName() {
        doReturn(false).when(taskScheduleTracker).isDisableBatch();
        doReturn("t1").when(taskScheduleTracker).taskStarted(anyString());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            scheduleTaskTrackerSample.runScheduleTaskThrowException();
        });

        verify(taskScheduleTracker, times(1)).taskStarted("myTracker1");
        verify(taskScheduleTracker, times(1)).taskFinished(eq("myTracker1"), eq("t1"), anyLong());
    }

    @Test
    @Property(name = "batch.node", value = "true")
    public void whenAnnotatedWithNoValue_thenDontStoreTimerName() {
        scheduleTaskTrackerSample.runScheduleTaskNoValue();
        spy(taskScheduleTracker).taskFinished(anyString(), anyString(), anyLong());
        verify(taskScheduleTracker, never()).taskStarted(anyString());
        verify(taskScheduleTracker, never()).taskFinished(anyString(), anyString(), anyLong());
    }

    @Test
    @Property(name = "batch.node", value = "true")
    public void whenAnnotatedAndBatchDisabled_thenDontRunBatch() {
        doReturn(true).when(taskScheduleTracker).isDisableBatch();

        scheduleTaskTrackerSample.runScheduleTaskNoValue();

        verify(taskScheduleTracker, never()).taskStarted(anyString());
        verify(taskScheduleTracker, never()).taskFinished(anyString(), anyString(), anyLong());
    }

    @MockBean(TaskScheduleTracker.class)
    TaskScheduleTracker taskSchedulerTracker() {
        return mock(TaskScheduleTracker.class);
    }
}
