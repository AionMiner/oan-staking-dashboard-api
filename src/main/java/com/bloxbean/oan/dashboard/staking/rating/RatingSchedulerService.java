package com.bloxbean.oan.dashboard.staking.rating;

import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import com.bloxbean.oan.dashboard.api.iterator.RevBlockIterator;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.client.StakedAccountSchedulerClient;
import com.bloxbean.oan.dashboard.staking.stats.StakingScheduleTimers;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlock;
import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.staking.rating.model.*;
import com.bloxbean.oan.dashboard.staking.rating.processor.PerfRatingAggregatorProcessor;
import com.bloxbean.oan.dashboard.staking.rating.processor.PerfRatingProcessor;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingKeyBuilder;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingUpdateTimeHelper;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.Range;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.aion4j.avm.helper.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class RatingSchedulerService {

    private final static String LAST_READ_RATING_SERVICE_BLOCK_NO_1h = "LAST_READ_RATING_SERVICE_BLOCK_NO_1h";

    private final static String LAST_READ_AGG_RATING_SERVICE_BLOCK = "LAST_READ_AGG_RATING_SERVICE_BLOCK";

    private static final String POS_BLOCKS_KEY = "pos_blocks";

    private final static long READ_BATCH_SIZE_1h = 360;
    private final static long ONEDAY_TOTAL_POS_BLOCKS = 3 * 60 * 24;
    private final static long ONEDAY_TOTAL_BLOCKS = 6 * 60 * 24;
//    private final static long READ_BATCH_SIZE_8h = 2880;//3 * 60; //estimated block per hour 180
//    private final static long READ_BATCH_SIZE_24h = 8640;
//    private final static long READ_BATCH_SIZE_7d = 60480;

    @Value("${batch.node}")
    private boolean isBatch;

    private RevBlockIterator perfRatingIterator1h;
    private PerfRatingProcessor perfRatingProcessor1h;

    //Scheduler controls
    private boolean ratingSchedulerRunning = false;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @Inject
    private RatingUpdateTimeHelper ratingUpdateTimeHelper;

    @Inject
    private PoolRedisService poolRedisService;

    @Inject
    private RatingKeyBuilder ratingKeyBuilder;

    @Inject
    private StakedAccountSchedulerClient stakedAccountSchedulerClient;

    @Inject
    public RatingSchedulerService(PerfRatingProcessor perfRatingProcessor1h) {
        this.perfRatingProcessor1h = perfRatingProcessor1h;
        this.perfRatingIterator1h = new RevBlockIterator(null, null);
        this.perfRatingIterator1h.setReadBlockBatchSize(READ_BATCH_SIZE_1h);
        this.perfRatingProcessor1h.setDurationSuffix("1h");
    }

    public void calculate() {

        if(!isBatch)
            return;

        if(taskScheduleTracker.isTaskRunning(StakingScheduleTimers.READ_VALIDATOR_BLOCKS_TIMER_TIMER)) {
            log.warn("Validation blocks timer is running. Rating calculation can not be called now.");
            throw new IllegalStateException("Validation blocks timer is running. Rating calculation can not be called now.");
        }

        String taskId = taskScheduleTracker.taskStarted(StakingScheduleTimers.RATING_CALCULATION_TIMER);
        long startTime = System.currentTimeMillis();

        try {
            resetCursor();

            List<Staker> stakers = stakedAccountSchedulerClient.getStakers(0, -1, false);
            stakers = stakers.stream().filter(p -> !p.isSolo()).collect(Collectors.toList()); //Only pools to avoid unncessary processing for now

            perfRatingProcessor1h.setPools(stakers);


            perfRatingProcessor1h.begin(perfRatingIterator1h.getFirstBlock(), perfRatingIterator1h.getLastBlock());

            try {
                calculateRating(perfRatingIterator1h, perfRatingProcessor1h);
            } catch (Exception e) {
                log.error("Rating calculation 1hr failed", e);
            }

            perfRatingProcessor1h.commit();

            //8h
            for(Staker staker: stakers) {
                connection.sync().del(LAST_READ_AGG_RATING_SERVICE_BLOCK); //Reset

                PerfRatingAggregatorProcessor perfRatingAggregatorProcessor = new PerfRatingAggregatorProcessor(staker.getIdentity(), connection,
                        ratingUpdateTimeHelper, ratingKeyBuilder );
                RevBlockIterator aggregatorIterator = new RevBlockIterator(BigInteger.valueOf(671), BigInteger.ZERO); //1h rating key should have 672 entries
                aggregatorIterator.setReadBlockBatchSize(8);
                perfRatingAggregatorProcessor.setDurationSuffix("8h");

                //Aggreate
                aggregatorIterator.iterate(perfRatingAggregatorProcessor);
                perfRatingAggregatorProcessor.commit();
            }

            //24hr
            for(Staker pool: stakers) {
                connection.sync().del(LAST_READ_AGG_RATING_SERVICE_BLOCK); //Reset

                PerfRatingAggregatorProcessor perfRatingAggregatorProcessor = new PerfRatingAggregatorProcessor(pool.getIdentity(), connection,
                        ratingUpdateTimeHelper, ratingKeyBuilder);
                RevBlockIterator aggregatorIterator = new RevBlockIterator(BigInteger.valueOf(671), BigInteger.ZERO); //1h rating key should have 672 entries
                aggregatorIterator.setReadBlockBatchSize(24);
                perfRatingAggregatorProcessor.setDurationSuffix("24h");

                //Aggreate
                aggregatorIterator.iterate(perfRatingAggregatorProcessor);
                perfRatingAggregatorProcessor.commit();
            }

            //7days - 168
            for(Staker pool: stakers) {
                connection.sync().del(LAST_READ_AGG_RATING_SERVICE_BLOCK); //Reset

                PerfRatingAggregatorProcessor perfRatingAggregatorProcessor = new PerfRatingAggregatorProcessor(pool.getIdentity(), connection,
                        ratingUpdateTimeHelper, ratingKeyBuilder);
                RevBlockIterator aggregatorIterator = new RevBlockIterator(BigInteger.valueOf(671), BigInteger.ZERO); //1h rating key should have 672 entries
                aggregatorIterator.setReadBlockBatchSize(168);
                perfRatingAggregatorProcessor.setDurationSuffix("7d");

                //Aggreate
                aggregatorIterator.iterate(perfRatingAggregatorProcessor);
                perfRatingAggregatorProcessor.commit();
            }

            //Prepare last 1 day 8640 total blks rating for all pools in one key
            prepareCurrentRatings();

        } finally {
            long endTime = System.currentTimeMillis();
            taskScheduleTracker.taskFinished(StakingScheduleTimers.RATING_CALCULATION_TIMER, taskId, (endTime - startTime));
            log.info("Rating: First block {}, Last block {}", perfRatingIterator1h.getFirstBlock(), perfRatingIterator1h.getLastBlock());
        }

    }

    private void prepareCurrentRatings() {
        List<Staker> stakers = stakedAccountSchedulerClient.getStakers(0, -1, false);

        if(stakers == null || stakers.size() == 0) {
            throw new IllegalStateException("Stakers list is empty");
        }

        String currentRatingKey = ratingKeyBuilder.getValidatorsCurrentRatingsKey();
        String curretRatingBackupKey = currentRatingKey + "_bk";

        //Only needed for solo staking aggregation
        BigInteger lastPOSBlockNo = getMaxBlockNoInPOSBlocks();
        long tMinusOneBlockNo = lastPOSBlockNo.longValue() - ONEDAY_TOTAL_BLOCKS; //Estimated no of POS block per day is 6 * 60 * 24

        Map<String, String> ratingsMap = new HashMap();
        for(Staker staker: stakers) {
            if(!staker.isSolo()) { //For pools
                try {
                    String rating = connection.sync().
                            zrange(ratingKeyBuilder.getValidatorRatingsKey(staker.getIdentity(), RatingKeyBuilder.DURATION_24h), -1, -1).get(0); //top element

                    if (rating == null || rating.isEmpty()) {
                        log.error("24h rating not found for the validator {}, pool: {}", staker.getIdentity(), !staker.isSolo());
                        throw new IllegalStateException("Rating not found for validator");
                    }

                    ratingsMap.put(staker.getIdentity(), rating);
                } catch (Exception e) {
                    log.error("Error getting last 1 day rating", e);
                }
            } else {
                try {
                    //For solo staker
                    String coinbase = staker.getCoinbaseAddress();
                    List<String> blocks = connection.sync().zrevrangebyscore(
                            ratingKeyBuilder.getPOSBlockKeyForCoinbaseAddress(coinbase), Range.create(tMinusOneBlockNo, lastPOSBlockNo.longValue()));

                    if(log.isDebugEnabled())
                        log.debug("Coin base address: " + coinbase +"  size: " + blocks.size());

                    if (blocks == null) {
                        //ratingsMap.put(staker.getIdentity(), toJson(new Rating()));
                        continue;
                    }

                    List<POSBlock> posBlocks = blocks.stream().map(ps -> JsonUtil.fromJson(ps, POSBlock.class)).collect(Collectors.toList());

                    double blockWeight = (double)posBlocks.size() / (double)ONEDAY_TOTAL_POS_BLOCKS; //Total POS blocks during this time is 4320

                    if(log.isDebugEnabled()) {
                        log.debug("Validator {} block weight: {} ", staker.getIdentity(), blockWeight);
                    }

                    double totalStakeWeight = 0.0;
                    //Calculate stake weight
                    for (POSBlock posBlock : posBlocks) {
                        totalStakeWeight += (posBlock.getStakeWeight() / 100.0); //divide by 100 as stake weight are stored in % during block processor
                    }

                    double avgStakeWeight = totalStakeWeight / (double)posBlocks.size();

                    double rating = (blockWeight / avgStakeWeight) * 100;

                    if(Double.isNaN(rating)) rating = 0; //error in rating calculation
                    if(Double.isNaN(avgStakeWeight)) avgStakeWeight = 0; //error in avg stakeweight calculation

                    Rating stakerRating = Rating.builder()
                            .rating(rating)
                            .blockNumber(lastPOSBlockNo.longValue())
                            .blockWeight(blockWeight)
                            .stakeWeight(avgStakeWeight)
                            .poolBlocks(posBlocks.size())
                            .totalBlocks(ONEDAY_TOTAL_POS_BLOCKS)
                            .build();

                    ratingsMap.put(staker.getIdentity(), JsonUtil.toJson(stakerRating));
                } catch (Exception e) {
                    log.error("Error crating current rating map for validator {} , isSolo: true", staker.getIdentity());
                }
            }

            connection.sync().hmset(curretRatingBackupKey, ratingsMap);
            connection.sync().rename(curretRatingBackupKey, currentRatingKey);
            ratingUpdateTimeHelper.updateCurrentRatingUpdateTime();
        }
    }

    public RatingReponse getRatingsForDuration(String validator, String duration, long start, long end) {
        String key = ratingKeyBuilder.getValidatorRatingsKey(validator, duration);
        List<String> list = connection.sync().zrevrange(key, start, end);
        long total = connection.sync().zcard(key);

        String lastupdatedTime = ratingUpdateTimeHelper.getRatingUpdateTime(validator, duration);

        List<Rating> ratings = null;
        if(list == null)
            ratings = Collections.emptyList();
        else
            ratings = list.stream().map( str -> JsonUtil.fromJson(str, Rating.class)).collect(Collectors.toList());

        double avgRating = 0;
        PoolAvgRating poolAvgRating = null;
        String avgStr = connection.sync().hget(ratingKeyBuilder.getAvgBucketKeyForDuration(duration), validator);
        if(!StringUtils.isEmpty(avgStr)) {
            poolAvgRating = JsonUtil.fromJson(avgStr, PoolAvgRating.class);
            avgRating = poolAvgRating.getAvgRating();
        }

//        if(ratings != null && ratings.size() > 0) {
//            for (Rating rating : ratings) {
//                avgRating += rating.getRating();
//            }
//            avgRating = avgRating / ratings.size();
//        }

        RatingReponse ratingReponse = RatingReponse.builder()
                .total(total)
                .lastUpdatedTime(lastupdatedTime)
                .ratings(ratings)
                .avgRating(avgRating)
                .build();

        return ratingReponse;
    }

    public Map<String, PoolAvgRating> getPoolsRatingForDuration(String duration) {
        Map<String, String> avgs = connection.sync().hgetall(ratingKeyBuilder.getAvgBucketKeyForDuration(duration));
        Map<String, PoolAvgRating> result = new HashMap<>();

        avgs.entrySet().stream()
                .forEach(entry -> {
                    result.put(entry.getKey(), JsonUtil.fromJson(entry.getValue(), PoolAvgRating.class));
                });

        return result;
    }

    public PoolAvgRating getPoolAvgRatingForDuration(String validator, String duration) {
        String retStr = connection.sync().hget(ratingKeyBuilder.getAvgBucketKeyForDuration(duration), validator);

        if(!StringUtils.isEmpty(retStr))
            return JsonUtil.fromJson(retStr, PoolAvgRating.class);
        else
            return new PoolAvgRating();
    }

    public CurrentRatings getValidatorCurrentRatings(long start, long end, boolean ignoreSolo) { //Ignore parameters for now
        Map<String, String> ratingsMap = connection.sync().hgetall(ratingKeyBuilder.getValidatorsCurrentRatingsKey());
        final Set<String> validators = stakedAccountSchedulerClient.getValidators();

        Map<String, ValidatorRating> validatorRatingMap = ratingsMap.entrySet().stream()
                .filter(entry -> {
                    if(ignoreSolo && validators != null)
                        return validators.contains(entry.getKey());
                    else
                        return true;
                }) //
                .map( entry -> {
            Rating rating = JsonUtil.fromJson(entry.getValue(), Rating.class);
            ValidatorRating validatorRating = ValidatorRating.builder()
                    .validator(entry.getKey())
                    .rating(rating)
                    .isSolo(validators != null ? !validators.contains(entry.getKey()): false)
                    .build();
            return validatorRating;
        }).collect(Collectors.toMap(validatorRating -> validatorRating.getValidator(), validatorRating ->  validatorRating));

        String  ludt =  ratingUpdateTimeHelper.getCurrentRatingUpdateTime();
        return CurrentRatings.builder()
                .lastUpdatedTime(ludt)
                .data(validatorRatingMap)
                .build();
    }

    public void deleteRatings() {
//        List<Staker> stakers = stakedAccountSchedulerClient.getStakers(0, -1, false);
//        for(Staker st: stakers) {
//            connection.sync().del(ratingKeyBuilder.getPOSBlockKeyForCoinbaseAddress(st.getCoinbaseAddress()))
//        }
    }

    private BigInteger getMaxBlockNoInPOSBlocks() {
        List<String> maxPosBlocks = connection.sync().zrange(POS_BLOCKS_KEY, -1, -1);
        if(maxPosBlocks == null || maxPosBlocks.size() == 0)
            return BigInteger.ZERO;
        else {
            POSBlock maxPosBlock = JsonUtil.fromJson(maxPosBlocks.get(0), POSBlock.class);
            return new BigInteger(maxPosBlock.getBlockNumber());
        }
    }

    private BigInteger getFirstBlockNoInPOSBlocks() {
        List<String> maxPosBlocks = connection.sync().zrange(POS_BLOCKS_KEY, 0, 0);
        if(maxPosBlocks == null || maxPosBlocks.size() == 0)
            return BigInteger.ZERO;
        else {
            POSBlock maxPosBlock = JsonUtil.fromJson(maxPosBlocks.get(0), POSBlock.class);
            return new BigInteger(maxPosBlock.getBlockNumber());
        }
    }

    private void resetCursor() {
        connection.sync().del(LAST_READ_RATING_SERVICE_BLOCK_NO_1h, LAST_READ_AGG_RATING_SERVICE_BLOCK);

        BigInteger firstPOSBlockNo = getFirstBlockNoInPOSBlocks();
        BigInteger lastPOSBlockNo = getMaxBlockNoInPOSBlocks();

        perfRatingIterator1h.setFirstBlock(firstPOSBlockNo);
        perfRatingIterator1h.setLastBlock(lastPOSBlockNo);

    }

    private void calculateRating(RevBlockIterator perfRatingIterator, PerfRatingProcessor perfRatingProcessor) {

        if(ratingSchedulerRunning) {//Already running
            log.info("Rating scheduler already running ...skipping this run...");
            return;
        }

        ratingSchedulerRunning = true;
        try {
            perfRatingIterator.iterate(perfRatingProcessor);
        } finally {
            ratingSchedulerRunning = false;
        }

    }
}
