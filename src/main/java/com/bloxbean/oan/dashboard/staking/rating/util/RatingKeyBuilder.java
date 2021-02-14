package com.bloxbean.oan.dashboard.staking.rating.util;

import javax.inject.Singleton;

@Singleton
public class RatingKeyBuilder {
    public final static String POS_BLOCKS_KEY = "pos_blocks";
    public final static String POS_BLOCK_KEY_SUFFIX = ".pos_blocks";
    public final static String VALIDATOR_RATING_KEY_SUFFIX = ".ratings";
    public final static String VALIDATOR_DAILY_AVG_HISTORY_RATING_SUFFIX=".daily.avg.rating.history"; //24hr avg for 28 days
    public final static String VALIDATOR_DAILY_CURRENT_HISTORY_RATING_SUFFIX=".daily.current.rating.history";

    public final static String VALIDATOR_MONTHLY_DAILY_AVG_HISTORY_RATING_SUFFIX=".monthly_daily.avg.rating.history"; //Monthly, 24hr avg for 28 days
    public final static String ALL_VALIDATORS_MONTHLY_DAILY_AVG_PREFIX_="all.validators.monthly_daily.avg.rating.";

    public final static String DURATION_1h = "1h";
    public final static String DURATION_24h = "24h";
    public final static String DURATION_7d = "7d";

    private static String POOLS_AVG_RATING_KEY_PREFIX = "pools.avg_ratings";
    private static String VALIDATORS_CURRENT_RATINGS = "validators.current.ratings";

    public String getPOSBlockKeyForCoinbaseAddress(String validator) {
        return validator + POS_BLOCK_KEY_SUFFIX;
    }

    public String getValidatorRatingsKey(String validator, String  durationSuffix) {
        return validator + VALIDATOR_RATING_KEY_SUFFIX + "_" + durationSuffix;
    }

    public String getValidatorRatingsKeyBackup(String validator, String durationSuffix) {
        return getValidatorRatingsKey(validator, durationSuffix) + "_bk";
    }

    public String getValidatorRatingHourlyKey(String validator) {
        return validator + VALIDATOR_RATING_KEY_SUFFIX + "_" + DURATION_1h;
    }

    public String getAvgBucketKeyForDuration(String duration) {
        return POOLS_AVG_RATING_KEY_PREFIX + "_" + duration;
    }

    public String getValidatorsCurrentRatingsKey() {
        return VALIDATORS_CURRENT_RATINGS;
    }

    //Historical
    public String getValidatorDailyAvgHistoryRatingsKey(String validator) {
        return validator + VALIDATOR_DAILY_AVG_HISTORY_RATING_SUFFIX;
    }
    public String getValidatorDailyCurrentHistoryRatingsKey(String validator) {
        return validator + VALIDATOR_DAILY_CURRENT_HISTORY_RATING_SUFFIX;
    }
    public String getValidatorMonthlyDailyAvgHistoryRatingsKey(String validator) {
        return validator + VALIDATOR_MONTHLY_DAILY_AVG_HISTORY_RATING_SUFFIX;
    }

    public String getAllValidatorsMontlyAvgRatingBucket(int date) {
        return ALL_VALIDATORS_MONTHLY_DAILY_AVG_PREFIX_ + date;
    }
}
