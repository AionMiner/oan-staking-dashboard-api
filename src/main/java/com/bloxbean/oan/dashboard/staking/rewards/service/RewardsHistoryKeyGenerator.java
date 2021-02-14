package com.bloxbean.oan.dashboard.staking.rewards.service;

import io.micronaut.cache.interceptor.DefaultCacheKeyGenerator;
import io.micronaut.core.annotation.AnnotationMetadata;

public class RewardsHistoryKeyGenerator extends DefaultCacheKeyGenerator {
    @Override
    public Object generateKey(AnnotationMetadata annotationMetadata, Object... params) {
        Object obj = super.generateKey(annotationMetadata, params);

        if(obj != null)
            return obj.toString();
        else
            return null;
    }
}
