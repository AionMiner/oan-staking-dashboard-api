package com.bloxbean.oan.dashboard.core.service;

import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import lombok.extern.slf4j.Slf4j;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.aion4j.avm.helper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

@Singleton
@Slf4j
public class Web3RpcService extends BasicService {
    private final static Logger logger = LoggerFactory.getLogger(Web3RpcService.class);

    public String sendTransaction(String network, String signedTx) {
        RemoteAVMNode remoteAvmNode = getNetworkRemoteAVMNode(network);

        return remoteAvmNode.sendRawTransaction(signedTx);
    }

    public String callContract(String network, String contract, String callData) {
        RemoteAVMNode remoteAvmNode = getNetworkRemoteAVMNode(network);

        return remoteAvmNode.call(contract, null, callData, null, NetworkConstants.gas, NetworkConstants.gasPrice);
    }

    public String getReceipt(String network, String txHash) {
        RemoteAVMNode remoteAvmNode = getNetworkRemoteAVMNode(network);

        return remoteAvmNode.getReceipt(txHash).toString();
    }

    public String proxyCall(String network, String json) {
        String networkWeb3RpcUrl = getNetworkWeb3RpcUrl(network);
        if(StringUtils.isEmpty(networkWeb3RpcUrl)) {
            throw new Web3CallException("Invalid web3 rpc url : " + networkWeb3RpcUrl);
        }

        return invokeRpc(networkWeb3RpcUrl, json);
    }

    private String invokeRpc(String networkWeb3RpcUrl, String jsonRequest) {
        try {
            log.info("Web3Rpc request to url {} with data: {} \n", networkWeb3RpcUrl, jsonRequest);
            HttpResponse<JsonNode> jsonResponse = this.getHttpRequest(networkWeb3RpcUrl).body(jsonRequest).asJson();

            JsonNode jsonNode = (JsonNode)jsonResponse.getBody();
            if (jsonNode == null) {
                return null;
            } else {
                return jsonNode.toString();
            }
        } catch (UnirestException ex) {
            throw new Web3CallException("Error calling web3 json rpc", ex);
        }
    }

    private HttpRequestWithBody getHttpRequest(String web3RpcUrl) {
        return Unirest.post(web3RpcUrl).header("accept", "application/json").header("Content-Type", "application/json");
    }
}
