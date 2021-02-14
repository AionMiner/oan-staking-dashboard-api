package com.bloxbean.oan.dashboard.staking.posblocks.controller;

import com.bloxbean.oan.dashboard.staking.posblocks.service.PosBlocksService;
import com.bloxbean.oan.dashboard.staking.stats.service.StakedAccountsScheduleService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.inject.Inject;

@Controller("pos-blocks")
@Slf4j
public class PosBlocksController {

    @Inject
    private PosBlocksService posBlocksService;

    @Inject
    private StakedAccountsScheduleService stakedAccountsScheduleService;

    @Get(uri = "/{validator}/by-blocks", produces = MediaType.APPLICATION_JSON)
    public String getPosBlocksCount(@PathVariable String validator, @QueryValue long top, @QueryValue long within, @Nullable @QueryValue Boolean includeBlocks) {
        return posBlocksService.getPosBlocksCount(validator, top, within, includeBlocks).toString();
    }

    @Get(uri = "/{validator}/by-dayrange", produces = MediaType.APPLICATION_JSON)
    public String getPosBlocksCountForDays(@PathVariable String validator, @QueryValue long fromDay, long toDay, @Nullable @QueryValue Boolean includeBlocks) {
        //T-fromDay to  T-toDay
        return posBlocksService.getPosBlocksCountForDayRange(validator, fromDay, toDay).toString();
    }

    @Get(uri = "/top-block-number")
    public String getPosBlocksLastReadBlockNumber() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("top-block-number", posBlocksService.getTopBlockNumber());
        jsonObject.put("last-updated-time", posBlocksService.getLastUpdatedTime());
        
        return jsonObject.toString();
    }

    @Get(uri = "/fetch-pos-blocks")
    public void runBlockFetcher() {
        stakedAccountsScheduleService.fetchPOSBlocks();
    }
}
