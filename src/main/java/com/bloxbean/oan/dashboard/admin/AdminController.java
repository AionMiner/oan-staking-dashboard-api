package com.bloxbean.oan.dashboard.admin;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.common.TaskActivity;
import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

@Controller("/admin")
@Slf4j
public class AdminController {

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @Inject
    private AdminService adminService;

    @Get(uri="/timers/running")
    public Set<String> getRunningTimers() {
        return taskScheduleTracker.getRunningTimers();
    }

    @Get(uri="/batch/is_disable")
    public boolean isDiableBatch() {
        return taskScheduleTracker.isDisableBatch();
    }

    @Get(uri="/batch/activities")
    public List<TaskActivity> getTimerTaskActivities(@QueryValue Long start, @QueryValue Long end) {
        if(end - start > 20)
            throw new IllegalArgumentException("Could not request for more than 20 records in one request");

        return taskScheduleTracker.getTimerTaskActivities(start, end);

    }

    @Post("auth-key/generate")
    public HttpResponse generateNewAuthKey(@Nullable @Header(name = "Auth-Key") String authKey) throws NoSuchAlgorithmException {
        String newKey = adminService.generateNewAdminKey(authKey);

        if(newKey != null) { //generated. both first time and with valid auth
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("auth-key", newKey);

            return HttpResponse.ok(jsonObject.toString());
        } else {
            return HttpResponse.unauthorized();
        }
    }

    @Post("batch/disable")
    public HttpResponse disableBatch(@Body Flag flag, @Header(name = "Auth-Key") String authKey) throws NoSuchAlgorithmException {

        if(!adminService.verifyAdminAuthKey(authKey))
            return HttpResponse.unauthorized();

        if(flag != null && flag.value) {
            taskScheduleTracker.disableBatch();
            return HttpResponse.ok();
        } else {
            return HttpResponse.badRequest();
        }
    }

    @Post("batch/enable")
    public HttpResponse enableBatch(@Body Flag flag, @Header(name = "Auth-Key") String authKey) throws NoSuchAlgorithmException {
        if(!adminService.verifyAdminAuthKey(authKey))
            return HttpResponse.unauthorized();

        if(flag != null && flag.value) {
            taskScheduleTracker.enableBatch();
            return HttpResponse.ok();
        } else {
            return HttpResponse.badRequest();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Flag {
        private boolean value;
    }
}
