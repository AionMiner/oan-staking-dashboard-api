package com.bloxbean.oan.dashboard.staking.rewards.service;

import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.model.MyRewards;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RewardsService {
    private Logger logger = LoggerFactory.getLogger(RewardsService.class);

    @Inject
    private PoolRegistryService poolRegistryService;

    public MyRewards getMyRewards(String address, String blockHeight) {
        if(address == null || address.isEmpty())
            return new MyRewards();

        List<Pool> pools = poolRegistryService.getPools();
        MyRewards myRewards = new MyRewards();

        for(Pool pool: pools) {
            BigInteger stake = poolRegistryService.getStake(pool.getValidatorAddress(), address, blockHeight);
            BigInteger rewards = poolRegistryService.getRewards(pool.getValidatorAddress(), address, blockHeight);

            myRewards.setAddress(address);
            MyRewards.PoolStake poolStake = new MyRewards.PoolStake();
            poolStake.setValidator(pool.getValidatorAddress());
            poolStake.setStake(stake);
            poolStake.setRewards(rewards);

            myRewards.getPoolStakes().add(poolStake);
        }

        return myRewards;
    }

    public MyRewards getMyRewards(String address, String[] pools, String blockHeight) {
        if(address == null || address.isEmpty())
            return new MyRewards();

        MyRewards myRewards = new MyRewards();
        myRewards.setAddress(address);

        List<CompletableFuture<MyRewards.PoolStake>> futures = new ArrayList<>();
        for(String pool: pools) {
            CompletableFuture<MyRewards.PoolStake> cf
                    = CompletableFuture.supplyAsync(() -> {
                BigInteger stake = poolRegistryService.getStake(pool, address, blockHeight);
                BigInteger rewards = poolRegistryService.getRewards(pool, address, blockHeight);

                MyRewards.PoolStake poolStake = new MyRewards.PoolStake();
                poolStake.setValidator(pool);
                poolStake.setStake(stake);
                poolStake.setRewards(rewards);
                poolStake.setStakeAion(CryptoUtil.ampToAion(stake));
                poolStake.setRewardsAion(CryptoUtil.ampToAion(rewards));

                return poolStake;
            });

            futures.add(cf);
        }

        futures.forEach( future -> {
            try {
                MyRewards.PoolStake poolStake = future.get();
                myRewards.getPoolStakes().add(poolStake);
            } catch (InterruptedException e) {
                logger.error("Error getting result for pool stake", e);
            } catch (ExecutionException e) {
                logger.error("Error getting result for pool stake", e);
            }
        });

        return myRewards;
    }
}
