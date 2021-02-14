package com.bloxbean.oan.dashboard.staking.rating.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.staking.rating.model.PoolAvgRating;
import com.bloxbean.oan.dashboard.staking.rating.model.Rating;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingKeyBuilder;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingUpdateTimeHelper;
import com.bloxbean.oan.dashboard.util.DateUtil;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.bloxbean.oan.dashboard.util.Tuple;
import com.bloxbean.oan.dashboard.util.Tuple3;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

//Aggregator objects are validator specific
@Slf4j
public class PerfRatingAggregatorProcessor implements BlockProcessor {
    private final static String VALIDATOR_RATING_KEY_SUFFIX = ".ratings";

    private String validator;

    private String durationSuffix = "";

    private StatefulRedisConnection<String, String> connection;

    private RatingUpdateTimeHelper ratingUpdateTimeHelper;
    private RatingKeyBuilder ratingKeyBuilder;

    public PerfRatingAggregatorProcessor(String validator, StatefulRedisConnection<String, String> connection,
                                         RatingUpdateTimeHelper ratingUpdateTimeHelper,
                                         RatingKeyBuilder ratingKeyBuilder) {
        this.validator = validator;
        this.connection = connection;
        this.ratingUpdateTimeHelper = ratingUpdateTimeHelper;
        this.ratingKeyBuilder = ratingKeyBuilder;
    }

    public void setDurationSuffix(String durationSuffix) {
        this.durationSuffix = durationSuffix;
    }

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {

        log.info("Processing {} aggregated rating for {} from block {} {}", durationSuffix, validator, fromBlock, toBlock);

        Rating rating = aggregateRating(validator, fromBlock.longValue(), toBlock.longValue());

        connection.sync().zadd(ratingKeyBuilder.getValidatorRatingsKeyBackup(validator, durationSuffix), toBlock.floatValue(), JsonUtil.toJson(rating));

    }

    public Rating aggregateRating(String validator, long fromBlock, long toBlock) {

        String validatorRatingHourlyKey = ratingKeyBuilder.getValidatorRatingHourlyKey(validator);
        List<String> hourlyRatings = connection.sync().zrange(validatorRatingHourlyKey, fromBlock, toBlock);

        double totalRating = 0;
        double totalBlockWeight = 0;
        double totalStakeWeight = 0;
        long totalBlocks = 0;
        long totalPoolBlocks = 0;

//        List<Rating> children = new ArrayList<>(); //TODO REMOVE LATER
        boolean estimatedStakeWeight = false;
        for (String hourlyRating : hourlyRatings) {
            Rating hourlyRatingObj = JsonUtil.fromJson(hourlyRating, Rating.class);
            double hourlRatingDouble = hourlyRatingObj.getRating();
            if (!Double.isNaN(hourlRatingDouble)) {
                totalRating += hourlRatingDouble;
            }

            if (!Double.isNaN(hourlyRatingObj.getBlockWeight())) {
                totalBlockWeight += hourlyRatingObj.getBlockWeight();
            }

            if (!Double.isNaN(hourlyRatingObj.getStakeWeight())) {
                totalStakeWeight += hourlyRatingObj.getStakeWeight();
            }

            totalBlocks += hourlyRatingObj.getTotalBlocks();
            totalPoolBlocks += hourlyRatingObj.getPoolBlocks();

            if(!estimatedStakeWeight && hourlyRatingObj.isEstimatedStakeWeight()) //If any of the entry is estimated stake weight, just indicate
                estimatedStakeWeight = true;
//            children.add(hourlyRatingObj);
        }

        //TODO remove later
        double weightedAvgRating = (totalBlockWeight / totalStakeWeight) * 100;

        //END

        double avgRating = totalRating / hourlyRatings.size();
        double avgBlockWeight = totalBlockWeight / hourlyRatings.size();
        double avgStakeWeight = totalStakeWeight / hourlyRatings.size();

        Rating topRatingBlock = null;
        if (hourlyRatings != null && hourlyRatings.size() > 0)
            topRatingBlock = JsonUtil.fromJson(hourlyRatings.get(hourlyRatings.size() - 1), Rating.class);

        Rating ratingObj = Rating.builder()
                .rating(avgRating)
                .blockWeight(avgBlockWeight)
                .stakeWeight(avgStakeWeight)
                .blockNumber(topRatingBlock.getBlockNumber())
                .timestamp(topRatingBlock != null ? topRatingBlock.getTimestamp() : 0)
                .totalBlocks(totalBlocks)
                .poolBlocks(totalPoolBlocks)
//                .children(children)
                .weightedAvgRating(weightedAvgRating)
                .estimatedStakeWeight(estimatedStakeWeight)
                .build();

        if(log.isDebugEnabled())
            log.debug("Aggregated Rating : " +  ratingObj);
        return ratingObj;
    }

    public void begin() {

    }

    public void commit() {
        String lastUpdatedTime = DateUtil.currentTimeInGMT();
        try {
            Tuple3<Long, Long, Double> avgRating = calculateAvgRatingForDurationInBackupKey();
            PoolAvgRating poolAvgRating = PoolAvgRating.builder()
                    .validator(validator)
                    .avgRating(avgRating._3)
                    .fromBlock(avgRating._1)
                    .toBlock(avgRating._2)
                    .lastUpdatedTime(lastUpdatedTime)
                    .build();

            connection.sync().hset(ratingKeyBuilder.getAvgBucketKeyForDuration(durationSuffix), validator, JsonUtil.toJson(poolAvgRating));
        } catch (Exception e) {
            log.error("Error updating avarage rating for {} for duation {}", validator, durationSuffix);
            log.error("Error updating avg rating", e);
        }

        connection.sync().rename(ratingKeyBuilder.getValidatorRatingsKeyBackup(validator, durationSuffix), ratingKeyBuilder.getValidatorRatingsKey(validator, durationSuffix));
        ratingUpdateTimeHelper.updateRatingUpdateTime(validator, durationSuffix, lastUpdatedTime);
    }

    private Tuple3<Long, Long, Double> calculateAvgRatingForDurationInBackupKey() {
        //Calculate avarage
        List<String> ratingEntries = connection.sync().zrevrange(ratingKeyBuilder.getValidatorRatingsKeyBackup(validator, durationSuffix), 0, -1);
        List<Rating> ratings = null;
        if(ratingEntries == null) ratings = Collections.emptyList();

        ratings = ratingEntries.stream().map( str -> JsonUtil.fromJson(str, Rating.class)).collect(Collectors.toList());

        double avgRating = 0.0;
        long fromBlock =0;
        long toBlock = 0;
        if(ratings != null && ratings.size() > 0) {
            toBlock = ratings.get(0).getBlockNumber();
            fromBlock = ratings.get(ratings.size()-1).getBlockNumber();

            for (Rating rating : ratings) {
                avgRating += rating.getRating();
            }
            avgRating = avgRating / ratings.size();
        }

        return new Tuple3<>(fromBlock, toBlock, avgRating);
    }

}
