package com.bloxbean.oan.dashboard.staking.activities.service;

import com.bloxbean.oan.dashboard.core.service.ChainService;
import com.bloxbean.oan.dashboard.staking.activities.model.*;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.Range;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.oan.dashboard.model.Block;
import com.bloxbean.oan.dashboard.util.DateUtil;
import org.aion4j.avm.helper.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
@Slf4j
public class StakeActivityStreamService {

    private final static String ACTIVITY_STREAM_KEY = "stake_activity_stream";
    private final static String FINALIZATION_BUCKET_KEY_PREFIX = "finalization_activity_stream_";
    private final static String FINALIZED_IDS_KEY_PREFIX = "finalized_ids_";
    private final static ObjectMapper objectMapper = JsonUtil.getObjectMapper();

    private final long FINALIZATION_KEY_TIME_OUT = 60 * 60 * 24 * 10;

    @Inject
    private StatefulRedisConnection<String, String> connection;

    @Inject
    private ChainService chainService;

    public void newActivity(Activity activity) {
        activity.setUpdatedTime(DateUtil.currentTimeInGMT()); //Set when the actvity was updated in cache.
        updateBlockTimeInAcitity(activity);

        //Global bucket
        deleteIfSameActivityAlreadyExists(ACTIVITY_STREAM_KEY, activity); //delete first if exists
        connection.sync().zadd(ACTIVITY_STREAM_KEY, activity.getBlock().doubleValue(), JsonUtil.toJson(activity));
        connection.sync().zremrangebyrank(ACTIVITY_STREAM_KEY, 0, -5001); //Top 1000s

        //Global action specific bucket
        addNewActivityToGlobalActionBucket(activity);

        String validator = activity.getValidator();
        if(!StringUtils.isEmpty(validator)) {
            //validator's main activity stream
            String validatorActivityStream = getValidatorActivityKey(validator);
            deleteIfSameActivityAlreadyExists(validatorActivityStream, activity);
            connection.sync().zadd(validatorActivityStream, activity.getBlock().doubleValue(), JsonUtil.toJson(activity));
            connection.sync().zremrangebyrank(validatorActivityStream, 0, -1001); //Top 1000s

            //validator's action specific activity stream
            String actionActivityStream = getValidatorActionSpecificActivityStream(validator, activity.getAction());
            deleteIfSameActivityAlreadyExists(actionActivityStream, activity); //delete first if exists
            connection.sync().zadd(actionActivityStream,
                    activity.getBlock().doubleValue(), JsonUtil.toJson(activity));
            connection.sync().zremrangebyrank(actionActivityStream, 0, -1001); //Top 1000s

           // try {
                //If Activity has an id, so finalization is required. So let' start tracking all pending finalization.
                newActivityStreamWithIdRequiredFinalization(activity);
//            } catch (Exception e) {
//                log.error("Error adding new pedning finalization event", e);
//            }
        }
    }

    private void addNewActivityToGlobalActionBucket(Activity activity) {
        if(activity == null || StringUtils.isEmpty(activity.getAction())) return;

        String key = getGlobalActionSpecificActivityStream(activity.getAction());

        deleteIfSameActivityAlreadyExists(key, activity); //delete first if exists
        connection.sync().zadd(key, activity.getBlock().doubleValue(), JsonUtil.toJson(activity));
        connection.sync().zremrangebyrank(key, 0, -5001); //Top 1000s
    }

    private void deleteIfSameActivityAlreadyExists(String key, Activity activity) {
        List<String> list = connection.sync().zrangebyscore(key, Range.create(activity.getBlock().doubleValue(), activity.getBlock().doubleValue()));
        if(list == null || list.size() == 0)
            return;

        if(log.isDebugEnabled())
            log.debug("Found for existing activity >>>>>>> " + JsonUtil.toJson(activity));

        for(String as: list) {
            Activity ac = JsonUtil.fromJson(as, Activity.class);
            if(activity.equals(ac)) { //Same activity found let's cleanup
                if(log.isDebugEnabled())
                    log.debug("Remove duplicate activity record >>>> " + as);

                connection.sync().zrem(key, as);
            }
        }
    }

