package com.bloxbean.oan.dashboard.common.annotation;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationValue;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@Slf4j
public class ScheduleTaskTrackerInterceptor implements MethodInterceptor {

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @Value("${batch.node}")
    private boolean isBatch;


    @Override
    public Object intercept(MethodInvocationContext context) {
        if(isBatch) {
            log.info("Batch mode. Start batch.");
            if(taskScheduleTracker.isDisableBatch()) {
                log.info("But Batch is disabled. So don't start now.");
                return null;
            }

            String taskId = null;

            AnnotationValue annotationValue = context.getAnnotation(ScheduleTaskTracker.class);
            Optional<String> value = Optional.empty();
            if (annotationValue != null) {
                value = annotationValue.getValue(String.class);
                if (value.isPresent()) {
                    taskId = taskScheduleTracker.taskStarted(value.get());

                    log.info("Task tracker started for task {}", value.get());

                    if (log.isDebugEnabled()) {
                        log.debug("Invoking TaskSchedulerTracker to store the timer value {} for method {}",
                                value.get(), context.getExecutableMethod().getName());
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("No value found for annotation {} on method {}",
                                ScheduleTaskTracker.class, context.getExecutableMethod().getName());
                    }
                }
            }


            long startTime = System.currentTimeMillis();
            try {
                return context.proceed();
            } finally {
                long endTime = System.currentTimeMillis();
                if (value.isPresent()) {
                    taskScheduleTracker.taskFinished(value.get(), taskId, (endTime - startTime));
                    log.debug("Task tracker finished for {}", value.get());
                    if (log.isDebugEnabled()) {
                        log.debug("Invoking TaskSchedulerTracker to clear the timer value {} for method {}",
                                value.get(), context.getExecutableMethod().getName());
                    }
                }
            }
        } else {
            System.out.println("Not batch node. So don't start the batch.");
            return null;
        }
    }
}
