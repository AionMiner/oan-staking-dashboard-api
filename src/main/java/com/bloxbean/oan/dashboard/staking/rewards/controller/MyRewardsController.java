package com.bloxbean.oan.dashboard.staking.rewards.controller;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import com.bloxbean.oan.dashboard.staking.exception.PoolControllerException;
import com.bloxbean.oan.dashboard.model.MyRewards;
import com.bloxbean.oan.dashboard.staking.rewards.service.RewardHistoryService;
import com.bloxbean.oan.dashboard.staking.rewards.service.RewardsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;

@Controller("/rewards")
public class MyRewardsController {
    private Logger logger = LoggerFactory.getLogger(MyRewardsController.class);

    @Inject
    private RewardsService rewardsService;

    @Inject
    private RewardHistoryService rewardHistoryService;

    @Get(uri = "/{address}", produces = MediaType.APPLICATION_JSON)
    public MyRewards getMyStakesAndRewards(@PathVariable("address") String delegator, @QueryValue("pools") String[] pools,
                                           @QueryValue @Nullable String blockHeight) {

        if(StringUtils.isEmpty(delegator))
            throw new PoolControllerException("Invalid delegator address");

        if(pools == null || pools.length == 0)
            throw new PoolControllerException("No pool address specified");

        return rewardsService.getMyRewards(delegator, pools, blockHeight);
    }

    @Get(uri = "/{address}/history", produces = MediaType.APPLICATION_JSON)
    public RewardHistoryService.MyRewardHistories getMyStakesAndRewardsForDayrange(@PathVariable("address") String delegator, @QueryValue("pools") String[] pools,
                                                                                   @QueryValue Long latestBlock, @QueryValue int fromDay, @QueryValue int toDay) {

        if(pools != null && pools.length > 5) {
            throw new IllegalArgumentException("Maximum upto 5 pools allowed in a single call");
        }

        return rewardHistoryService.getMyRewardsHistories(delegator, pools, latestBlock, fromDay, toDay);
    }

}
