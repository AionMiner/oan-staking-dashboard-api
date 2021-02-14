package com.bloxbean.oan.dashboard.staking.pools.controller;

import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.model.PoolMetaData;
import com.bloxbean.oan.dashboard.core.service.ChainService;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.core.service.RoadTo100mService;
import com.bloxbean.oan.dashboard.staking.rewards.service.RewardsService;
import com.bloxbean.oan.dashboard.util.Tuple;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller("/pools")
public class PoolController {
    private static final Logger logger = LoggerFactory.getLogger(PoolController.class);

    @Inject
    private PoolRegistryService poolRegistryService;

    @Inject
    private PoolRedisService redisService;

    @Inject
    private RewardsService rewardsService;

    @Inject
    private ChainService chainService;

    @Inject
    private RoadTo100mService roadTo100mService;

    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<List<Pool>> getPools(@Nullable @QueryValue Boolean ignoreLogoData) {
        List<Pool> pools = redisService.getPools();

        String lastUpdatedTime = redisService.getLastUpdatedTimeForPoolInfo();

        if(pools == null || pools.size() == 0)
            pools = poolRegistryService.getPools();

        if(ignoreLogoData != null && ignoreLogoData) {
            pools.forEach(pool -> {
                if (pool.getPoolMetaData() != null)
                    pool.getPoolMetaData().setLogo(null);
            });
        }

        if(!StringUtils.isEmpty(lastUpdatedTime)) {
            return HttpResponse.ok(pools)
                    .header("Last-Modified", lastUpdatedTime);
        } else {
            return HttpResponse.ok(pools);
        }
    }

    @Get(uri = "/metadata", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, PoolMetaData>> getPoolsMetaData() {
        Map<String, PoolMetaData> poolsMetaData = redisService.getPoolsMetaData();
        String lastUpdatedTime = redisService.getLastUpdatedTimeForPoolsMetaData();

        if(poolsMetaData == null || poolsMetaData.size() == 0)
            poolsMetaData = Collections.EMPTY_MAP;

        return HttpResponse.ok(poolsMetaData)
                .header("Last-Modified", lastUpdatedTime);
    }

    @Get(uri = "/{poolAddress}", produces = MediaType.APPLICATION_JSON)
    public Pool getPool(@PathVariable("poolAddress") String address, @Nullable @QueryValue Boolean ignoreLogoData) {
        Pool pool = redisService.getPool(address);

        if(pool.getPoolMetaData() != null && ignoreLogoData != null && ignoreLogoData) {
            pool.getPoolMetaData().setLogo(null);
        }

        return pool;
    }

    @Get(uri = "/{poolAddress}/totalStake", produces = MediaType.APPLICATION_JSON)
    public String getTotalStake(@PathVariable("poolAddress") String address, @QueryValue @Nullable String blockHeight) {
        BigInteger[] stakes = poolRegistryService.getTotalStake(address, blockHeight);
        JSONObject result = new JSONObject();

        result.put("pool", address);
        result.put("totalStake", stakes[0]);
        result.put("totalStakeAion", CryptoUtil.ampToAion(stakes[0]));
        result.put("pendingStake", stakes[1]);
        result.put("pendingStakeAion", CryptoUtil.ampToAion(stakes[1]));
        return result.toString();
    }

    @Get(uri = "/{poolAddress}/{delegator}/stake", produces = MediaType.APPLICATION_JSON)
    public String getStake(@PathVariable("poolAddress") String poolAddress, @PathVariable("delegator") String delegator, @QueryValue @Nullable String blockHeight) {
        BigInteger stake = poolRegistryService.getStake(poolAddress, delegator, blockHeight);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("pool", poolAddress);
        jsonObject.put("delegator", delegator);
        jsonObject.put("stake", stake);
        jsonObject.put("stakeAion", CryptoUtil.ampToAion(stake));
        return jsonObject.toString();
    }

    @Get(uri = "/{poolAddress}/{delegator}/rewards", produces = MediaType.APPLICATION_JSON)
    public String getRewards(@PathVariable("poolAddress") String poolAddress, @PathVariable("delegator") String delegator, @QueryValue @Nullable String blockHeight) {
        BigInteger rewards = poolRegistryService.getRewards(poolAddress, delegator, blockHeight);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("pool", poolAddress);
        jsonObject.put("delegator", delegator);
        jsonObject.put("rewards", rewards);
        jsonObject.put("rewardsAion", CryptoUtil.ampToAion(rewards));
        return jsonObject.toString();
    }

    @Get(uri = "/{poolAddress}/{delegator}", produces = MediaType.APPLICATION_JSON)
    public String getStakeAndRewards(@PathVariable("poolAddress") String poolAddress, @PathVariable("delegator") String delegator
            , @QueryValue @Nullable String blockHeight) {
        BigInteger stake = poolRegistryService.getStake(poolAddress, delegator, blockHeight);
        BigInteger rewards = poolRegistryService.getRewards(poolAddress, delegator, blockHeight);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("pool", poolAddress);
        jsonObject.put("delegator", delegator);
        jsonObject.put("stake", stake);
        jsonObject.put("stakeAion", CryptoUtil.ampToAion(stake));
        jsonObject.put("rewards", rewards);
        jsonObject.put("rewardsAion", CryptoUtil.ampToAion(rewards));
        return jsonObject.toString();
    }

    @Get(uri="/totalStake", processes = MediaType.APPLICATION_JSON)
    public String getTotalStake(@QueryValue @Nullable String blockHeight) {
        BigInteger totalStake = poolRegistryService.getTotalStake(blockHeight);
        BigInteger latestBlock = chainService.getLatestBlock();

        BigDecimal totalStakeAion = CryptoUtil.ampToAion(totalStake);

        JSONObject result = new JSONObject();
        result.put("totalStake", totalStake);
        result.put("totalStakeAion", totalStakeAion);

        if(blockHeight == null)
            result.put("blockNo", latestBlock);
        else
            result.put("blockNo", blockHeight);

        return result.toString();
    }

    @Get(uri="/logo/{validator}.img")
    public HttpResponse<byte[]> getPoolLogo(@PathVariable String validator) {
        Tuple<byte[], String> logo = redisService.getPoolLogo(validator);

        if(logo != null && logo._1 != null && logo._1.length != 0)
            return HttpResponse.ok(logo._1)
                    .contentType(logo._2)
                    .header("Cache-Control", "public, max-age=864000");
        else {
            logger.warn("Logo not found for validator : " + validator);
            return HttpResponse.notFound();
        }
    }

    @Get(uri="/road-to-100m", processes = MediaType.APPLICATION_JSON)
    public String getTotalStakeStreams() {
        return roadTo100mService.get100MStakes();
    }

    @Get(uri="/fetch-pool-info")
    public void runFetchPools() throws JsonProcessingException {
        redisService.putPoolsInfo();
    }
}
