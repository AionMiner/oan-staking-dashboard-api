package com.bloxbean.oan.dashboard.staking.pools.services;

import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.bloxbean.oan.dashboard.util.PoolInfoLoader;
import com.bloxbean.oan.dashboard.util.Tuple;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.annotation.Scheduled;
import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import com.bloxbean.oan.dashboard.common.annotation.ScheduleTaskTracker;
import com.bloxbean.oan.dashboard.model.PoolMetaData;
import com.bloxbean.oan.dashboard.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Singleton
public class PoolRedisService {
    private final static Logger logger = LoggerFactory.getLogger(PoolRedisService.class);

    private final static String POOLS = "pools";
    private static final String POOLS_MAP = "pools_map";
    private static final String POOLS_META_DATA = "pools_metadata";
    private static final String POOLS_LOGO_MAP = "pools_logo";
    private final static String POOL_INFO_UPDATED_TIME = "pools_info_last_updated_time";
    private final static String POOL_METADATA_UPDATED_TIME = "pools_metadata_last_updated_time";

    //Timers
    private final static String POOL_INFO_FETCH_TIMER = "POOL_INFO_FETCHER_TIMER";
    private final static String POOL_META_DATA_LOADER_TIMER = "POOL_META_DATA_LOADER_TIMER";

    @Inject
    StatefulRedisConnection<String, String> connection;

    @Inject
    PoolRegistryService poolRegistryService;

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @Scheduled(fixedRate = "5m")
    @ScheduleTaskTracker(POOL_INFO_FETCH_TIMER)
    public void putPoolsInfo() throws JsonProcessingException {
        logger.info("Loading pool info to cache ...");
        List<Pool> pools = poolRegistryService.getPools();

        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        //Get metadata info
        for(Pool pool: pools) {
            PoolMetaData poolMetaData = PoolInfoLoader.loadPoolMetaData(pool.getMetadataUrl(), pool.getValidatorAddress());

            if(poolMetaData == null || StringUtils.isEmpty(poolMetaData.getUrl())) {
                //Get from existing cache
                Pool cachedPool = getPool(pool.getValidatorAddress());
                if(cachedPool != null)
                    pool.setPoolMetaData(cachedPool.getPoolMetaData());
            }
            pool.setPoolMetaData(poolMetaData);
        }

        Map<String, String> poolsMap = new HashMap<>();
        Map<String, String> poolLogos = new HashMap<>();
        for(Pool pool: pools) {

            try {
                //store pull images separately
                if (pool.getPoolMetaData() != null && !StringUtils.isEmpty(pool.getPoolMetaData().getLogo())) {
                    if (pool.getPoolMetaData().getLogo().trim().startsWith("data")) {
                        poolLogos.put(pool.getValidatorAddress(), pool.getPoolMetaData().getLogo());

                        pool.getPoolMetaData().setLogoUrl(pool.getValidatorAddress() + ".img");
                    } else if (pool.getPoolMetaData().getLogo().startsWith("http")) {
                        pool.getPoolMetaData().setLogoUrl(pool.getPoolMetaData().getLogo());
                    }
                }
            } catch (Exception e) {
                logger.error("Error populating preparing logo image for pool: " + pool.getValidatorAddress());
            }

            poolsMap.put(pool.getValidatorAddress(), objectMapper.writeValueAsString(pool));
        }

        pools.sort(new Comparator<Pool>() {
            @Override
            public int compare(Pool o1, Pool o2) {
                return o2.getTotalStake().compareTo(o1.getTotalStake());
            }
        });
        connection.sync().hmset(POOLS_MAP, poolsMap);

        String poolsStr = objectMapper.writeValueAsString(pools);

        if(logger.isDebugEnabled())
            logger.debug(poolsStr);

        connection.sync().set(POOLS, poolsStr);
        connection.sync().hmset(POOLS_LOGO_MAP, poolLogos);
        connection.sync().set(POOL_INFO_UPDATED_TIME, DateUtil.currentTimeInGMT());
        connection.flushCommands();

        logger.info("Pools info loaded successfully");
    }

