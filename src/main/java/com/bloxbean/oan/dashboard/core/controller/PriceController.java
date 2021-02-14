package com.bloxbean.oan.dashboard.core.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import com.bloxbean.oan.dashboard.core.service.CryptoRateService;
import org.json.JSONObject;

import javax.inject.Inject;

@Controller("/crypto")
public class PriceController {

    @Inject
    private CryptoRateService cryptoRateService;

    @Get(uri = "/price", produces = MediaType.APPLICATION_JSON)
    public String getPrice(@QueryValue String sym, @QueryValue String currency) {
        String price = cryptoRateService.getPrice(sym, currency);
        JSONObject jsonObject= new JSONObject();
        jsonObject.put("USD", price);

        return jsonObject.toString();
    }
}
