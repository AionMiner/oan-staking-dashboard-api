package com.bloxbean.oan.dashboard.staking.stats.controller;

import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.staking.activities.model.Delegator;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorPools;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorsResult;
import com.bloxbean.oan.dashboard.staking.activities.model.PoolDelegators;
import com.bloxbean.oan.dashboard.staking.stats.service.StakedAccountsScheduleService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

@Controller("/staking-stats")
public class StakedAccountsScheduleController {
    private Logger logger = LoggerFactory.getLogger(StakedAccountsScheduleController.class);

    @Inject
    private StakedAccountsScheduleService stakedAccountsScheduleService;

    @Get(uri = "/last-delegation-read-block-no", produces = MediaType.APPLICATION_JSON)
    public String getLastDelegationReadBlockNumber() {
        BigInteger lastReadBlockNo = stakedAccountsScheduleService.getInitialDelegationEventReadBlockNo();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("last-read-block-no", lastReadBlockNo);

        return jsonObject.toString();
    }

    @Get(uri = "/last-stakers-read-block-no", produces = MediaType.APPLICATION_JSON)
    public String getLastStakersReadBlockNumber() {
        BigInteger lastReadBlockNo = stakedAccountsScheduleService.getInitialDelegationEventReadBlockNo();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("last-read-block-no", lastReadBlockNo);

        return jsonObject.toString();
    }

    @Get(uri = "/status", produces = MediaType.APPLICATION_JSON)
    public String status() {
        String delegationEventsLogTime = stakedAccountsScheduleService.getDelegationEventLogLastUpdatedTime();
        String stakesEventsLogTime = stakedAccountsScheduleService.getStakesEventLogLastUpdatedTime();
        String topDelegatorTime = stakedAccountsScheduleService.getTopDelegatorsLastUpdatedTime();

        BigInteger lastDelegationReadBlockNo = stakedAccountsScheduleService.getInitialDelegationEventReadBlockNo();
        BigInteger lastStakesReadBlockNo = stakedAccountsScheduleService.getInitialStakersEventReadBlockNo();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("last-poolregistry-events-lastupdated-time", delegationEventsLogTime);
        jsonObject.put("last-stakeregistry-events-lastupdated-time", stakesEventsLogTime);
        jsonObject.put("top-delegators-lastupdated-time", topDelegatorTime);
        jsonObject.put("last-poolregistry-events-read-block-no", lastDelegationReadBlockNo);
        jsonObject.put("last-stakeregistry-events-read-block-no", lastStakesReadBlockNo);

        return jsonObject.toString();
    }

    @Get(uri = "/validators", produces = MediaType.APPLICATION_JSON)
    public Set<String> getValidators() {
        return stakedAccountsScheduleService.getValidators();
    }

    @Get(uri = "/validators/{validator}/delegators", produces = MediaType.APPLICATION_JSON)
    public PoolDelegators getTopDelegators(@PathVariable String validator, @QueryValue long start, @QueryValue long end) {
        if(end == 0)
            end = 10; //default 10

        return stakedAccountsScheduleService.getTopDelegatorsForValidator(validator, start, end);
    }

    @Get(uri = "/top-delegators", produces = MediaType.APPLICATION_JSON)
    public List<Delegator> getTopDelegators() {
        return stakedAccountsScheduleService.getTopDelegators(0, 50);
    }

    @Get(uri = "/delegators/{delegator}/pools", produces = MediaType.APPLICATION_JSON)
    public DelegatorPools getStakesForDelegator(@PathVariable String delegator, @Nullable @QueryValue Boolean ignoreLogoData) {
        if(ignoreLogoData == null)
            ignoreLogoData = false;
        return stakedAccountsScheduleService.getAllStakesOfADelegator(delegator, ignoreLogoData);
    }

    @Get(uri = "/delegators", produces = MediaType.APPLICATION_JSON)
    public DelegatorsResult getAllDelegators(@QueryValue String cursor, @QueryValue boolean isFinished) {
        return stakedAccountsScheduleService.getDelegators(cursor, isFinished);
    }

    //staker endpoints
    @Get(uri = "/stakers", produces = MediaType.APPLICATION_JSON)
    public List<Staker> getStakers(@QueryValue long start, @QueryValue long end, @Nullable @QueryValue Boolean poolInfo, @Nullable @QueryValue Boolean ignoreLogoData) {
        if(poolInfo == null)
            poolInfo = false;

        if(ignoreLogoData == null)
            ignoreLogoData = false;

        return stakedAccountsScheduleService.getStakers(start, end, poolInfo, ignoreLogoData);
    }

    @Get(uri = "/stakers/solo", produces = MediaType.APPLICATION_JSON)
    public List<Staker> getSoloStakers(@QueryValue long start, @QueryValue long end) {
        return stakedAccountsScheduleService.getSoloStakers(start, end);
    }

    @Get(uri = "/stakers/{identity}", produces = MediaType.APPLICATION_JSON)
    public Staker getStakers(@PathVariable String identity, @Nullable @QueryValue Boolean ignoreLogoData) {
        Staker staker = stakedAccountsScheduleService.getStaker(identity);

        if(staker.getPoolMetaData() != null && ignoreLogoData != null && ignoreLogoData) {
            staker.getPoolMetaData().setLogo(null);
        }

        return staker;
    }

    //TODO - Use just for testing
    //Don't use the following api in UI directly
    @Get(uri = "/fetch-event-logs", produces = MediaType.APPLICATION_JSON)
    public String fetchDelegatedEvents() {

        stakedAccountsScheduleService.fetchEventLogsForDelegationAndUndelegation();
        stakedAccountsScheduleService.fetchEventLogsForSoloStaking();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("status", "success");

        return jsonObject.toString();
    }

    @Get(uri = "/build-top-delegators", produces = MediaType.APPLICATION_JSON)
    public String buildTopDelegators() {
        stakedAccountsScheduleService.populateTopDelegators();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("status", "success");

        return jsonObject.toString();
    }

    @Get(uri = "/fetch-withdrawal-logs", produces = MediaType.APPLICATION_JSON)
    public String fetchWithdrawalLogs() {

        stakedAccountsScheduleService.fetchWithdrawRewardsEvents();

        JSONObject jsonObject= new JSONObject();
        jsonObject.put("status", "success");

        return jsonObject.toString();
    }
}