    @Scheduled(fixedRate = "57m")
    @ScheduleTaskTracker(POOL_META_DATA_LOADER_TIMER)
    public void loadPoolsMetaData() throws JsonProcessingException {
        List<Pool> pools = getPools();
        if(pools == null || pools.size() == 0)
            return;

        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        Map<String, String> poolMetaDataMap = new HashMap<>();
        //Get metadata info
        for(Pool pool: pools) {
            PoolMetaData poolMetaData = PoolInfoLoader.loadPoolMetaData(pool.getMetadataUrl(), pool.getValidatorAddress());

            if(poolMetaData == null || StringUtils.isEmpty(poolMetaData.getUrl())) {
                //Get from existing cache
                Pool cachedPool = getPool(pool.getValidatorAddress());
                if(cachedPool != null)
                    poolMetaData = cachedPool.getPoolMetaData();

            }

            poolMetaDataMap.put(pool.getValidatorAddress(), objectMapper.writeValueAsString(poolMetaData));
        }

        connection.sync().hmset(POOLS_META_DATA, poolMetaDataMap);
        connection.sync().set(POOL_METADATA_UPDATED_TIME, DateUtil.currentTimeInGMT());

        logger.info("Pools metadata loaded successfully");
    }

    public List<Pool> getPools() {
        String content = connection.sync().get(POOLS);
        if(content == null || content.isEmpty())
            return Collections.EMPTY_LIST;

        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        Pool[] pools = new Pool[0];
        try {
            pools = objectMapper.readValue(content, Pool[].class);
        } catch (IOException e) {
            return Collections.EMPTY_LIST;
        }

        List<Pool> poolsList = Arrays.asList(pools);
        return poolsList;
    }

    public String getLastUpdatedTimeForPoolInfo() {
        return connection.sync().get(POOL_INFO_UPDATED_TIME);
    }

    public Pool getPool(String validatorAddress) {
        String content = connection.sync().hget(POOLS_MAP, validatorAddress);
        if(content == null || content.isEmpty())
            return null;

        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        Pool pool = null;
        try {
            pool = objectMapper.readValue(content, Pool.class);
        } catch (IOException e) {
            return null;
        }

        return pool;
    }

    public Map<String, PoolMetaData> getPoolsMetaData() {
        Map<String, String> poolsMetaData = connection.sync().hgetall(POOLS_META_DATA);

        if(poolsMetaData == null || poolsMetaData.isEmpty())
            return Collections.EMPTY_MAP;

        Map<String, PoolMetaData> poolMetaDataMapMap = new HashMap<>();
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        for(String key : poolsMetaData.keySet()) {
            try {
                PoolMetaData metaData = objectMapper.readValue(poolsMetaData.get(key), PoolMetaData.class);
                poolMetaDataMapMap.put(key, metaData);
            } catch (IOException e) {
                logger.error("Error de-serializing pool metadata info", e);
            }
        }
        return poolMetaDataMapMap;
    }

    public Tuple<byte[], String> getPoolLogo(String validationAddress) {
        if(StringUtils.isEmpty(validationAddress)) return null;

        String logoText = connection.sync().hget(POOLS_LOGO_MAP, validationAddress);
        if(!StringUtils.isEmpty(logoText)) {
            String[] split = logoText.split(";base64,");
            String type = split[0].substring(5);

            byte[] logo = decodeToImage(split[1]);
            if(logo != null)
                return new Tuple(logo, type);
            else
                return new Tuple(new byte[0], "image/png");
        } else
            return null;
    }

    public String getLastUpdatedTimeForPoolsMetaData() {
        return connection.sync().get(POOL_METADATA_UPDATED_TIME);
    }

    public void addCryptoPriceToCache(String sym, String currency, BigDecimal price) {
        connection.async().set(getPriceKey(sym, currency), price.toString());
    }

    public String getCryptoPriceFromCache(String sym, String currency) {
        return connection.sync().get(getPriceKey(sym, currency));
    }

    private static byte[] decodeToImage(String data) {
        try {
            byte[] imageByte = Base64.getDecoder().decode(data);
            return imageByte;
        } catch (Exception e) {
            logger.error("Error decoding image base64 data to byte[] ", e);
        }
        return new byte[0];
    }

    private String getPriceKey(String sym, String currency) {
        return sym + ":" + currency;
    }

}
