package com.bloxbean.oan.dashboard.util;

import com.bloxbean.oan.dashboard.model.Pool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.oan.dashboard.model.PoolMetaData;
import org.aion.base.util.ByteUtil;
import org.aion4j.avm.helper.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PoolInfoLoader {
    private static Logger logger = LoggerFactory.getLogger(PoolInfoLoader.class);

    public static  List<Pool> parsePoolsJson(String res) throws Exception {
        JsonNode jsonNode = JsonUtil.getObjectMapper().readTree(res);
        JsonNode resultNode = jsonNode.get("result");

        if(!resultNode.isArray()) {
            logger.error("Invalid json ");
            return Collections.EMPTY_LIST;
        }

        List<Pool> pools = new ArrayList<>();
        for(int i=0; i<resultNode.size(); i++) {
            JsonNode poolNode = resultNode.get(i);
            Pool pool = new Pool();
            String dataHex = poolNode.get("data").asText();
            String url = new String(HexUtil.hexStringToBytes(dataHex));

            JsonNode topicsNode = poolNode.get("topics");
            String identity = topicsNode.get(1).asText();
            String commissionHex = topicsNode.get(2).asText();
            BigInteger commission = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(commissionHex));

            pool.setValidatorAddress(identity);
            pool.setMetadataUrl(url);
            pool.setCommission(commission.toString());

            pools.add(pool);
        }

        return pools;
    }

    public static PoolMetaData loadPoolMetaData(String url, String identity) {
        try {
            String content = CurlExecutor.download(url);//FileDownloader.downloadFile(url);

            if(content == null || content.isEmpty()) {
                logger.error(">> Error parsing metadata url. Empty conten : " + url);
                return new PoolMetaData();
            }
            ObjectMapper objectMapper = JsonUtil.getObjectMapper();
            PoolMetaData metaData = null;
            try {
                metaData = objectMapper.readValue(content, PoolMetaData.class);
            } catch (IOException e) {
                logger.error("url could not be parsed: " + url);
                e.printStackTrace();
                return new PoolMetaData();
            }
            logger.info("Meta data successfully fetched for validator {}, from url {}", identity, url);
            return metaData;
        } catch (Exception e) {
            logger.error("Error parsing metadata url : " + url, e);
            return new PoolMetaData();
        }
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        PoolMetaData metaData = objectMapper.readValue(new File("/Users/satya/work/bloxbean/bloxbean.github.io/stake/metadata.json"), PoolMetaData.class);
        System.out.println(metaData);
    }

}
