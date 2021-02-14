package com.bloxbean.oan.dashboard.staking.rating.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlock;
import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.staking.rating.model.Rating;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingKeyBuilder;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingUpdateTimeHelper;
import com.bloxbean.oan.dashboard.util.DateUtil;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import org.aion4j.avm.helper.util.CryptoUtil;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PerfRatingProcessor implements BlockProcessor {
    private final static String POS_BLOCKS_KEY = "pos_blocks";
    private final static String POS_BLOCK_KEY_SUFFIX = ".pos_blocks";
    private final static String VALIDATOR_RATING_KEY_SUFFIX = ".ratings";

    private String durationSuffix = "";

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private RatingUpdateTimeHelper ratingUpdateTimeHelper;

    @Inject
    private RatingKeyBuilder ratingKeyBuilder;

    @Inject
    private PoolRegistryService poolRegistryService;

    public void setDurationSuffix(String durationSuffix) {
        this.durationSuffix = durationSuffix;
    }

    private List<Staker> pools;
    private Map<String, Double> avgDefaultStakeWeightMap = new HashMap<>();

    public void setPools(List<Staker> pools) {
        if(pools != null)
            this.pools = pools;
        else
            this.pools = new ArrayList<>();
    }

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
        if(pools == null) return;

        for(Staker pool: pools) {
            Rating rating = calculateRating(pool, fromBlock.longValue(), toBlock.longValue());
            connection.sync().zadd(ratingKeyBuilder.getValidatorRatingsKeyBackup(pool.getIdentity(), durationSuffix), toBlock.floatValue(), JsonUtil.toJson(rating));
        }
    }

    public Rating calculateRating(Staker pool, long fromBlock, long toBlock) {

        String coinbaseAddress = pool.getCoinbaseAddress();

        String coinbasePOSBlockKey = ratingKeyBuilder.getPOSBlockKeyForCoinbaseAddress(coinbaseAddress);

        if(log.isDebugEnabled())
            log.debug("RANGE >>>>>>>>>>>>>>>>>>>> " + fromBlock + " : " + toBlock);

        Range<Long> range = Range.create(fromBlock, toBlock);

        if(log.isDebugEnabled()) {
           log.debug("Validator block count for coinbbase key {} ", coinbasePOSBlockKey);
        }

        long noOfValidatorBlocks = connection.sync().zcount(coinbasePOSBlockKey, range);
        long totalBlock = connection.sync().zcount(POS_BLOCKS_KEY, range);

        log.info("Total blocks :  {}, Blocks for coinbaseAddress {} identity {} : {}", totalBlock, coinbaseAddress, pool.getIdentity(), noOfValidatorBlocks);

        double blockWeight = noOfValidatorBlocks / (double)totalBlock;
        if(log.isDebugEnabled())
            log.info("Block weight : {}", blockWeight);

        List<String> posBlocks = connection.sync().zrangebyscore(coinbasePOSBlockKey, range);

        double stakeWeight = 0;
        for(String posBlockStr: posBlocks) {
            POSBlock posBlock = JsonUtil.fromJson(posBlockStr, POSBlock.class);
            stakeWeight += posBlock.getStakeWeight();
        }

        stakeWeight = (stakeWeight / posBlocks.size()) / 100;

        boolean estimatedStakeWeight = false;
        if(Double.isNaN(stakeWeight)) { //If stakeweight is zero due to non block production in this range.. the avg calculation doesn't match.
            Double avgStakeWeightForDuration = avgDefaultStakeWeightMap.get(pool.getIdentity());
            if(avgStakeWeightForDuration != null)
                stakeWeight = avgStakeWeightForDuration;
            estimatedStakeWeight = true;
        } else { //update last known stake weight, so that it can be used incase of missing stake weight.
            avgDefaultStakeWeightMap.put(pool.getIdentity(), stakeWeight);
        }

        if(log.isDebugEnabled())
            log.debug("Avg stake weight {}", stakeWeight);

        double rating = (blockWeight / stakeWeight) * 100; //%

        if(Double.isNaN(rating))
            rating = 0.0;

        if(Double.isNaN(stakeWeight))
            stakeWeight = 0.0;

        Limit firstElement = Limit.create(0, 1);
        List<String> topBlocks = connection.sync().zrevrangebyscore(POS_BLOCKS_KEY, Range.create(fromBlock, toBlock), firstElement);
        POSBlock topBlock = null;
        if(topBlocks != null && topBlocks.size() > 0)
            topBlock = JsonUtil.fromJson(topBlocks.get(0), POSBlock.class);

        Rating ratingObj = Rating.builder()
                .rating(rating)
                .blockWeight(blockWeight)
                .stakeWeight(stakeWeight)
                .blockNumber(toBlock)
                .timestamp(topBlock != null? topBlock.getTimestamp(): 0)
                .totalBlocks(totalBlock)
                .poolBlocks(noOfValidatorBlocks)
                .estimatedStakeWeight(estimatedStakeWeight)
                .build();

        return ratingObj;
    }

    public void begin(BigInteger firstBlock, BigInteger lastBlock) {

        //Let's find the avarage stake weight for this duration and set it in stakeweight incase there is no block for the duration and stake weight is not
        //catpured. It's just a workaround,
        for(Staker pool: pools) {
            try {
                String validator = pool.getIdentity();

                double startStakeWeight = calculateStakeWeightAtBlockForValidator(validator, firstBlock.longValue());
                double endStakeWeight = calculateStakeWeightAtBlockForValidator(validator, lastBlock.longValue());

                double avgStakeWeight = (startStakeWeight + endStakeWeight) / 2;
                avgDefaultStakeWeightMap.put(validator, avgStakeWeight);
            } catch (Exception e) {
                log.warn("Error calculating avgStakeWeight for missing stakeweights", e);
            }
        }

    }

    private double calculateStakeWeightAtBlockForValidator(String validatorAddress, long block) {
        BigInteger[] validatorTotalStakes = poolRegistryService.getTotalStake(validatorAddress, String.valueOf(block));
        BigInteger totalStake = poolRegistryService.getTotalStake(String.valueOf(block));

        double stakeWeight = 0.0;
        if (validatorTotalStakes != null && validatorTotalStakes.length > 0) {
            BigDecimal valStakeAmtInAion = CryptoUtil.ampToAion(validatorTotalStakes[0]);
            BigDecimal totalStakeAmtInAion = CryptoUtil.ampToAion(totalStake);
            stakeWeight = (valStakeAmtInAion.doubleValue() / totalStakeAmtInAion.doubleValue());
        }
        return stakeWeight;
    }

    public void commit() {
        try {
            if (pools != null && pools.size() > 0) {
                for (Staker pool : pools) {
                    //rename backup to main
                    connection.sync().rename(ratingKeyBuilder.getValidatorRatingsKeyBackup(pool.getIdentity(), durationSuffix),
                            ratingKeyBuilder.getValidatorRatingsKey(pool.getIdentity(), durationSuffix));

                    ratingUpdateTimeHelper.updateRatingUpdateTime(pool.getIdentity(), "1h", DateUtil.currentTimeInGMT());
                }
            }
        } finally {
            avgDefaultStakeWeightMap.clear();
        }
    }
}
