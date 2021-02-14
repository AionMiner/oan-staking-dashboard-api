package com.bloxbean.oan.dashboard.staking.activities.processor;

import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.model.PoolMetaData;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.staking.activities.service.StakeActivityStreamService;
import com.bloxbean.oan.dashboard.staking.activities.model.Delegator;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorPools;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorsResult;
import com.bloxbean.oan.dashboard.staking.activities.model.PoolDelegators;
import com.bloxbean.oan.dashboard.staking.activities.model.Activity;
import com.bloxbean.oan.dashboard.staking.activities.model.Delegation;
import com.bloxbean.oan.dashboard.staking.activities.model.Transfer;
import com.bloxbean.oan.dashboard.staking.activities.model.UnDelegation;
import com.bloxbean.oan.dashboard.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.ValueScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.HexUtil;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class DelegatedEventBlockProcessor implements BlockProcessor {
    private final static Logger logger = LoggerFactory.getLogger(DelegatedEventBlockProcessor.class);

    private final static String VALIDATORS_KEY = "validators";
    private final static String DELEGATORS_KEY = "delegators";
    private final static String TOP_DELEGATORS = "top_delegators";
    private final static String TOP_DELEGATORS_BACKUP = "top_delegators_bakup";

    private static final String DELEGATOR_EVENT_LOGS_UPDATED_TIME = "DELEGATED_EVENT_LOGS_LAST_UPDATED_TIME";
    private static final String TOP_DELEGATORS_UPDATED_TIME = "TOP_DELEGATOR_LAST_UPDATE_TIME";

    private String delegatedTopic = "0x41445344656c6567617465640000000000000000000000000000000000000000"; //"ADSDelegated"; //ADSPoolRegistered
    private String undelegatedTopic = "0x414453556e64656c656761746564000000000000000000000000000000000000"; //ADSUndelegated
    private String transferredTopic = "0x41445344656c65676174696f6e5472616e736665727265640000000000000000"; //ADSDelegationTransferred

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    @Inject
    private PoolRegistryService poolRegistryService;

    @Inject
    private PoolRedisService poolRedisService;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private StakeActivityStreamService stakeActivityStreamService;

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {

        //Delegate
        String res = remoteNodeAdapterService.getRemoteAvmNode()
                .getLogs(fromBlock.toString(), toBlock.toString(), null, delegatedTopic, null);
        try {
            parseDelegatedEventJson(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse delegate event log", e);
        }

        //Undelegate
        res = remoteNodeAdapterService.getRemoteAvmNode()
                .getLogs(fromBlock.toString(), toBlock.toString(), null, undelegatedTopic, null);

        try {
            parseunDelegatedEventJson(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse undelegate event log", e);
        }

        //Transferred
        res = remoteNodeAdapterService.getRemoteAvmNode()
                .getLogs(fromBlock.toString(), toBlock.toString(), null, transferredTopic, null);

        try {
            parseTransferredEventJson(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse transferred event log", e);
        }

        connection.sync().set(DELEGATOR_EVENT_LOGS_UPDATED_TIME, DateUtil.currentTimeInGMT());
    }

    public void parseDelegatedEventJson(String res) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode =objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for delegate event ");
            return;
        }

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);
//            String dataHex = eventNode.get("data").asText();
            String address = eventNode.get("address").asText();
            if(!NetworkConstants.POOL_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;
//
            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            //block number
            String blockNoHex = eventNode.get("blockNumber").asText();
            BigInteger blockNumber = HexConverter.hexToBigInteger(blockNoHex);

            //txhash
            String txHash = eventNode.get("transactionHash").asText();

            //Get delegated amount - needed for activity stream
            String dataHex = eventNode.get("data").asText();
            BigInteger delegatedAmt = HexConverter.hexToBigInteger(dataHex);

            if(delegatedTopic.equals(topicType)) {
                String delegator = topicsNode.get(1).asText();
                String validator = topicsNode.get(2).asText();

                double stakedAmountAionDouble = getStakedAmountInAion(delegator, validator);

                connection.sync().zadd(getValidatorDelegatorsKey(validator), stakedAmountAionDouble, delegator);
                connection.sync().zadd(getDelegatorValidatorsKey(delegator), stakedAmountAionDouble, validator);
                connection.sync().sadd(VALIDATORS_KEY, validator);
                connection.sync().sadd(DELEGATORS_KEY, delegator);

                Activity activity = getDelegatedActivity(validator, delegator, delegatedAmt, blockNumber, txHash);
                stakeActivityStreamService.newActivity(activity);
            }
        }
    }

    private double getStakedAmountInAion(String delegator, String validator) {
        double stakedAmountAionDouble = 0;
        try {
            BigInteger stakedAmount = poolRegistryService.getStake(validator, delegator, null);
            stakedAmountAionDouble = CryptoUtil.ampToAion(stakedAmount).doubleValue();
        } catch (Exception e) {
            e.printStackTrace();
            stakedAmountAionDouble = -1;
        }
        return stakedAmountAionDouble;
    }

    public void parseunDelegatedEventJson(String res) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode =objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for undelegate event ");
            return;
        }

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);
           // String dataHex = poolNode.get("data").asText();
            String address = eventNode.get("address").asText();
            if(!NetworkConstants.POOL_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;

            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            //block number
            String blockNoHex = eventNode.get("blockNumber").asText();
            BigInteger blockNumber = HexConverter.hexToBigInteger(blockNoHex);

            //txhash
            String txHash = eventNode.get("transactionHash").asText();

             if(undelegatedTopic.equals(topicType)) {
                String idStr = topicsNode.get(1).asText();
                long id = -1;
                if(!StringUtils.isEmpty(idStr)) {
                    try {
                        id = HexConverter.hexToLong(idStr);
                    } catch (Exception e) {
                        logger.error("Error converting hex to long for id", e);
                    }
                }

                String delegator = topicsNode.get(2).asText();
                String validator = topicsNode.get(3).asText();
                //Get undelegated amounts - needed for activity stream
                String dataHex = eventNode.get("data").asText();

                Tuple<BigInteger, BigInteger> amts = getUndelegationEventFromEventLog(dataHex);
                BigInteger undelegatedAmt = amts._1;
                BigInteger fee = amts._2;

                double stakedAmountAionDouble = getStakedAmountInAion(delegator, validator);

                if(stakedAmountAionDouble == 0) {
                    connection.sync().zrem(getValidatorDelegatorsKey(validator), delegator);
                    connection.sync().zrem(getDelegatorValidatorsKey(delegator), validator);
                }
                connection.sync().sadd(getValidatorUnDelegatorsKey(validator), delegator);

                //activity stream
                 Activity activity = getUnDelegatedActivity(id, validator, delegator, undelegatedAmt, fee, blockNumber, txHash);
                 stakeActivityStreamService.newActivity(activity);
            }
        }
    }

    private void parseTransferredEventJson(String res) throws Exception{
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode =objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for transferred event ");
            return;
        }

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);
            // String dataHex = poolNode.get("data").asText();
            String address = eventNode.get("address").asText();
            if(!NetworkConstants.POOL_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;

            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            //block number
            String blockNoHex = eventNode.get("blockNumber").asText();
            BigInteger blockNumber = HexConverter.hexToBigInteger(blockNoHex);

            //txhash
            String txHash = eventNode.get("transactionHash").asText();

            if(transferredTopic.equals(topicType)) {
                String idStr = topicsNode.get(1).asText();
                long id = -1;
                if(!StringUtils.isEmpty(idStr)) {
                    try {
                        id = HexConverter.hexToLong(idStr);
                    } catch (Exception e) {
                        logger.error("Error converting hex to long for id", e);
                    }
                }

                String delegator = topicsNode.get(2).asText();
                String fromPool = topicsNode.get(3).asText();
                //Get undelegated amounts - needed for activity stream
                String dataHex = eventNode.get("data").asText();

                Tuple3<String, BigInteger, BigInteger> toPoolAmt = getTransferEventFromEventLog(dataHex);
                String toPool = toPoolAmt._1;
                BigInteger transferredAmt = toPoolAmt._2;
                BigInteger fee = toPoolAmt._3;

                if(!StringUtils.isEmpty(toPool) && !toPool.startsWith("0x"))
                    toPool = "0x" + toPool;

                double stakedAmountAionDouble = getStakedAmountInAion(delegator, fromPool);

                if(stakedAmountAionDouble == 0) {
                    connection.sync().zrem(getValidatorDelegatorsKey(fromPool), delegator);
                    connection.sync().zrem(getDelegatorValidatorsKey(delegator), fromPool);
                }

                if(connection.sync().zscore(getValidatorDelegatorsKey(toPool), delegator) == null) //If delegator is not there yet
                    connection.sync().zadd(getValidatorDelegatorsKey(toPool), 0, delegator);

                if(connection.sync().zscore(getDelegatorValidatorsKey(delegator), toPool) == null)
                    connection.sync().zadd(getDelegatorValidatorsKey(delegator), 0, toPool);

                //activity stream
                Activity outActivity = getTransferredOutActivity(id, fromPool, delegator, transferredAmt, fee, blockNumber, txHash);
                stakeActivityStreamService.newActivity(outActivity);

                Activity inActivity = getTransferredInActivity(id, toPool, delegator, transferredAmt, fee, blockNumber, txHash);
                stakeActivityStreamService.newActivity(inActivity);
            }
        }
    }

    public Set<String> getValidators() {
        return connection.sync().smembers(VALIDATORS_KEY);
    }

    public boolean isValidator(String address) {
        return connection.sync().sismember(VALIDATORS_KEY, address);
    }

    public DelegatorsResult getDelegators(String cursor, boolean isFinished) {
        long total = connection.sync().scard(DELEGATORS_KEY);
        ValueScanCursor<String> valueScanCursor = connection.sync().sscan(DELEGATORS_KEY, new ScanCursor(cursor, isFinished));
        List<String> delegators = valueScanCursor.getValues();

        DelegatorsResult delegatorsResult = new DelegatorsResult();
        delegatorsResult.setTotal(total);
        delegatorsResult.setCursor(valueScanCursor.getCursor());
        delegatorsResult.setFinished(valueScanCursor.isFinished());

        delegatorsResult.setDelegators(delegators);

        return delegatorsResult;
    }

    public PoolDelegators getTopDelegatorsForValidator(String validator, long start, long end) {
        List<ScoredValue<String>> delegators = connection.sync().zrevrangeWithScores(getValidatorDelegatorsKey(validator), start, end);
        long total = connection.sync().zcard(getValidatorDelegatorsKey(validator));

        PoolDelegators poolDelegators = new PoolDelegators();
        poolDelegators.setValidator(validator);
        poolDelegators.setTotal(total);

        try {
            BigInteger[] stakeAmounts = poolRegistryService.getTotalStake(validator, null);
            if (stakeAmounts.length > 0) {
                poolDelegators.setStateAmtInAion(CryptoUtil.ampToAion(stakeAmounts[0]).doubleValue());
            }
        } catch (Exception e) {

        }

        if(delegators == null)
            return poolDelegators;

        for(ScoredValue<String> dv: delegators) {
            String delegator = dv.getValue();
            double value = dv.getScore();

            PoolDelegators.Staker staker = new PoolDelegators.Staker(delegator, value);
            poolDelegators.addDelegator(staker);
        }

        return poolDelegators;
    }

    public void populateTopDelegators(long start, long end) {
        logger.info("Populating top delegators");

        Set<String> validators = getValidators();

        Set<String> topDelegators = new HashSet<>();
        for(String validator: validators) {
            PoolDelegators poolDelegators = getTopDelegatorsForValidator(validator, start, end);

            for(PoolDelegators.Staker staker: poolDelegators.getDelegators()) {
                topDelegators.add(staker.getDelegator());
            }
        }

        logger.info("Top delegators combined: " + topDelegators);
        logger.info("Size top delegators combined: " + topDelegators.size());

        for(String delegator: topDelegators) {
            List<ScoredValue<String>> delegatedValues = connection.sync().zrevrangeWithScores(getDelegatorValidatorsKey(delegator), 0, -1 );

            double amountInAion = 0;
            for(ScoredValue<String> dv: delegatedValues) {
                //String vali = dv.getValue();
                double value = dv.getScore();
                amountInAion += value;
            }

            connection.sync().zadd(TOP_DELEGATORS_BACKUP, amountInAion, delegator);
        }

        //Delete main, rename back to main
       //TODO connection.sync().multi();  //For transaction, need to use pool as connection is shared.
        connection.sync().del(TOP_DELEGATORS);
        connection.sync().rename(TOP_DELEGATORS_BACKUP, TOP_DELEGATORS);
        connection.sync().set(TOP_DELEGATORS_UPDATED_TIME, DateUtil.currentTimeInGMT());
       //TODO connection.sync().exec();

        //connection.sync().zremrangebyrank(TOP_DELEGATORS, end, );
        logger.info("Top delegators population done.");
    }

    public List<Delegator> getTopDelegators(long start, long end) {
        List<ScoredValue<String>> delegators = connection.sync().zrevrangeWithScores(TOP_DELEGATORS, start, end);

        List<Delegator> topDelegators = new ArrayList<>();

        for(ScoredValue<String> dv: delegators) {
            String delegator = dv.getValue();
            double value = dv.getScore();

            Delegator topDel = new Delegator(delegator, value);
            topDelegators.add(topDel);
        }

        return topDelegators;
    }

    public DelegatorPools getAllStakesOfADelegator(String delegator, boolean ignoreLogoData) {
        List<ScoredValue<String>> validatorStakes = connection.sync().zrevrangeWithScores(getDelegatorValidatorsKey(delegator), 0, -1);

        DelegatorPools delegatorPools = new DelegatorPools();
        delegatorPools.setDelegator(delegator);

        for(ScoredValue<String> dv: validatorStakes) {
            String validator = dv.getValue();
            double value = dv.getScore();

            PoolMetaData poolMetaData = null;
            double commission = -1; //error
            try {
                poolMetaData = poolRedisService.getPool(validator).getPoolMetaData();
                if(ignoreLogoData)
                    poolMetaData.setLogo(null);

                String commissionStr = poolRedisService.getPool(validator).getCommission();
                if(!StringUtils.isEmpty(commissionStr)) {
                    try {
                        commission = Double.parseDouble(commissionStr);
                        if(commission != 0)
                            commission = commission / 10000;
                    } catch (Exception e) {

                    }
                }
            } catch (Exception e) {
                logger.error("error getting pool metadata: " + validator, e);
            }

            DelegatorPools.Stake stake = new DelegatorPools.Stake(validator, value);
            stake.setPoolMetaData(poolMetaData);
            stake.setCommission(commission);

            delegatorPools.addStake(stake);
        }

        return delegatorPools;
    }

    public String getLastUpdatedTime() {
        return connection.sync().get(DELEGATOR_EVENT_LOGS_UPDATED_TIME);
    }

    public String getTopDelegatorsLastUpdatedTime() {
        return connection.sync().get(TOP_DELEGATORS_UPDATED_TIME);
    }

    private String getValidatorDelegatorsKey(String validator) {
        return validator + ".delegators";
    }

    private String getDelegatorValidatorsKey(String delegator) {
        return delegator + ".validators";
    }

    private String getValidatorUnDelegatorsKey(String validator) {
        return validator + ".undelegators";
    }

    private Tuple<BigInteger, BigInteger> getUndelegationEventFromEventLog(String dataInHex) {
        byte[] bytes = HexUtil.hexStringToBytes(dataInHex);

        ABIDecoder abiDecoder = new ABIDecoder(bytes);
        BigInteger amt = abiDecoder.decodeOneBigInteger(); //amount
        BigInteger fee = abiDecoder.decodeOneBigInteger(); //fee

        return new Tuple<>(amt, fee);
    }

    private Tuple3<String, BigInteger, BigInteger> getTransferEventFromEventLog(String dataInHex) {
        byte[] bytes = HexUtil.hexStringToBytes(dataInHex);

        ABIDecoder abiDecoder = new ABIDecoder(bytes);
        String toPool = abiDecoder.decodeOneAddress().toString();
        BigInteger amt = abiDecoder.decodeOneBigInteger(); //amount
        BigInteger fee = abiDecoder.decodeOneBigInteger(); //fee

        return new Tuple3(toPool, amt, fee);
    }

    private Activity getDelegatedActivity(String validator, String caller, BigInteger amt, BigInteger block, String txHash) {
        Activity delegation = Delegation.builder()
                .action(Delegation.ACTION_KEY)
                .validator(validator)
                .delegator(caller)
                .amt(amt)
                .amtInAion(CryptoUtil.ampToAion(amt))
                .block(block)
                .txHash(txHash)
                .build();

        return delegation;
    }

    private Activity getUnDelegatedActivity(long id, String validator, String caller, BigInteger amt, BigInteger fee, BigInteger block, String txHash) {
        Activity undelegation = UnDelegation.builder()
                .action(UnDelegation.ACTION_KEY)
                .validator(validator)
                .delegator(caller)
                .amt(amt)
                .amtInAion(CryptoUtil.ampToAion(amt))
                .block(block)
                .txHash(txHash)
                .id(id)
                .fee(fee)
                .build();

        return undelegation;
    }

    private Activity getTransferredOutActivity(long id, String fromPool, String caller, BigInteger amt, BigInteger fee, BigInteger block, String txHash) {
        Activity transfer = Transfer.builder()
                .action(Transfer.OUT_ACTION_KEY)
                .validator(fromPool)
                .delegator(caller)
                .amt(amt)
                .amtInAion(CryptoUtil.ampToAion(amt))
                .block(block)
                .txHash(txHash)
                .id(id)
                .fee(fee)
                .build();

        return transfer;
    }

    private Activity getTransferredInActivity(long id, String toPool, String caller, BigInteger amt, BigInteger fee, BigInteger block, String txHash) {
        Activity transfer = Transfer.builder()
                .action(Transfer.IN_ACTION_KEY)
                .validator(toPool)
                .delegator(caller)
                .amt(amt)
                .amtInAion(CryptoUtil.ampToAion(amt))
                .block(block)
                .txHash(txHash)
                .id(id)
                .fee(fee)
                .build();

        return transfer;
    }
}