    //For finalization ids
    private void newActivityStreamWithIdRequiredFinalization(Activity activity) {
        if(activity.getId() == null || activity.getId() == 0 || activity.getId() == -1)
            return;

        if(StringUtils.isEmpty(activity.getAction()))
            return;

        String delegator = activity.getDelegator();
        String key = getDelegatorActivityStreamKeyForFinalization(delegator, activity.getAction());

        String globalFinalizationActionSpecificKey = getGlobalFinalizationActionSpecificActivityStream(activity.getAction());

        connection.sync().zremrangebyscore(globalFinalizationActionSpecificKey, Range.create(activity.getId(), activity.getId())); //delete before add
        connection.sync().zadd(globalFinalizationActionSpecificKey, activity.getId(), JsonUtil.toJson(activity));

        connection.sync().zremrangebyscore(key, Range.create(activity.getId(), activity.getId())); //remove delegator's finalization bucket and then add
        connection.sync().zadd(key, activity.getId(), JsonUtil.toJson(activity));

        //set expiry for
        connection.sync().expire(key, FINALIZATION_KEY_TIME_OUT);

        //keep top
        connection.sync().zremrangebyrank(globalFinalizationActionSpecificKey, 0, -25001); //Top 50000s finalization events
    }

    public void finalizeUndelegatedActivity(long id, BigInteger blockNumber) {
        String globalUndelegatedFinalizationStreamingKey = getGlobalFinalizationActionSpecificActivityStream(UnDelegation.ACTION_KEY);

        List<String> list = connection.sync().zrangebyscore(globalUndelegatedFinalizationStreamingKey, Range.create(id, id));

        if(list == null || list.size() == 0)
            return;

        String activityStr = list.get(0);
        Activity activity = JsonUtil.fromJson(activityStr, Activity.class);
        if(activity == null)
            return;

        //Update status to finalize and save
        activity.setFinalized(true);
        activity.setFinalizeBlock(blockNumber);

        connection.sync().zremrangebyscore(globalUndelegatedFinalizationStreamingKey, Range.create(id, id)); //Remove and then add updated one to main list.
        connection.sync().zadd(globalUndelegatedFinalizationStreamingKey, id, JsonUtil.toJson(activity));

        String delegator = activity.getDelegator();
        if(StringUtils.isEmpty(delegator) || StringUtils.isEmpty(activity.getAction())) return;

        String key = getDelegatorActivityStreamKeyForFinalization(delegator, activity.getAction());

        //delete activity from delegator's bucket
        connection.sync().zremrangebyscore(key, Range.create(id, id));
        if(connection.sync().zcard(key) == 0)
            connection.sync().del(key);

        //Add id to finalized ids bucket
        connection.sync().zadd(getGlobalFinalizedIdsZSetKey(activity.getAction()), id, String.valueOf(id));
        connection.sync().zremrangebyrank(getGlobalFinalizedIdsZSetKey(activity.getAction()), 0, -10001); //Keep 10000
    }

    public String getValidatorActivityKey(String validator) {
        return validator + ".activity_stream";
    }

    public String getValidatorActionSpecificActivityStream(String validator, String action) {
        return validator + ".activity_stream_" + action;
    }

    public String getGlobalActionSpecificActivityStream(String action) {
        return ACTIVITY_STREAM_KEY + "_" + action;
    }

    public String getDelegatorActivityStreamKeyForFinalization(String delegator, String action) {
        return delegator + "_pending_finalizations_" + action ;
    }

    public String getGlobalFinalizationActionSpecificActivityStream(String action) {
        return FINALIZATION_BUCKET_KEY_PREFIX + action;
    }

    public String getGlobalFinalizedIdsZSetKey(String action) {
        return FINALIZED_IDS_KEY_PREFIX + action;
    }

