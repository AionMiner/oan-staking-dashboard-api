package com.bloxbean.oan.dashboard.staking.activities.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.staking.activities.service.StakeActivityStreamService;
import com.bloxbean.oan.dashboard.staking.activities.model.Activity;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.staking.activities.model.Withdrawal;
import com.bloxbean.oan.dashboard.util.DateUtil;
import com.bloxbean.oan.dashboard.util.HexConverter;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

@Singleton
public class WithdrawRewardsProcessor implements BlockProcessor {
    private final static Logger logger = LoggerFactory.getLogger(WithdrawRewardsProcessor.class);

    private final static String WITHDREW_TOPIC = "0x4144535769746864726577000000000000000000000000000000000000000000"; //ADSWithdrew
    private static final String WITHDREW_LAST_UPDATED_TIME = "withdrew_last_updated_time";

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private StakeActivityStreamService activityStreamService;

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
        //Delegate
        String res = remoteNodeAdapterService.getRemoteAvmNode()
                .getLogs(fromBlock.toString(), toBlock.toString(), null, WITHDREW_TOPIC, null);

        System.out.println(res);
        try {
            parseWithdrewEvents(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse delegate event log", e);
        }

        connection.sync().set(WITHDREW_LAST_UPDATED_TIME, DateUtil.currentTimeInGMT());
    }

    private void parseWithdrewEvents(String res) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode =objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for withdrew event ");
            return;
        }

        System.out.println(res);

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);

            String address = eventNode.get("address").asText();
            if(!NetworkConstants.POOL_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;
//
            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            if(WITHDREW_TOPIC.equals(topicType)) {
                String caller = topicsNode.get(1).asText();
                String pool = topicsNode.get(2).asText();
                BigInteger withdrawAmt = null;

                String dataHex = eventNode.get("data").asText();
                withdrawAmt = HexConverter.hexToBigInteger(dataHex);

                //block number
                String blockNoHex = eventNode.get("blockNumber").asText();
                BigInteger blockNumber = HexConverter.hexToBigInteger(blockNoHex);

                //txhash
                String txHash = eventNode.get("transactionHash").asText();

                Activity withdrawal = getWithdrawalDetailsAsString(pool, caller, withdrawAmt, blockNumber, txHash);
                String content = JsonUtil.toJson(withdrawal);
                if(content == null)
                    continue;

                activityStreamService.newActivity(withdrawal);

            }
        }
    }

    public String getWithdrawRewardsLastUpdatedTime() {
        return connection.sync().get(WITHDREW_LAST_UPDATED_TIME);
    }


    private Activity getWithdrawalDetailsAsString(String validator, String caller, BigInteger amt, BigInteger block, String txHash) {
        Activity withdrawal = Withdrawal.builder()
                .action(Withdrawal.ACTION_KEY)
                .validator(validator)
                .delegator(caller)
                .amt(amt)
                .amtInAion(CryptoUtil.ampToAion(amt))
                .block(block)
                .txHash(txHash)
                .build();

        return withdrawal;
    }

}
