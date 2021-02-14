package com.bloxbean.oan.dashboard.core.service;

import io.micronaut.context.annotation.Value;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

@Singleton
public class RemoteNodeAdapterService {
    private final static Logger logger = LoggerFactory.getLogger(RemoteNodeAdapterService.class);

    @Value("${web3rpc_url}")
    private String web3rpcUrl;

    public RemoteAVMNode getRemoteAvmNode() {
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }
}
