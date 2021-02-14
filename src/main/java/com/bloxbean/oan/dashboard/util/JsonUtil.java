package com.bloxbean.oan.dashboard.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {
    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    public static String toJson(Object object) {
        try {
            String content = objectMapper.writeValueAsString(object);
            return content;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Error creating  object", e);
        }
        return null;
    }

    public static <T> T fromJson(String json, Class<T> tClass) {
        try {
            return (T) objectMapper.readValue(json, tClass);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error converting json to object", e);
            return null;
        }
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
