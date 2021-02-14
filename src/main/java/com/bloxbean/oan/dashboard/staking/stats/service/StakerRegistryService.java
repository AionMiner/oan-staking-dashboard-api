package com.bloxbean.oan.dashboard.staking.stats.service;

import avm.Address;
import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion4j.avm.helper.local.LocalAvmNode;
import org.aion4j.avm.helper.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

@Singleton
public class StakerRegistryService {
    private static Logger logger = LoggerFactory.getLogger(StakerRegistryService.class);

    @Inject
    private RemoteNodeAdapterService remoteNodeAdapterService;

    public BigInteger getTotalStake(String staker) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getTotalStake", new Object[]{new Address(HexUtil.hexStringToBytes(staker))});

        String response = remoteNodeAdapterService.getRemoteAvmNode().call(NetworkConstants.STAKER_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, NetworkConstants.gas, NetworkConstants.gasPrice);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger stakeAmount = abiDecoder.decodeOneBigInteger();

        return stakeAmount;
    }
}
