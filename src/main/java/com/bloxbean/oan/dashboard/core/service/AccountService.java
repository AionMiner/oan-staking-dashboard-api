package com.bloxbean.oan.dashboard.core.service;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.util.EnvUtil;
import io.micronaut.context.annotation.Value;
import org.aion.base.util.ByteUtil;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.math.BigInteger;

@Singleton
public class AccountService {
    private final static Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Value("${web3rpc_url}")
    private String web3rpcUrl;

    public BigInteger getBalance(String account) {
        try {
            RemoteAVMNode remoteAvmNode = getRemoteAvmNode();
            String balanceStr = remoteAvmNode.getBalance(account);

            if(balanceStr == null || balanceStr.isEmpty()) {
                throw new Web3CallException("Error getting account balance");
            }

            if(balanceStr.startsWith("0x")) {
                BigInteger balance = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(balanceStr));
                return balance;
            } else
                return new BigInteger(balanceStr);


        } catch (Exception e) {
            logger.error("Error getting account balance", e);
            return null;
        }
    }

    public BigInteger getBalanceByNetwork(String network, String account) {
        RemoteAVMNode networkRemoteAvmNode = getNetworkRemoteAVMNode(network);
        String balanceStr = networkRemoteAvmNode.getBalance(account);

        if(balanceStr == null || balanceStr.isEmpty()) {
            throw new Web3CallException("Error getting account balance");
        }

        if(balanceStr.startsWith("0x")) {
            BigInteger balance = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(balanceStr));
            return balance;
        } else
            return new BigInteger(balanceStr);
    }

    private RemoteAVMNode getNetworkRemoteAVMNode(String network) {
        if(StringUtils.isEmpty(network)) {
            logger.error(String.format("Invalid network id : %s", network));
            throw new Web3CallException(String.format("Invalid network id : %s", network));
        }

        String networkWeb3RpcUrl = EnvUtil.getProperty(network + "_web3rpc_url");
        if(StringUtils.isEmpty(networkWeb3RpcUrl)) {
            logger.error(String.format("No web3rpc_url found for network %s ", network));
            throw new Web3CallException(String.format("No web3rpc_url found for network %s ", network));
        }

        return new RemoteAVMNode(networkWeb3RpcUrl, new Slf4jLog(logger));
    }

    private RemoteAVMNode getRemoteAvmNode() {
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }
}
