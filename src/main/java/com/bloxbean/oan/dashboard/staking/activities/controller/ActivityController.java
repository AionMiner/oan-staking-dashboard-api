package com.bloxbean.oan.dashboard.staking.activities.controller;

import com.bloxbean.oan.dashboard.staking.activities.service.StakeActivityStreamService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.staking.activities.model.ActivitiesResponse;
import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigDecimal;

@Controller("/activities")
@Slf4j
public class ActivityController {

    @Inject
    private StakeActivityStreamService activityStreamService;

    @Get(uri = "/", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getRecentActivities(@QueryValue long start, @QueryValue long end) {
        return activityStreamService.getAllRecentActivities(start, end);
    }

    @Get(uri = "/{validatorAddress}", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getRecentActivities(@PathVariable String validatorAddress, @QueryValue long start, @QueryValue long end) {
        return activityStreamService.getAllRecentActivitiesForValidator(validatorAddress, start, end);
    }

    @Get(uri = "/{validatorAddress}/withdrawals", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getRecentWithdrawals(@PathVariable String validatorAddress, @QueryValue long start, @QueryValue long end) {
        return activityStreamService.getRecentWithdrawalsActivities(validatorAddress, start, end);
    }

    @Get(uri = "/{validatorAddress}/delegations", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getRecentDelegations(@PathVariable String validatorAddress, @QueryValue long start, @QueryValue long end) {
        return activityStreamService.getRecentDelegationActivities(validatorAddress, start, end);
    }
    @Get(uri = "/{validatorAddress}/undelegations", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getRecentUndelegations(@PathVariable String validatorAddress, @QueryValue long start, @QueryValue long end) {
        return activityStreamService.getRecentUnDelegationActivities(validatorAddress, start, end);
    }

    @Get(uri = "/pending-undelegations", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getPendingForFinalized(@Nullable @QueryValue Long start, @Nullable @QueryValue Long end) {
        if(start == null) start = 0L;
        if(end == null) end = 20L;
        return activityStreamService.getPendingUndelegationForFinalizeActivities(start, end);
    }

    @Get(uri = "/pending-undelegations/total", produces = MediaType.APPLICATION_JSON)
    public String getTotalAmtPendingForFinalized() {
        BigDecimal totalAmountBD = activityStreamService.getPendingUndelegationTotalAmount();
        if(totalAmountBD == null)
            totalAmountBD = BigDecimal.ZERO;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("totalAmtInAion", totalAmountBD.doubleValue());

        return jsonObject.toString();
    }

    @Get(uri = "/{delegator}/pending-undelegations", produces = MediaType.APPLICATION_JSON)
    public ActivitiesResponse getDelegatorPendingForFinalized(@PathVariable String delegator, @Nullable @QueryValue Long start, @Nullable @QueryValue Long end) {
        if(start == null) start = 0L;
        if(end == null) end = 20L;
        return activityStreamService.getDelegatorPendingUndelegationForFinalizeActivities(delegator, start, end);
    }
}
