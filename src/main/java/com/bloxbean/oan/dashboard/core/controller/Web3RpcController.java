package com.bloxbean.oan.dashboard.core.controller;

import io.micronaut.http.annotation.*;
import com.bloxbean.oan.dashboard.core.service.Web3RpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Controller("/web3")
public class Web3RpcController {
    private final static Logger logger = LoggerFactory.getLogger(Web3RpcController.class);

    @Inject
    private Web3RpcService web3RpcService;

    @Post(uri = "/{network}/transaction", consumes = "text/plain")
    public String sendTransaction(@PathVariable String network, @Body  String rawTxn) {
        return web3RpcService.sendTransaction(network, rawTxn);
    }

    @Post(uri = "/transaction", consumes = "text/plain")
    public String sendMainnetTransaction(@Body String rawTxn) {
        return web3RpcService.sendTransaction(null, rawTxn);
    }

    @Get(uri = "/{network}/call/{contract}/{callData}")
    public String call(@PathVariable String network,@PathVariable String contract, @PathVariable  String callData) {
        return web3RpcService.callContract(network, contract, callData);
    }

    @Get(uri = "/call/{contract}/{callData}")
    public String callMainnet(@PathVariable String contract, @PathVariable  String callData) {
        return web3RpcService.callContract(null, contract, callData);
    }

    @Get(uri = "/{network}/receipt/{txHash}")
    public String getReceipt(@PathVariable String network, @PathVariable String txHash) {
        return web3RpcService.getReceipt(network, txHash);
    }

    @Get(uri = "/receipt/{txHash}")
    public String getMainnetReceipt(@PathVariable String txHash) {
        return web3RpcService.getReceipt(null, txHash);
    }

    @Post(value = "/{network}/json_rpc", consumes = "application/json", produces = "application/json")
    public String web3Call(@PathVariable String network, @Body String requestBody) {
        String result = web3RpcService.proxyCall(network, requestBody);
        return result;
    }
}
