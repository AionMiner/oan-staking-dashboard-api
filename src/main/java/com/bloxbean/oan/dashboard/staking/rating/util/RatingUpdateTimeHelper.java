package com.bloxbean.oan.dashboard.staking.rating.util;

import com.bloxbean.oan.dashboard.util.DateUtil;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

@Singleton
@Slf4j
public class RatingUpdateTimeHelper {

    private final static String RATING_UPDATE_TIME_BUCKET_KEY = "rating_update_times";
    private final static String RATING_LAST_UPDATED_AT = "RATING_LAST_UPDATED_AT";
    private final static String CURRENT_RATING_UPDATED_AT = "CURRRENT_RATING_UPDATED_AT";

    private StatefulRedisConnection<String, String> connection;

    public RatingUpdateTimeHelper(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public void updateRatingUpdateTime(String validator, String rateKey, String time) {
        connection.sync().hset(RATING_UPDATE_TIME_BUCKET_KEY, validator + "." + RATING_LAST_UPDATED_AT + "_" + rateKey, DateUtil.currentTimeInGMT());
    }

    public String getRatingUpdateTime(String validator, String rateKey) {
        return connection.sync().hget(RATING_UPDATE_TIME_BUCKET_KEY, validator + "." + RATING_LAST_UPDATED_AT + "_" + rateKey);
    }

    public void updateCurrentRatingUpdateTime() {
        connection.sync().hset(RATING_UPDATE_TIME_BUCKET_KEY, CURRENT_RATING_UPDATED_AT, DateUtil.currentTimeInGMT());
    }

    public String getCurrentRatingUpdateTime() {
        return connection.sync().hget(RATING_UPDATE_TIME_BUCKET_KEY, CURRENT_RATING_UPDATED_AT);
    }
}