    public ActivitiesResponse getAllRecentActivities(long start, long end) {
        List<String> list = connection.sync()
                .zrevrange(ACTIVITY_STREAM_KEY, start, end);

        long total = connection.sync()
                .zcard(ACTIVITY_STREAM_KEY);

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<Activity> activities = new ArrayList<>();
            for (String content : list) {
                try {
                    Activity activity = objectMapper.readValue(content, Activity.class);
                    activities.add(activity);
                } catch (IOException e) {
                    log.error("Error deserializing activity json to Activity object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(activities);
        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }



        return activitiesResponse;
    }

    public ActivitiesResponse getAllRecentActivitiesForValidator(String validator, long start, long end) {
        List<String> list = connection.sync()
                .zrevrange(getValidatorActivityKey(validator), start, end);

        long total = connection.sync()
                .zcard(getValidatorActivityKey(validator));

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<Activity> activities = new ArrayList<>();
            for (String content : list) {
                try {
                    Activity activity = objectMapper.readValue(content, Activity.class);
                    activities.add(activity);
                } catch (IOException e) {
                    log.error("Error deserializing activity json to Activity object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(activities);
        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }

        return activitiesResponse;
    }

    public ActivitiesResponse getRecentWithdrawalsActivities(String validatorAddress, long start, long end) {
        List<String> list = connection.sync()
                .zrevrange(getValidatorActionSpecificActivityStream(validatorAddress, Withdrawal.ACTION_KEY), start, end);

        long total = connection.sync()
                .zcard(getValidatorActionSpecificActivityStream(validatorAddress, Withdrawal.ACTION_KEY));

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<Withdrawal> withdrawals = new ArrayList<>();
            for (String content : list) {
                try {
                    Withdrawal withdrawal = objectMapper.readValue(content, Withdrawal.class);
                    withdrawals.add(withdrawal);
                } catch (IOException e) {
                    log.error("Error deserializing withdrwal json to withdrawal object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(withdrawals);

        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }

        return activitiesResponse;
    }

    public ActivitiesResponse getRecentDelegationActivities(String validatorAddress, long start, long end) {
        List<String> list = connection.sync()
                .zrevrange(getValidatorActionSpecificActivityStream(validatorAddress, Delegation.ACTION_KEY), start, end);

        long total = connection.sync().zcard(getValidatorActionSpecificActivityStream(validatorAddress, Delegation.ACTION_KEY));

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<Delegation> delegations = new ArrayList<>();
            for (String content : list) {
                try {
                    Delegation delegation = objectMapper.readValue(content, Delegation.class);
                    delegations.add(delegation);
                } catch (IOException e) {
                    log.error("Error deserializing delegation json to Delegation object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(delegations);
        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }

        return activitiesResponse;
    }

    public ActivitiesResponse<UnDelegation> getRecentUnDelegationActivities(String validatorAddress, long start, long end) {
        List<String> list = connection.sync()
                .zrevrange(getValidatorActionSpecificActivityStream(validatorAddress, UnDelegation.ACTION_KEY), start, end);

        long total = connection.sync().zcard(getValidatorActionSpecificActivityStream(validatorAddress, UnDelegation.ACTION_KEY));

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<UnDelegation> unDelegations = new ArrayList<>();
            for (String content : list) {
                try {
                    UnDelegation unDelegation = objectMapper.readValue(content, UnDelegation.class);
                    unDelegations.add(unDelegation);
                } catch (IOException e) {
                    log.error("Error deserializing undelegation json to undelegation object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(unDelegations);
        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }

        return activitiesResponse;
    }

    public ActivitiesResponse getPendingUndelegationForFinalizeActivities(long start, long end) {
        String key = getGlobalFinalizationActionSpecificActivityStream(UnDelegation.ACTION_KEY);
        return getActivitiesForKey(key, start, end);
    }

    public ActivitiesResponse getDelegatorPendingUndelegationForFinalizeActivities(String delegator, Long start, Long end) {
        String key = getDelegatorActivityStreamKeyForFinalization(delegator, UnDelegation.ACTION_KEY);
        return getActivitiesForKey(key, start, end);
    }

    public BigDecimal getPendingUndelegationTotalAmount() {
        long start = 0L;
        long end = 20L;

        boolean isContinue = true;
        BigDecimal totalUndelegationAmt = new BigDecimal("0");

        int isFinalizedCounter = 0;
        while(isContinue) {
            ActivitiesResponse activitiesResponse = getPendingUndelegationForFinalizeActivities(start, end);
            List<Activity> activities = activitiesResponse.getActivities();

            for(Activity activity: activities) {
                if(!activity.isFinalized()) {
                    if(activity.getAmtInAion() != null)
                        totalUndelegationAmt = totalUndelegationAmt.add(activity.getAmtInAion());
                } else {
                    isFinalizedCounter ++; //Check for few records to make sure no adhoc finalization done.
                }
            }

            if(isFinalizedCounter >= 5)
                isContinue = false;
            else {
                start = end + 1;
                end = end + 20;
            }
        }

        return totalUndelegationAmt;
    }

    private ActivitiesResponse getActivitiesForKey(String key, Long start, Long end) {
        List<String> list = connection.sync()
                .zrevrange(key, start, end);

        long total = connection.sync().zcard(key);

        ActivitiesResponse activitiesResponse = new ActivitiesResponse();
        activitiesResponse.setTotal(total);

        if(list != null) {
            List<Activity> activities = new ArrayList<>();
            for (String content : list) {
                try {
                    Activity activity = objectMapper.readValue(content, Activity.class);
                    activities.add(activity);
                } catch (IOException e) {
                    log.error("Error deserializing activity json to Activity object", e);
                    continue;
                }
            }

            activitiesResponse.setActivities(activities);
        } else {
            activitiesResponse.setActivities(Collections.emptyList());
        }

        return activitiesResponse;
    }

    private void updateBlockTimeInAcitity(Activity activity) {
        if(activity.getBlock() == null || activity.getBlockTime() != 0)
            return;

        try {
            //Get block time
            Block block = chainService.getBlock(activity.getBlock().toString());
            activity.setBlockTime(block.getTimestampInMillis());
        } catch (Exception e) {
            log.error("Error getting block data from kernel: " + activity.getBlock());
        }
    }

}
