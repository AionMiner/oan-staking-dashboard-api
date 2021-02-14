package com.bloxbean.oan.dashboard.staking.activities.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.model.PoolMetaData;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.staking.stats.service.StakerRegistryService;
import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.util.DateUtil;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class SoloStakerBlockProcessor implements BlockProcessor {
    private final static Logger logger = LoggerFactory.getLogger(SoloStakerBlockProcessor.class);

    private final static String STAKER_REGISTERED_TOPIC = "0x5374616b65725265676973746572656400000000000000000000000000000000"; //StakerRegistered
    private final static String STAKER_TRANSFERED_TOPIC =  "0x5374616b655472616e7366657272656400000000000000000000000000000000"; //StakeTransferred
    private final static String STAKER_BONDED_TOPIC = "0x426f6e6465640000000000000000000000000000000000000000000000000000"; //Bonded
    private final static String STAKER_UNBOUNDED_TOPIC = "0x556e626f6e646564000000000000000000000000000000000000000000000000"; //Unbonded

    private final static String STAKERS_KEYS = "stakers";
    private final static String STAKERS_PROPS_KEYS = "stakers.props";
    private final static String STAKERS_RANKING = "stakers.ranking";
    private static final String STAKERS_LAST_UPDATED_TIME = "STAKERS_LAST_UPDATED_TIME";

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    @Inject
    private StakerRegistryService stakerRegistryService;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private DelegatedEventBlockProcessor delegatedEventBlockProcessor;

    @Inject
    private PoolRedisService poolRedisService;

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
        //Delegate
        String res = remoteNodeAdapterService.getRemoteAvmNode()
                .getLogs(fromBlock.toString(), toBlock.toString(), null, STAKER_REGISTERED_TOPIC, null);
        try {
            parseStakerRegisteredEvent(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse delegate event log", e);
        }

        connection.sync().set(STAKERS_LAST_UPDATED_TIME, DateUtil.currentTimeInGMT());
    }

    private void parseStakerRegisteredEvent(String res) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode =objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for staker registered event ");
            return;
        }

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);

            String address = eventNode.get("address").asText();
            if(!NetworkConstants.STAKER_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;
//
            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            if(STAKER_REGISTERED_TOPIC.equals(topicType)) {
                String identityAddress = topicsNode.get(1).asText();
                String signingAddress = topicsNode.get(2).asText();
                String coinbaseAddress = topicsNode.get(3).asText();

                String dataHex = eventNode.get("data").asText();
                String managementAddress = dataHex;

                connection.sync().sadd(STAKERS_KEYS, identityAddress);
                connection.sync().hset(STAKERS_PROPS_KEYS, identityAddress + ".signingAddress", signingAddress);
                connection.sync().hset(STAKERS_PROPS_KEYS, identityAddress + ".coinbaseAddress", coinbaseAddress);
                connection.sync().hset(STAKERS_PROPS_KEYS, identityAddress + ".managementAddress", managementAddress);
            }
        }
    }

    //Update stake amount
    public void updateStakerRankingByStakeAmount() {
        Set<String> stakers = connection.sync().smembers(STAKERS_KEYS);

        if(stakers == null)
            return;

        for(String staker: stakers) {
            BigInteger stakeAmount = stakerRegistryService.getTotalStake(staker);

            if(stakeAmount != null) {
                BigDecimal stakeAmountInAion = CryptoUtil.ampToAion(stakeAmount);
                connection.sync().zadd(STAKERS_RANKING, stakeAmountInAion.doubleValue(), staker);
            }
        }
    }

    //Get methods
    public List<Staker> getStakers(long start, long end, boolean poolInfo, boolean ignoreLogoData) {
        List<ScoredValue<String>> stakersScores = connection.sync().zrevrangeWithScores(STAKERS_RANKING, start, end);

        Set<String> validators = delegatedEventBlockProcessor.getValidators(); //pools

        List<Staker> stakers = new ArrayList<>();

        for(ScoredValue<String> sv: stakersScores) {
            String stakerIdentity = sv.getValue();
            double value = sv.getScore();

            Staker staker = getStaker(stakerIdentity, poolInfo, ignoreLogoData);

//            Staker staker= new Staker(stakerIdentity, value);
//
//            if(!validators.contains(stakerIdentity)) {
//                staker.setSolo(true);
//            } else {
//                if(poolInfo) {
//                    PoolMetaData poolMetaData = null;
//                    try {
//                        poolMetaData = poolRedisService.getPool(stakerIdentity).getPoolMetaData();
//                    } catch (Exception e) {
//                        logger.error("error getting pool metadata: " + stakerIdentity, e);
//                    }
//
//                    if (poolMetaData != null) {
//                        staker.setPoolMetaData(poolMetaData);
//                    } else {
//                        staker.setPoolMetaData(new PoolMetaData());
//                    }
//                }
//            }

            stakers.add(staker);
        }

        return stakers;
    }

    //Get methods
    public List<Staker> getSoloStakers(long start, long end) {
        List<ScoredValue<String>> stakersScores = connection.sync().zrevrangeWithScores(STAKERS_RANKING, start, end);

        Set<String> validators = delegatedEventBlockProcessor.getValidators(); //pools

        List<Staker> stakers = new ArrayList<>();

        for(ScoredValue<String> sv: stakersScores) {
            String stakerIdentity = sv.getValue();
            double value = sv.getScore();

            if(!validators.contains(stakerIdentity)) {
                Staker staker= new Staker(stakerIdentity, value);
                staker.setSolo(true);
                stakers.add(staker);
            }
        }

        return stakers;
    }

    public Staker getStaker(String address) {
        return getStaker(address, true, false);
    }
    //Grt staker by address
    public Staker getStaker(String address, boolean poolInfo, boolean ignoreLogoData) {
        double stakeAmt = connection.sync().zscore(STAKERS_RANKING, address);
        boolean isPool = delegatedEventBlockProcessor.isValidator(address);

        //get staker props
        String signingAddress = connection.sync().hget(STAKERS_PROPS_KEYS, address + ".signingAddress");
        String coinbaseAddress = connection.sync().hget(STAKERS_PROPS_KEYS, address + ".coinbaseAddress");
        String managementAddress = connection.sync().hget(STAKERS_PROPS_KEYS, address + ".managementAddress");

        Staker staker= new Staker(address, stakeAmt);
        staker.setSigningAddress(signingAddress);
        staker.setCoinbaseAddress(coinbaseAddress);
        staker.setManagementAddress(managementAddress);

        if(!isPool)
            staker.setSolo(true);

        PoolMetaData poolMetaData = null;
        if(isPool && poolInfo) {
            //TODO only fetch poolmetadata for pools not solo staker
            try {
                poolMetaData = poolRedisService.getPool(address).getPoolMetaData();

                if(poolMetaData != null && ignoreLogoData) {
                    poolMetaData.setLogo(null);
                }
            } catch (Exception e) {
                logger.error("error getting pool metadata: " + address, e);
            }
        }

        if (poolMetaData != null) {
            staker.setPoolMetaData(poolMetaData);
        } else {
            staker.setPoolMetaData(new PoolMetaData());
        }

        return staker;
    }

    public String getLastUpdatedTime() {
        return connection.sync().get(STAKERS_LAST_UPDATED_TIME);
    }

}
