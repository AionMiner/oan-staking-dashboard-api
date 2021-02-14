package com.bloxbean.oan.dashboard.staking.rating.controller;

import com.bloxbean.oan.dashboard.staking.rating.RatingSnapshotService;
import com.bloxbean.oan.dashboard.staking.rating.model.CurrentRatings;
import com.bloxbean.oan.dashboard.staking.rating.model.PoolAvgRating;
import com.bloxbean.oan.dashboard.staking.rating.model.Rating;
import com.bloxbean.oan.dashboard.staking.rating.model.RatingReponse;
import com.bloxbean.oan.dashboard.staking.rating.RatingSchedulerService;
import io.micronaut.http.annotation.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@Controller("rating")
@Slf4j
public class PerfRatingController {

    @Inject
    private RatingSchedulerService ratingSchedulerService;

    @Inject
    private RatingSnapshotService ratingSnapshotService;

    @Get(uri = "/{validator}/{duration}")
    public RatingReponse getRatingsPer1Hrs(@PathVariable String validator, @PathVariable String duration,
                                           @Nullable @QueryValue Long start, @Nullable @QueryValue Long end) {
        if(start == null) start = 0L;
        if(end == null) end = -1L;

        return ratingSchedulerService.getRatingsForDuration(validator, duration, start, end);
    }

    @Get(uri = "/{duration}")
    public Map<String, PoolAvgRating> getRatingsForPools(@PathVariable String duration) {
        return ratingSchedulerService.getPoolsRatingForDuration(duration);
    }

    @Get(uri = "/{validator}/avg-rating/{duration}")
    public PoolAvgRating getPoolAvgRatingForDuration(@PathVariable String validator, @PathVariable String duration) {
        return ratingSchedulerService.getPoolAvgRatingForDuration(validator, duration);
    }

    @Get(uri = "/current")
    public CurrentRatings getValidatorsCurrentRating(@Nullable @QueryValue Long start, @Nullable @QueryValue Long end, @Nullable @QueryValue Boolean ignoreSolo) {
        if(start == null)
            start = 0L;
        if(end == null)
            end = -1L;

        if(ignoreSolo == null)
            ignoreSolo = Boolean.FALSE;

        return ratingSchedulerService.getValidatorCurrentRatings(start, end, ignoreSolo);
    }

    @Get(uri = "/snapshot/monthly/month/{month}")
    public List<PoolAvgRating> getMonthlyRatingsForPools(@PathVariable int month) {
        return ratingSnapshotService.getMonthlyRatingForAllValidators(month);
    }

    @Get(uri = "/snapshot/monthly/validator/{validator}")
    public List<PoolAvgRating> getMonthlyRatingsForValidator(@PathVariable String validator, @QueryValue int start, @QueryValue int end) {
        return ratingSnapshotService.getAvgRatingForValidatorForLastnMonths(validator, start, end);
    }

    @Get(uri = "/snapshot/daily/validator/{validator}")
    public List<Rating> getDailyRatingsForPools(@PathVariable String validator, @QueryValue int start, @QueryValue int end) {
        return ratingSnapshotService.getDailyRatingForLastnDays(validator, start, end);
    }

    @Get(uri = "/calculate-rating")
    public void runBlockFetcher() {
        ratingSchedulerService.calculate();
    }
}
