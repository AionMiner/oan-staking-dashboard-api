package com.bloxbean.oan.dashboard.common;

import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.aion4j.avm.helper.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class TaskScheduleTracker {

    public static final String TIMERS_BUCKET = "timers.bucket.map";
    public static final String TIMERS_ACTIVITY_STREAM = "timers.activity.stream";
    public static final String TIMER_GLOBAL_LOCK = "timer.global.lock";
    private static final String DISABLE_BATCH = "batch.disabled";
    private static final String TIMER_ID_GEN_KEY = "timer.id.gen.key";

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Value("${batch.node}")
    private boolean isBatch;

    public void enableBatch() {
        connection.sync().del(DISABLE_BATCH);
    }

    public void disableBatch() {
        connection.sync().set(DISABLE_BATCH, "true");
    }

    public boolean isDisableBatch() {
        String disableBatch = connection.sync().get(DISABLE_BATCH);
        if(StringUtils.isEmpty(disableBatch))
            return false;
        else
            return Boolean.parseBoolean(disableBatch);
    }

    public String taskStarted(String taskName) {
        if(!isBatch)
            return null;
        if(StringUtils.isEmpty(taskName))
            return null;

        String timerKey = getTimerKey(taskName);

        TaskActivity taskActivity = TaskActivity.builder()
                .id(getNewId())
                .name(timerKey)
                .action(TaskActivity.TASK_STARTED)
                .timestamp(System.currentTimeMillis())
                .build();

        connection.sync().sadd(TIMERS_BUCKET, timerKey);
        connection.sync().rpush(TIMERS_ACTIVITY_STREAM, JsonUtil.toJson(taskActivity));

        return taskActivity.getId();
    }

    public void taskFinished(String taskName, String taskId, long timeTakenInMillis) {
        if(!isBatch)
            return;
        if(StringUtils.isEmpty(taskName))
            return;

        String timerKey = getTimerKey(taskName);

        TaskActivity taskActivity = TaskActivity.builder()
                .id(taskId)
                .name(timerKey)
                .action(TaskActivity.TASK_FINISHED)
                .timestamp(System.currentTimeMillis())
                .timeTaken(timeTakenInMillis)
                .build();

        connection.sync().srem(TIMERS_BUCKET, timerKey);
        connection.sync().rpush(TIMERS_ACTIVITY_STREAM, JsonUtil.toJson(taskActivity));

        connection.sync().ltrim(TIMERS_ACTIVITY_STREAM, -500, -1); //Last 500 activities
    }

    public boolean isTaskRunning(String taskName) {
        if(!isBatch)
            return false;
        if(StringUtils.isEmpty(taskName))
            return false;

        String timerKey = getTimerKey(taskName);
        return connection.sync().sismember(TIMERS_BUCKET, timerKey);
    }

    public List<TaskActivity> getTimerTaskActivities(long start, long end) {

        //Revrange
        if(start == 0)
            start = -1;
        else
            start = -1 * start;

        end = -1 * end;

        List<String> activities = connection.sync().lrange(TIMERS_ACTIVITY_STREAM, end, start); //need to reverse

        if(activities == null || activities.isEmpty())
            return Collections.emptyList();

        Collections.reverse(activities);

        return activities.stream().map(s -> JsonUtil.fromJson(s, TaskActivity.class)).collect(Collectors.toList());
    }

    public Set<String> getRunningTimers() {
        return connection.sync().smembers(TIMERS_BUCKET);
    }

    public void getGlobalLock(String owner, long inMin) {

    }

    public void cleanAll() {
        if(!isBatch)
            return;

        connection.sync().del(TIMERS_BUCKET);
        connection.sync().del(TIMER_GLOBAL_LOCK);
    }

    private String getTimerKey(String taskName) {
        return "timer." + taskName;
    }

    private String getNewId() {
        try {
            return connection.sync().incr(TIMER_ID_GEN_KEY) + "";
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

}
