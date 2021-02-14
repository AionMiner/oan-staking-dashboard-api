package com.bloxbean.oan.dashboard.core.service;

import io.micronaut.context.annotation.Value;
import com.bloxbean.oan.dashboard.model.Block;
import com.bloxbean.oan.dashboard.util.HexConverter;
import org.aion.base.util.ByteUtil;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.aion4j.avm.helper.util.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.math.BigInteger;

import static com.bloxbean.oan.dashboard.util.JsonUtil.fromJson;

@Singleton
public class ChainService {
    private final static Logger logger = LoggerFactory.getLogger(ChainService.class);

    @Value("${web3rpc_url}")
    private String web3rpcUrl;

    public BigInteger getLatestBlock() {
        try {
            RemoteAVMNode remoteAvmNode = getRemoteAvmNode();
            String latestBlock = remoteAvmNode.getLatestBlock();

            if(latestBlock != null && latestBlock.startsWith("0x")) {
                BigInteger latestBlockBI = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(latestBlock));
                return latestBlockBI;
            } else
                return new BigInteger(latestBlock);
        } catch (Exception e) {
            logger.error("Error getting latest block", e);
            return null;
        }
    }

    public Block getBlock(String blockNumber) {
        try {
            RemoteAVMNode remoteAvmNode = getRemoteAvmNode();
            JSONObject blockJson = remoteAvmNode.getBlockByNumber(blockNumber);

            if(blockJson == null) return null;

            Block block = fromJson(blockJson.toString(), Block.class);
            if(!StringUtils.isEmpty(block.getTimestamp())) {
                block.setTimestampInMillis(HexConverter.hexToTimestampInMillis(block.getTimestamp()));
            }

            return block;

        } catch (Exception e) {
            logger.error("Error getting block", e);
            return null;
        }
    }

    private RemoteAVMNode getRemoteAvmNode() {
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }
}
