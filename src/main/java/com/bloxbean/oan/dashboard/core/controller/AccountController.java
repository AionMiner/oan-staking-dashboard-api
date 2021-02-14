package com.bloxbean.oan.dashboard.core.controller;

import com.bloxbean.oan.dashboard.core.service.AccountService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.math.BigInteger;

@Controller("/")
public class AccountController {
    private Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Inject
    private AccountService accountService;

    @Get(uri = "/accounts/{address}/balance")
    public String getAccountBalance(String address) {
        BigInteger balance = accountService.getBalance(address);

        return createGetBalanceResponse(balance);
    }

    @Get(uri = "/{network}/accounts/{address}/balance")
    public String getAccountBalanceByNetwork(String network, String address) {
        BigInteger balance = accountService.getBalanceByNetwork(network, address);
        return createGetBalanceResponse(balance);
    }

    private String createGetBalanceResponse(BigInteger balance) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("balance", balance);
        jsonObject.put("balanceAion", CryptoUtil.ampToAion(balance));

        return jsonObject.toString();
    }

}
