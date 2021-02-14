package com.bloxbean.oan.dashboard.staking.rewards.service;

import com.bloxbean.oan.dashboard.core.service.ChainService;
import io.micronaut.core.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.staking.exception.PoolControllerException;
import com.bloxbean.oan.dashboard.model.MyRewards;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
public class RewardHistoryService {
    private long DAILY_BLOCK_COUNTS = 8640;

    @Inject
    private RewardsService rewardsService;

    @Inject
    private ChainService chainService;

    public MyRewardHistories getMyRewardsHistories(String delegator, String[] pools, Long latestBlock, int fromDay, int toDay) {
        if(fromDay - toDay > 10)
            throw new IllegalArgumentException("Day range can not be more than 10 days");

        if(StringUtils.isEmpty(delegator))
            throw new PoolControllerException("Invalid delegator address");

        if(pools == null || pools.length == 0)
            throw new PoolControllerException("No pool address specified");

        List<MyRewardsHistory> myRewardsHistoriesList = new ArrayList<>();

        long blockHeight = latestBlock.longValue();
        blockHeight = blockHeight - toDay * DAILY_BLOCK_COUNTS;
        for(int i=toDay; i < fromDay; i++) {
            if(i != 0)
                blockHeight = blockHeight - DAILY_BLOCK_COUNTS;

            try {
                MyRewards myRewards = rewardsService.getMyRewards(delegator, pools, String.valueOf(blockHeight));
                MyRewardsHistory myRewardsHistory = MyRewardsHistory.builder()
                        .myRewards(myRewards)
                        .day(i)
                        .blockHeight(blockHeight)
                        .build();

                myRewardsHistoriesList.add(myRewardsHistory);
            } catch (Exception e) {
                log.error("Error getting myrewards ", e);
            }
        }

        boolean earliest = blockHeight <= 4721900? true: false;

        MyRewardHistories myRewardHistories = new MyRewardHistories();
        myRewardHistories.setHistories(myRewardsHistoriesList);
        myRewardHistories.setEarliest(earliest);

        return myRewardHistories;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MyRewardsHistory {
        private int day;
        private long blockHeight;

        MyRewards myRewards;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MyRewardHistories {
        private boolean earliest;
        private List<MyRewardsHistory> histories;
    }
}


