package com.bloxbean.oan.dashboard.staking.blocktracker;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.util.HexConverter;
import org.aion4j.avm.helper.util.StringUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import java.math.BigInteger;

public class BlockTrackerProcessor implements BlockProcessor {

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
        for (BigInteger bi = fromBlock;
             bi.compareTo(toBlock) <= 0;
             bi = bi.add(BigInteger.ONE)) {

            JSONObject result = remoteNodeAdapterService.getRemoteAvmNode().getBlockByNumber(bi.toString());
            if(result == null)
                break;

            String timestampHex = result.getString("timestamp");
            long timestamp = 0L;

            if(!StringUtils.isEmpty(timestampHex))
                timestamp = HexConverter.hexToTimestampInMillis(timestampHex);

        }
    }
}
