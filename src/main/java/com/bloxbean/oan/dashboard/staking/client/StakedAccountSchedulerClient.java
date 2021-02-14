package com.bloxbean.oan.dashboard.staking.client;

import com.bloxbean.oan.dashboard.model.Staker;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Set;

@Client("/staking-stats")
public interface StakedAccountSchedulerClient {

    @Get(uri = "/stakers", produces = MediaType.APPLICATION_JSON)
    public List<Staker> getStakers(@QueryValue long start, @QueryValue long end, @QueryValue boolean poolInfo);

    @Get(uri = "/validators", produces = MediaType.APPLICATION_JSON)
    public Set<String> getValidators();
}
