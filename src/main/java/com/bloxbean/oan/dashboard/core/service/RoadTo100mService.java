package com.bloxbean.oan.dashboard.core.service;

import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.bloxbean.oan.dashboard.model.Block;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class RoadTo100mService {
    private final static Logger logger = LoggerFactory.getLogger(RoadTo100mService.class);


    @Inject
    private PoolRegistryService poolRegistryService;

    @Inject
    private PoolRedisService poolRedisService;

    @Inject
    StatefulRedisConnection<String, String> connection;

    @Inject
    private ChainService chainService;

    public String get100MStakes() {
        String key = "100m-stream";

        String content = connection.sync().get(key);
        if(StringUtils.isEmpty(content)) {
            long latestBlock = 5465994;//chainService.getLatestBlock().longValue();
            long from = 4591124;

            List<Pool> pools = poolRedisService.getPools();

            List<Stakes> stakes = new ArrayList();
            for (long i = from; i < latestBlock; i = i + 8640) {
                BigInteger totalStake = poolRegistryService.getTotalStake(i + "");
                Block block = chainService.getBlock(i + "");

                BigDecimal totalStakeAion = CryptoUtil.ampToAion(totalStake);

                Stakes st = Stakes.builder()
                        .stakeAmtInAion(Math.round(totalStakeAion.doubleValue()))
                        .timestamp(block.getTimestampInMillis()).build();

                st.setPoolStakeMap(new HashMap<>());

                long counter = 0;
                for(Pool pool: pools) {
                    try {
                        BigInteger poolStake = poolRegistryService.getTotalStake(pool.getValidatorAddress(), i + "")[0];
                        if (poolStake != null && poolStake.longValue() > 0L) {
                            String poolName = pool.getPoolMetaData() != null ? pool.getPoolMetaData().getName() : pool.getValidatorAddress();
                            if (StringUtils.isEmpty(poolName))
                                poolName = pool.getValidatorAddress();

                            BigDecimal poolStakeInAion = CryptoUtil.ampToAion(poolStake);

                            st.getPoolStakeMap().put(poolName, Math.round(poolStakeInAion.doubleValue()));
                            counter ++;
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }

                System.out.println("Data succesfully fetched for # " + counter);
                stakes.add(st);
            }

            connection.sync().set(key, JsonUtil.toJson(stakes));
            return connection.sync().get(key);
        } else
            return content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class Stakes {
        private double stakeAmtInAion;
        private long timestamp;
        private Map<String, Long> poolStakeMap = new HashMap<>();
    }

}
