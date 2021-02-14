package com.bloxbean.oan.dashboard.staking.rating;

import com.bloxbean.oan.dashboard.core.service.ChainService;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlock;
import com.bloxbean.oan.dashboard.staking.posblocks.processor.ValidatorBlockProcessor;
import com.bloxbean.oan.dashboard.staking.rating.model.CurrentRatings;
import com.bloxbean.oan.dashboard.staking.rating.model.PoolAvgRating;
import com.bloxbean.oan.dashboard.staking.rating.model.Rating;
import com.bloxbean.oan.dashboard.staking.rating.model.ValidatorRating;
import com.bloxbean.oan.dashboard.staking.rating.util.RatingKeyBuilder;
import com.bloxbean.oan.dashboard.util.DateUtil;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.core.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class RatingSnapshotService {

    private final static String LAST_DAILY_RATING_SNAPSHOT_TAKEN_AT ="LAST_DAILY_RATING_SNAPSHOT_TAKEN_AT";
    private final static long DAILY_MILLIS = 24 * 60 * 60 * 1000;

    private final static String LAST_MONTHLY_RATING_SNAPSHOT_TAKEN_AT ="LAST_MONTHLY_RATING_SNAPSHOT_TAKEN_AT";
    private final static long MONTHLY_SECONDS = 24 * 60 * 60 * 30; //seconds in a month of 30 days

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private ValidatorBlockProcessor validatorBlockProcessor;

    @Inject
    private RatingSchedulerService ratingSchedulerService;

    @Inject
    private RatingKeyBuilder ratingKeyBuilder;

    @Inject
    private ChainService chainService;

    //Temporary cache to avoid getting blocktime multiple times for the
    //same block from the kernel
    private Map<Long, Long> blockTimeCache = new HashMap<>();

    public void takeRatingSnapshot() {
        //Clear the cache
        blockTimeCache.clear();

        POSBlock posBlock = validatorBlockProcessor.getMaxBlockNoInPOSBlocks();
        if(posBlock == null) {
            log.warn("Last pos block is null. No snapshot will be taken. Make sure everything is ok.");
            return;
        }

        if(isDailyRatingSnapshotRequired(posBlock)) {
            //take snapshot
            _takeDailySnapshot(posBlock);

            if(posBlock != null) {
                log.info("Daily rating snapshot taken at currentTime: {}, blockTime {}, for posBlock {} ",
                        DateUtil.currentTimeInGMT(), posBlock.getTimestamp(), posBlock.getBlockNumber());
            }
        }
        if(isMonthlySnapshotRequired(posBlock)) {
            _takeMonthlySnapshot(posBlock); //Montly 24hr avg of 28 days.

            if(posBlock != null) {
                log.info("Monthly rating snapshot taken at currentTime: {}, blockTime {}, for posBlock {} ",
                        DateUtil.currentTimeInGMT(), posBlock.getTimestamp(), posBlock.getBlockNumber());
            }
        }

        //Clear blocktime cache
        blockTimeCache.clear();
    }

    public List<PoolAvgRating> getAvgRatingForValidatorForLastnMonths(String validator, int startMonth, int endMonth) {
        if(StringUtils.isEmpty(validator) || (endMonth - startMonth) > 12)
            return Collections.EMPTY_LIST;

        String key = ratingKeyBuilder.getValidatorMonthlyDailyAvgHistoryRatingsKey(validator);
        List<String> list = connection.sync().zrevrange(key, startMonth, endMonth);

        if(list != null)
            return list.stream().map(rs -> JsonUtil.fromJson(rs, PoolAvgRating.class)).collect(Collectors.toList());
        else
            return Collections.EMPTY_LIST;
    }

    public List<PoolAvgRating> getMonthlyRatingForAllValidators(int month) {
        if(month == 0)
            return Collections.EMPTY_LIST;

        String key = ratingKeyBuilder.getAllValidatorsMontlyAvgRatingBucket(month);

        Map<String, String> ratings = connection.sync().hgetall(key);

        List<PoolAvgRating> avgRatingsList = new ArrayList<>();
        if(ratings != null) {
            ratings.entrySet().stream().forEach(entry -> {
                try {
                    if (!StringUtils.isEmpty(entry.getValue())) {
                        avgRatingsList.add(JsonUtil.fromJson(entry.getValue(), PoolAvgRating.class));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            avgRatingsList.sort(new Comparator<PoolAvgRating>() {
                @Override
                public int compare(PoolAvgRating o1, PoolAvgRating o2) {
                    if(o1.getAvgRating() > o2.getAvgRating())
                        return -1;
                    else if(o1.getAvgRating() == o2.getAvgRating())
                        return 0;
                    else
                        return 1;
                }
            });

            return avgRatingsList;
        } else
            return Collections.EMPTY_LIST;
    }

    public List<Rating> getDailyRatingForLastnDays(String validator, int startDate, int endDate) {
        if(endDate - startDate > 180)
            return Collections.EMPTY_LIST;

        String key = ratingKeyBuilder.getValidatorDailyCurrentHistoryRatingsKey(validator);

        List<String> ratings = connection.sync().zrevrange(key, startDate, endDate);

        if(ratings != null)
            return ratings.stream().map(rs -> JsonUtil.fromJson(rs, Rating.class)).collect(Collectors.toList());
        else
            return Collections.EMPTY_LIST;
    }

    private void _takeMonthlySnapshot(POSBlock posBlock) {
        long timestamp = posBlock.getTimestamp();
        if(timestamp == 0) {
            log.warn("Last pos block timestamp is 0. No snapshot will be taken");
            return;
        }
        int month = millisToMonth(timestamp); //yyyyMM format
        _takeMonthlyDailyAvgSnapshot(posBlock, month);

        updateMonthlyRatingSnapshotTakenAt(timestamp);
    }

    private void _takeDailySnapshot(POSBlock posBlock) {
        long timestamp = posBlock.getTimestamp();
        if(timestamp == 0) {
            log.warn("Last pos block timestamp is 0. No snapshot will be taken");
            return;
        }
        int date = millisToDate(timestamp); //yyyyMMdd format

        _takeDailyAvgSnapshot(posBlock, date);
        _takeCurrentSnapshot(posBlock, date);
        updateDailyRatingSnapshotTakenAt(timestamp);
    }

    //24hr avg for last 28days
    private void _takeDailyAvgSnapshot(POSBlock posBlock, int date) {
        Map<String, PoolAvgRating> poolsAvgRatingMap
                = ratingSchedulerService.getPoolsRatingForDuration(RatingKeyBuilder.DURATION_24h);

        Set<String> poolKeys = poolsAvgRatingMap.keySet();
        for(String poolKey: poolKeys) {
            PoolAvgRating poolAvgRating = poolsAvgRatingMap.get(poolKey);
            if(poolAvgRating == null)
                continue;

            if(poolAvgRating.getToBlockTime() == 0)
                poolAvgRating.setToBlockTime(getBlockTime(poolAvgRating.getToBlock()));

            connection.sync().zadd(ratingKeyBuilder.getValidatorDailyAvgHistoryRatingsKey(poolKey), date, JsonUtil.toJson(poolAvgRating));
        }

        if(poolKeys.size() == 0) {
            log.info("No pools avg record found for daily snapshot");
        }
        log.info("Daily avg snapshot taken for {} at block ", date, posBlock);
    }

    //Current rating snapshot
    private void _takeCurrentSnapshot(POSBlock posBlock, int date) {
        CurrentRatings currentRatings = ratingSchedulerService.getValidatorCurrentRatings(0, -1, true);
        Map<String, ValidatorRating> currentRatingsMap = currentRatings.getData();

        Set<String> poolKeys = currentRatingsMap.keySet();
        for(String poolKey: poolKeys) {
            ValidatorRating poolRating = currentRatingsMap.get(poolKey);
            if(poolRating == null || poolRating.getRating() == null)
                continue;
            connection.sync().zadd(ratingKeyBuilder.getValidatorDailyCurrentHistoryRatingsKey(poolKey), date, JsonUtil.toJson(poolRating.getRating()));
        }

        if(poolKeys.size() == 0) {
            log.info("No pools avg record found for current snapshot");
        }

        log.info("Current snapshot taken for {} at block ", date, posBlock);
    }

    //24hr avg for last 28days - For monthly snapshot
    private void _takeMonthlyDailyAvgSnapshot(POSBlock posBlock, int date) {
        Map<String, PoolAvgRating> poolsAvgRatingMap
                = ratingSchedulerService.getPoolsRatingForDuration(RatingKeyBuilder.DURATION_24h);

        Set<String> poolKeys = poolsAvgRatingMap.keySet();
        for(String poolKey: poolKeys) {
            PoolAvgRating poolAvgRating = poolsAvgRatingMap.get(poolKey);
            if(poolAvgRating == null)
                continue;

            if(poolAvgRating.getToBlockTime() == 0)
                poolAvgRating.setToBlockTime(getBlockTime(poolAvgRating.getToBlock()));

            connection.sync().zadd(
                    ratingKeyBuilder.getValidatorMonthlyDailyAvgHistoryRatingsKey(poolKey), date, JsonUtil.toJson(poolAvgRating));

            //To all pool bucket
            connection.sync().hset(ratingKeyBuilder.getAllValidatorsMontlyAvgRatingBucket(date), poolKey, JsonUtil.toJson(poolAvgRating));
        }

        if(poolKeys.size() == 0) {
            log.info("No pools avg record found for monthly avg snapshot");
        }

        log.info("Montly daily avg snapshot taken for {} at block ", date, posBlock);
    }

    private boolean isDailyRatingSnapshotRequired(POSBlock currentPosBlock) {
        if(currentPosBlock == null || currentPosBlock.getTimestamp() == 0)
            return false;

        long lastSnapshotTakenAt = 0;
        try {
            lastSnapshotTakenAt = Long.parseLong(connection.sync().get(LAST_DAILY_RATING_SNAPSHOT_TAKEN_AT));
        } catch (Exception e) {
            log.warn("Last snapshot taken at parse failed.", e);
        }

        long delta = currentPosBlock.getTimestamp() - lastSnapshotTakenAt;
        if(delta >= DAILY_MILLIS)
            return true; //SNAPshot required.
        else
            return false;
    }

    private boolean isMonthlySnapshotRequired(POSBlock currentPosBlock) {
        if(currentPosBlock == null || currentPosBlock.getTimestamp() == 0)
            return false;

        long lastSnapshotTakenAt = 0;
        try {
            lastSnapshotTakenAt = Long.parseLong(connection.sync().get(LAST_MONTHLY_RATING_SNAPSHOT_TAKEN_AT));
        } catch (Exception e) {
            log.warn("Last snapshot taken at parse failed.", e);
        }

        long delta = (currentPosBlock.getTimestamp() - lastSnapshotTakenAt) / 1000; //In sec
        if(delta >= MONTHLY_SECONDS)
            return true; //SNAPshot required.
        else
            return false;
    }

    private void updateDailyRatingSnapshotTakenAt(long timeInMillis) {
        connection.sync().set(LAST_DAILY_RATING_SNAPSHOT_TAKEN_AT, String.valueOf(timeInMillis));
    }

    private void updateMonthlyRatingSnapshotTakenAt(long timeInMillis) {
        connection.sync().set(LAST_MONTHLY_RATING_SNAPSHOT_TAKEN_AT, String.valueOf(timeInMillis));
    }

    private long getBlockTime(long blockNumber) {
        long blockTime = blockTimeCache.getOrDefault(blockNumber, 0L);
        if(blockTime != 0) {
            return blockTime;
        } else {
            try {
                blockTime = chainService.getBlock(String.valueOf(blockNumber)).getTimestampInMillis();
                if(blockTime != 0)
                    blockTimeCache.put(blockNumber, blockTime);
                return blockTime;
            } catch (Exception e) {
                return 0; //Don't do anything
            }
        }
    }

    private int millisToDate(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        DateFormat df = new SimpleDateFormat("yyyyMMdd");

        String date = df.format(calendar.getTime());
        return Integer.parseInt(date);
    }

    private int millisToMonth(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        DateFormat df = new SimpleDateFormat("yyyyMM");

        String date = df.format(calendar.getTime());
        return Integer.parseInt(date);
    }

}
