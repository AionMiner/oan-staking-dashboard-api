package com.bloxbean.oan.dashboard.staking.posblocks.service;

import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlockListResponse;
import com.bloxbean.oan.dashboard.staking.posblocks.processor.ValidatorBlockProcessor;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlock;
import com.bloxbean.oan.dashboard.model.Staker;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
public class PosBlocksService {

    private  final static long DAILY_TOTAL_BLOCKS = 8640; //6 * 60 * 24

    @Inject
    private ValidatorBlockProcessor validatorBlockProcessor;

    @Inject
    private PoolRedisService poolRedisService;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private com.bloxbean.oan.dashboard.staking.stats.service.StakedAccountsScheduleService StakedAccountsScheduleService;

    public JSONObject getPosBlocksCount(String validator,long top, long within, boolean includeBlocks) {
        Staker staker = StakedAccountsScheduleService.getStaker(validator);
        if(staker == null)
            throw new RuntimeException("Staker not found for validator: " + validator);

        if(top == -1)
            top = validatorBlockProcessor.getTopBlockNumber();

        long totalCount = validatorBlockProcessor.getTotalPosBlockCount(top, within);
        long validatorCount = validatorBlockProcessor.getPosBlockCountByCoinBase(staker.getCoinbaseAddress(),top, within);
        String lastUpdatedTime = validatorBlockProcessor.getValidatorBlocksLastUpdatedTime();

        List<POSBlock> posBlocks = null;
        if(includeBlocks) {
            posBlocks = validatorBlockProcessor.getPOSBlocksByBlockNumbers( staker.getCoinbaseAddress(), top-within, top);
            System.out.println("pos blocks...." + posBlocks);
        }

        //response
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("validator", validator);
        jsonObject.put("coinbaseAddress", staker.getCoinbaseAddress());
        jsonObject.put("to-block", top);
        jsonObject.put("from-block", top - within);

        jsonObject.put("validator-posblock-count", validatorCount);
        jsonObject.put("total-posblock-count", totalCount);
        jsonObject.put("last-updated-time", lastUpdatedTime);
        if(posBlocks != null) {
            jsonObject.put("blocks", posBlocks);
        }

        return jsonObject;
    }

    public JSONObject getPosBlocksCountForDayRange(String validator,long fromDay, long toDay) {

        Staker staker = StakedAccountsScheduleService.getStaker(validator);
        if(staker == null)
            throw new RuntimeException("Staker not found for validator: " + validator);

        long top = validatorBlockProcessor.getTopBlockNumber();

        long within = (fromDay - toDay) * DAILY_TOTAL_BLOCKS;
        long toBlock = toDay * DAILY_TOTAL_BLOCKS;

        top = top - toBlock; //effective top
        long totalCount = validatorBlockProcessor.getTotalPosBlockCount(top, within);
        long validatorCount = validatorBlockProcessor.getPosBlockCountByCoinBase(staker.getCoinbaseAddress(),top, within);
        String lastUpdatedTime = validatorBlockProcessor.getValidatorBlocksLastUpdatedTime();

        //response
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("validator", validator);
        jsonObject.put("coinbaseAddress", staker.getCoinbaseAddress());
        jsonObject.put("to-day", toDay);
        jsonObject.put("from-day", fromDay);
        jsonObject.put("to-block", top);
        jsonObject.put("from-block", top - within);

        jsonObject.put("validator-posblock-count", validatorCount);
        jsonObject.put("total-posblock-count", totalCount);
        jsonObject.put("last-updated-time", lastUpdatedTime);

        return jsonObject;
    }

    public POSBlockListResponse getValidatorBlocks(String validator, long start, long end) {
        Staker staker = StakedAccountsScheduleService.getStaker(validator);
        if(staker == null)
            throw new RuntimeException("Staker not found for validator: " + validator);

        validatorBlockProcessor.getPosBlockCountByCoinBase(staker.getCoinbaseAddress(),start, end);
        return null;
    }

    public long getTopBlockNumber() {
        return validatorBlockProcessor.getTopBlockNumber();
    }

    public String getLastUpdatedTime() {
        return validatorBlockProcessor.getValidatorBlocksLastUpdatedTime();
    }
}
