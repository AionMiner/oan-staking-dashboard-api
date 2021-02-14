package com.bloxbean.oan.dashboard.util;

import org.aion4j.avm.helper.util.StringUtils;

public class EnvUtil {
    public static String getProperty(String key) {
        if(StringUtils.isEmpty(key)) return null;

        String value = System.getProperty(key);
        if(StringUtils.isEmpty(value))
            value = System.getenv(key);

        return value;
    }
}
