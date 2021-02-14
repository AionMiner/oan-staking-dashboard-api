package com.bloxbean.oan.dashboard.staking.activities.processor;

import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.staking.activities.service.StakeActivityStreamService;
import com.bloxbean.oan.dashboard.util.HexConverter;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.util.DateUtil;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

@Singleton
public class FinalizeDoneProcessor implements BlockProcessor {
    private final static Logger logger = LoggerFactory.getLogger(FinalizeDoneProcessor.class);

    private final static String FINALIZE_UNBOUND_TOPIC = "0x556e626f6e6446696e616c697a65640000000000000000000000000000000000"; //UnbondFinalized in StakerRegistry
    private final static String FINALIZE_TRANSFER_TOPIC = "0x5472616e7366657246696e616c697a6564000000000000000000000000000000"; //TransferFinalized in StakerRegistry
    private static final String FINALIZE_LAST_UPDATED_TIME = "finalize_last_updated_time";

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
                .getLogs(fromBlock.toString(), toBlock.toString(), null, FINALIZE_UNBOUND_TOPIC, null);

        if(logger.isDebugEnabled())
            logger.debug(res);

        try {
            parseFinalizeUnboundEvent(res);
        } catch (Exception e) {
            throw new Web3CallException("Unable to parse finalize unbound event log", e);
        }

        connection.sync().set(FINALIZE_LAST_UPDATED_TIME, DateUtil.currentTimeInGMT());
    }

    private void parseFinalizeUnboundEvent(String res) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json for unbound event ");
            return;
        }

        System.out.println(res);

        for(int i=0; i<resultNode.size(); i++) {
            JsonNode eventNode = resultNode.get(i);

            String address = eventNode.get("address").asText();
            if(!NetworkConstants.STAKER_REGISTRY_ADDRESS.equalsIgnoreCase(address))
                continue;
//
            JsonNode topicsNode = eventNode.get("topics");
            String topicType = topicsNode.get(0).asText();

            //block number
            String blockNoHex = eventNode.get("blockNumber").asText();
            BigInteger blockNumber = HexConverter.hexToBigInteger(blockNoHex);

            if(FINALIZE_UNBOUND_TOPIC.equals(topicType)) {
                String dataHex = eventNode.get("data").asText();
                long id = -1;
                if(!StringUtils.isEmpty(dataHex)) {
                    try {
                        id = HexConverter.hexToLong(dataHex);
                    } catch (Exception e) {
                        logger.error("Error converting hex to long for id", e);
                    }
                }

                if(id == -1)
                    continue;

                activityStreamService.finalizeUndelegatedActivity(id, blockNumber);

            }
        }
    }

}
