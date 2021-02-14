package com.bloxbean.oan.dashboard.core.service;

import com.bloxbean.oan.dashboard.common.annotation.ScheduleTaskTracker;
import com.bloxbean.oan.dashboard.staking.pools.services.PoolRedisService;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;

@Singleton
public class CryptoRateService {
    private final static Logger logger = LoggerFactory.getLogger(CryptoRateService.class);
    private final static String RATE_FETCH_TIMER = "RATE_FETCH_TIMER";

    @Inject
    private PoolRedisService redisService;

    @Value("${rate_api_url}")
    private String rateApiUrl;

    @Scheduled(fixedRate = "1h")
    @ScheduleTaskTracker(RATE_FETCH_TIMER)
    public void fetchAionExchangeRate() {
        try {
            HttpResponse<JsonNode> response = Unirest.get(rateApiUrl)
                    .asJson();
            if(response.getStatus() == 200) {
                JsonNode jsonNode = response.getBody();
                BigDecimal price = jsonNode.getObject().getBigDecimal("USD");
                System.out.println("Price >> " + price);

                if(price != null)
                    redisService.addCryptoPriceToCache("AION", "USD", price);
            } else {
                logger.error("Error getting price from aion. Status code: " + response.getStatus());
            }
        } catch (UnirestException e) {
            logger.error("Error getting price info", e);
        }
    }

    public String getPrice(String sym, String currency) {
        return redisService.getCryptoPriceFromCache(sym, currency);
    }
}
