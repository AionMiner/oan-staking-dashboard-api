package com.bloxbean.oan.dashboard.core.service;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import io.micronaut.context.annotation.Value;
import com.bloxbean.oan.dashboard.util.EnvUtil;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicService {
    private final static Logger logger = LoggerFactory.getLogger(BasicService.class);

    @Value("${web3rpc_url}")
    protected String web3rpcUrl;

    protected RemoteAVMNode getNetworkRemoteAVMNode(String network) {
        if(StringUtils.isEmpty(network)) {//mainnet
            return getRemoteAvmNode();
        }

        String networkWeb3RpcUrl = EnvUtil.getProperty(network + "_web3rpc_url");
        if(StringUtils.isEmpty(networkWeb3RpcUrl)) {
            logger.error(String.format("No web3rpc_url found for network %s ", network));
            throw new Web3CallException(String.format("No web3rpc_url found for network %s ", network));
        }

        return new RemoteAVMNode(networkWeb3RpcUrl, new Slf4jLog(logger));
    }

    protected RemoteAVMNode getRemoteAvmNode() {
        if(logger.isDebugEnabled()) {
            logger.debug("Using web3rpc_url " + web3rpcUrl);
        }
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }

    protected String getNetworkWeb3RpcUrl(String network) {
        if(StringUtils.isEmpty(network)) {//mainnet
            return null;
        }
        if("mainnet".equalsIgnoreCase(network))
            return web3rpcUrl;

        return EnvUtil.getProperty(network + "_web3rpc_url");
    }
}
