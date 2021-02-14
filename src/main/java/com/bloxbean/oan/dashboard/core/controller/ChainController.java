package com.bloxbean.oan.dashboard.core.controller;

import com.bloxbean.oan.dashboard.core.service.ChainService;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import com.bloxbean.oan.dashboard.model.Block;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRegistryService;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.math.BigInteger;

@Controller("/chain")
public class ChainController {
    private Logger logger = LoggerFactory.getLogger(ChainController.class);

    @Value("${web3rpc_url}")
    private String web3rpcUrl;

    @Inject
    private PoolRegistryService poolRegistryService;

    @Inject
    private ChainService chainService;

    @Get(uri = "/latest-block", produces = MediaType.TEXT_PLAIN)
    public String getLatestBlock() {
        BigInteger latestBlock = chainService.getLatestBlock();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("latest", latestBlock);
        return jsonObject.toString();
    }

    @Get(uri = "/blocks/{blockNumber}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Block> getBlock(@QueryValue String blockNumber) {
        Block block = chainService.getBlock(blockNumber);

        if(block != null)
            return HttpResponse.ok(block);
        else
            return HttpResponse.notFound();
    }

    private RemoteAVMNode getRemoteAvmNode() {
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }

}
