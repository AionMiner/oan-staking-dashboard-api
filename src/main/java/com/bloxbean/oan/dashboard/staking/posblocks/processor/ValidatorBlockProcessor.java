package com.bloxbean.oan.dashboard.staking.posblocks.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.staking.posblocks.model.POSBlock;
import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.util.DateUtil;
import com.bloxbean.oan.dashboard.util.HexConverter;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import io.lettuce.core.Range;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.staking.client.StakedAccountSchedulerClient;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.StringUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class ValidatorBlockProcessor implements BlockProcessor {

    private final static String POS_BLOCKS_KEY = "pos_blocks";
    private final static String POS_BLOCKS_ERROR_KEY = "pos_blocks_error";
    private static final String VALIDATOR_BLOCKS_UPDATED_TIME = "validator_blocks_last_updated_time" ;

    private static long DAILY_POS_BLOCKS = 4320; //10sec per block. 14 * 24hr delay. 6/2 x 60 x 24

    private static long TOTAL_POS_BLOCKS_TO_KEEP = 28 * DAILY_POS_BLOCKS; //28 days

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    @Inject
    private PoolRedisService poolRedisService;

    @Inject
    private PoolRegistryService poolRegistryService;

    @Inject
    private StakedAccountSchedulerClient stakedAccountSchedulerClient;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {

        List<Pool> pools = poolRedisService.getPools();
        Map<String, String> coinbaseToValidatorMap = new HashMap<>();
        for(Pool pool: pools) {
            String cb = pool.getCoinbaseAddress();
            if(cb != null && !cb.startsWith("0x"))
                cb = "0x" + cb;

            coinbaseToValidatorMap.put(cb, pool.getValidatorAddress());
        }
        //Get stakers to add any additional validators
        List<Staker> stakers = stakedAccountSchedulerClient.getStakers(0, -1, false);
        for(Staker staker: stakers) {
            String cb = staker.getCoinbaseAddress();
            if(cb != null && !cb.startsWith("0x"))
                cb = "0x" + cb;

            String identity = staker.getIdentity();
            if(identity != null && !identity.startsWith("0x"))
                identity = "0x" + identity;

            coinbaseToValidatorMap.putIfAbsent(cb, identity);
        }

        for (BigInteger bi = fromBlock;
             bi.compareTo(toBlock) <= 0;
             bi = bi.add(BigInteger.ONE)) {

            JSONObject result = remoteNodeAdapterService.getRemoteAvmNode().getBlockByNumber(bi.toString());

            String sealType = result.getString("sealType");

            if(log.isDebugEnabled())
                log.debug("block: " + result);

            if("0x2".equalsIgnoreCase(sealType)) {
                String coinBaseAddress = result.getString("miner");
                String timestampHex = result.getString("timestamp");
                long timestamp = 0L;

                if(!StringUtils.isEmpty(timestampHex))
                    timestamp = HexConverter.hexToTimestampInMillis(timestampHex);

                //get validator's take ratio
                String validatorAddress = coinbaseToValidatorMap.get(coinBaseAddress);

                if(!StringUtils.isEmpty(validatorAddress)) {
                    log.debug("validator address: " + validatorAddress + "   coinbase address: " + coinBaseAddress);
                    BigInteger validatorTotalStakes = poolRegistryService.getTotalStakeFromStakeRegistry(validatorAddress, bi.toString());
                    BigInteger totalStake = poolRegistryService.getTotalStake(bi.toString());

                    log.debug("validator stake ---  {} is {}", validatorAddress, validatorTotalStakes);

                    double stakeWeight = 0.0;
                    if (validatorTotalStakes != null) { //&& validatorTotalStakes.length > 0) {
                        BigDecimal valStakeAmtInAion = CryptoUtil.ampToAion(validatorTotalStakes);
                        BigDecimal totalStakeAmtInAion = CryptoUtil.ampToAion(totalStake);
                        stakeWeight = (valStakeAmtInAion.doubleValue() / totalStakeAmtInAion.doubleValue()) * 100;
                    }

                    POSBlock posBlock = POSBlock.builder()
                            .blockNumber(bi.toString())
                            .coinbase(coinBaseAddress)
                            .stakeWeight(stakeWeight)
                            .timestamp(timestamp)
                            .build();

                    connection.sync().zremrangebyscore(POS_BLOCKS_KEY, Range.create(bi.doubleValue(), bi.doubleValue())); //first remove and then add to avoid duplicate
                    connection.sync().zadd(POS_BLOCKS_KEY, bi.doubleValue(), JsonUtil.toJson(posBlock));

                    connection.sync().zremrangebyscore(getCoinbasePosBlockKey(coinBaseAddress), Range.create(bi.doubleValue(), bi.doubleValue()));
                    connection.sync().zadd(getCoinbasePosBlockKey(coinBaseAddress), bi.doubleValue(), JsonUtil.toJson(posBlock));

                    connection.sync().zremrangebyrank(POS_BLOCKS_KEY, 0, -1 * (TOTAL_POS_BLOCKS_TO_KEEP + 1));
                    connection.sync().zremrangebyrank(getCoinbasePosBlockKey(coinBaseAddress), 0, -1 * (TOTAL_POS_BLOCKS_TO_KEEP + 1));
                } else {
                    //Add to pos_blocks only and set an error entry
                    POSBlock posBlock = POSBlock.builder()
                            .blockNumber(bi.toString())
                            .coinbase(coinBaseAddress)
                            .stakeWeight(0)
                            .timestamp(timestamp)
                            .build();

                    log.warn("No validator address found for coinbase address : {} at block# {}", coinBaseAddress, bi.toString());
                    connection.sync().zadd(POS_BLOCKS_KEY, bi.doubleValue(), JsonUtil.toJson(posBlock));

                    connection.sync().zadd(POS_BLOCKS_ERROR_KEY, bi.doubleValue(), "NVF");
                    connection.sync().zremrangebyrank(POS_BLOCKS_ERROR_KEY, 0, 500);
                }
            } else {
                continue;
            }
        }

        connection.sync().set(VALIDATOR_BLOCKS_UPDATED_TIME, DateUtil.currentTimeInGMT());

    }

    public String getValidatorBlocksLastUpdatedTime() {
        return connection.sync().get(VALIDATOR_BLOCKS_UPDATED_TIME);
    }

    public long getTopBlockNumber() {
        List<ScoredValue<String>> stakersScores = connection.sync().zrevrangeWithScores(POS_BLOCKS_KEY, 0, 0);
        if(stakersScores == null || stakersScores.isEmpty())
            return 0;
        else
            return (long)stakersScores.get(0).getScore();
    }

    public long getPosBlockCountByCoinBase(String coinbase, long top, long within) {
        if(!coinbase.startsWith("0x"))
            coinbase = "0x" + coinbase;

        String key = getCoinbasePosBlockKey(coinbase);
        return connection.sync().zcount(key, Range.create(top - within, top));
    }

    public long getTotalPosBlockCount(long top, long within) {
        return connection.sync().zcount(POS_BLOCKS_KEY, Range.create(top - within, top));
    }

    public List<POSBlock> getPOSBlocksByBlockNumbers(String coinbase, long startBlockNumber, long endBlockNumber) {
        if(!coinbase.startsWith("0x"))
            coinbase = "0x" + coinbase;

        String key = getCoinbasePosBlockKey(coinbase);
        List<String> posBlocks = connection.sync().zrangebyscore(key, Range.create(startBlockNumber, endBlockNumber));
        if(posBlocks == null)
            return null;
        else
            return posBlocks.stream().map(ps -> JsonUtil.fromJson(ps, POSBlock.class)).collect(Collectors.toList());
    }

    private String getCoinbasePosBlockKey(String coinbaseAddress) {
        return coinbaseAddress + ".pos_blocks";
    }

    public POSBlock getMaxBlockNoInPOSBlocks() {
        List<String> maxPosBlocks = connection.sync().zrange(POS_BLOCKS_KEY, -1, -1);
        if(maxPosBlocks == null || maxPosBlocks.size() == 0)
            return null;
        else {
            POSBlock maxPosBlock = JsonUtil.fromJson(maxPosBlocks.get(0), POSBlock.class);
            return maxPosBlock;
        }
    }

}
