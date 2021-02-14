package com.bloxbean.oan.dashboard.staking.stats.service;

import com.bloxbean.oan.dashboard.common.TaskScheduleTracker;
import com.bloxbean.oan.dashboard.common.annotation.ScheduleTaskTracker;
import com.bloxbean.oan.dashboard.api.iterator.BlockIterator;
import com.bloxbean.oan.dashboard.core.service.ChainService;
import com.bloxbean.oan.dashboard.core.service.RemoteNodeAdapterService;
import com.bloxbean.oan.dashboard.model.Staker;
import com.bloxbean.oan.dashboard.staking.rating.RatingSnapshotService;
import com.bloxbean.oan.dashboard.staking.stats.StakingScheduleTimers;
import com.bloxbean.oan.dashboard.staking.activities.model.Delegator;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorPools;
import com.bloxbean.oan.dashboard.staking.activities.model.DelegatorsResult;
import com.bloxbean.oan.dashboard.staking.activities.model.PoolDelegators;
import com.bloxbean.oan.dashboard.staking.activities.processor.DelegatedEventBlockProcessor;
import com.bloxbean.oan.dashboard.staking.activities.processor.FinalizeDoneProcessor;
import com.bloxbean.oan.dashboard.staking.activities.processor.SoloStakerBlockProcessor;
import com.bloxbean.oan.dashboard.staking.activities.processor.WithdrawRewardsProcessor;
import com.bloxbean.oan.dashboard.staking.posblocks.processor.ValidatorBlockProcessor;
import com.bloxbean.oan.dashboard.staking.rating.RatingSchedulerService;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

@Singleton
public class StakedAccountsScheduleService {
    private final static Logger logger = LoggerFactory.getLogger(StakedAccountsScheduleService.class);

    private final static String LAST_READ_BLOCK_NUMBER_DELEGATION = "LAST_READ_BLOCK_NUMBER_FOR_DELEGATION";
    private static final String LAST_READ_BLOCK_NUMBER_SOLO_STAKERS = "LAST_READ_BLOCK_NUMBER_FOR_SOLO_STAKER";
    private static final String LAST_READ_BLOCK_NUMBER_WITHDRAWALS = "LAST_READ_BLOCK_NUMBER_WITHDRAWALS";
    private static final String LAST_READ_VALIDATOR_BLOCKS = "LAST_READ_VALIDATOR_BLOCKS";
    private static final String LAST_READ_BLOCK_FINALIZATION_DONE = "LAST_READ_BLOCK_FINALIZATION_DONE";

    private RemoteNodeAdapterService remoteNodeAdapterService;
    private ChainService chainService;
    private StatefulRedisConnection<String, String> connection;
    private BlockIterator delegatedEventBlockIterator;
    private DelegatedEventBlockProcessor delegatedEventBlockProcessor;

    private BlockIterator soloStakerIterator;
    private SoloStakerBlockProcessor soloStakerBlockProcessor;

    private BlockIterator withdrawalIterator;
    private WithdrawRewardsProcessor withdrawRewardsProcessor;

    private BlockIterator validatorBlockIterator;
    private ValidatorBlockProcessor validatorBlockProcessor;

    private BlockIterator finalizeDoneIterator;
    private FinalizeDoneProcessor finalizeDoneProcessor;

    private BigInteger initialBlockNo = new BigInteger("4591124");

    private BigInteger unitBlockFork = new BigInteger("4721900");

    public BigInteger finalizationDoneStartBlock = new BigInteger("5360454");

    @Value("${batch.node}")
    private boolean isBatch;

    @Inject
    private TaskScheduleTracker taskScheduleTracker;

    @Inject
    private RatingSchedulerService ratingSchedulerService;

    @Inject
    private RatingSnapshotService ratingSnapshotService;

    //Scheduler controls
    private boolean readDelegationSchedulerRunning = false;
    private boolean readStakerSchedulerRunning = false;
    private boolean topDelegationSchedulerRunning = false;
    private boolean readWithdrawalRewardsRunning = false;
    private boolean readValidatorBlocksRunning = false;

    private long validatorTimerCount = 0; //To control how many times rating counter should run

    @Inject
    public StakedAccountsScheduleService(RemoteNodeAdapterService remoteNodeAdapterService,
                                         ChainService chainService, StatefulRedisConnection<String, String> connection,
                                         DelegatedEventBlockProcessor delegatedEventBlockProcessor, SoloStakerBlockProcessor soloStakerBlockProcessor,
                                         WithdrawRewardsProcessor withdrawRewardsProcessor,
                                         ValidatorBlockProcessor validatorBlockProcessor,
                                         FinalizeDoneProcessor finalizeDoneProcessor) {
        this.remoteNodeAdapterService = remoteNodeAdapterService;
        this.chainService = chainService;
        this.connection = connection;

        this.delegatedEventBlockProcessor = delegatedEventBlockProcessor;
        this.soloStakerBlockProcessor = soloStakerBlockProcessor;
        this.withdrawRewardsProcessor = withdrawRewardsProcessor;
        this.validatorBlockProcessor = validatorBlockProcessor;
        this.finalizeDoneProcessor = finalizeDoneProcessor;

        delegatedEventBlockIterator = new BlockIterator(chainService, connection, LAST_READ_BLOCK_NUMBER_DELEGATION, initialBlockNo);
        soloStakerIterator = new BlockIterator(chainService, connection, LAST_READ_BLOCK_NUMBER_SOLO_STAKERS, initialBlockNo);
        withdrawalIterator = new BlockIterator(chainService, connection, LAST_READ_BLOCK_NUMBER_WITHDRAWALS, initialBlockNo);
        validatorBlockIterator = new BlockIterator(chainService, connection, LAST_READ_VALIDATOR_BLOCKS, unitBlockFork, 8640);
        finalizeDoneIterator = new BlockIterator(chainService, connection, LAST_READ_BLOCK_FINALIZATION_DONE, finalizationDoneStartBlock);
    }

    @Scheduled(fixedDelay = "5m")
    @ScheduleTaskTracker(StakingScheduleTimers.READ_DELEGATION_EVENTS_TIMER)
    public void fetchEventLogsForDelegationAndUndelegation() {
        if(readDelegationSchedulerRunning) {//Already running
            logger.info("Delegation event log scheduler already running ...skipping this run...");
            return;
        }

        //taskScheduleTracker.taskStarted(READ_DELEGATION_EVENTS_TIMER);
        readDelegationSchedulerRunning = true;

        try {
            delegatedEventBlockIterator.iterate(delegatedEventBlockProcessor);
            finalizeDoneIterator.iterate(finalizeDoneProcessor);
        } finally {
            readDelegationSchedulerRunning = false;
            //taskScheduleTracker.taskFinished(READ_DELEGATION_EVENTS_TIMER);
        }
    }

    @Scheduled(fixedDelay = "6m")
    @ScheduleTaskTracker(StakingScheduleTimers.READ_SOLO_STAKER_EVENTS_TIMER)
    public void fetchEventLogsForSoloStaking() {
        if(readStakerSchedulerRunning) {
            logger.info("Staker registry event log scheduler already running ...skipping this run...");
            return;
        }

        readStakerSchedulerRunning = true;

//        taskScheduleTracker.taskStarted(READ_SOLO_STAKER_EVENTS_TIMER);

        try {
            soloStakerIterator.iterate(soloStakerBlockProcessor);

            //Update stake amounts
            soloStakerBlockProcessor.updateStakerRankingByStakeAmount();
        } finally {
            readStakerSchedulerRunning = false;
//            taskScheduleTracker.taskFinished(READ_SOLO_STAKER_EVENTS_TIMER);
        }
    }

    @Scheduled(fixedDelay = "15m")
    @ScheduleTaskTracker(StakingScheduleTimers.TOP_DELEGATORS_TIMER_TIMER)
    public void populateTopDelegators() {
        if(topDelegationSchedulerRunning) {//Already running
            logger.info("Top delegation scheduler already running ...skipping this run...");
            return;
        }

//        taskScheduleTracker.taskStarted(TOP_DELEGATORS_TIMER_TIMER);

        topDelegationSchedulerRunning = true;
        try {
            delegatedEventBlockProcessor.populateTopDelegators(0, 50);
        } finally {
            topDelegationSchedulerRunning = false;
//            taskScheduleTracker.taskFinished(TOP_DELEGATORS_TIMER_TIMER);
        }
    }

    @Scheduled(fixedDelay = "6m")
    @ScheduleTaskTracker(StakingScheduleTimers.READ_WITHDRAWALS_TIMER_TIMER)
    public void fetchWithdrawRewardsEvents() {
        if(readWithdrawalRewardsRunning) {
            logger.info("Withdrawal rewards events reading scheduler running ...skipping this run...");
            return;
        }

        readWithdrawalRewardsRunning = true;
//        taskScheduleTracker.taskStarted(READ_WITHDRAWALS_TIMER_TIMER);
        try {
            withdrawalIterator.iterate(withdrawRewardsProcessor);
        } finally {
            readWithdrawalRewardsRunning = false;
//            taskScheduleTracker.taskFinished(READ_WITHDRAWALS_TIMER_TIMER);
        }
    }

    @Scheduled(fixedDelay = "6m") //Don't use @ScheduleTaskTracker here as there is a dependency between jobs
    public void fetchPOSBlocks() {
        if(!isBatch || taskScheduleTracker.isDisableBatch())
            return;

        List<Staker> stakers = getStakers(0, 35, false, false);
        if(stakers == null || stakers.size() < 35) {
            logger.info("Stakers not loaded fully.. don't run fetchPOSBlocks...");
            return;
        }

        if(readValidatorBlocksRunning) {
            logger.info("Validator blocks scheduler running ...skipping this run...");
            return;
        }

        String taskId = taskScheduleTracker.taskStarted(StakingScheduleTimers.READ_VALIDATOR_BLOCKS_TIMER_TIMER);

        readValidatorBlocksRunning = true;
        long startTime = System.currentTimeMillis();

        try {
            validatorBlockIterator.iterate(validatorBlockProcessor);
        } finally {
            long endTime = System.currentTimeMillis();

            readValidatorBlocksRunning = false;
            taskScheduleTracker.taskFinished(StakingScheduleTimers.READ_VALIDATOR_BLOCKS_TIMER_TIMER, taskId, (endTime - startTime));
        }
        //Rating calculation
        if(validatorTimerCount % 2 == 0) { //Run once every 2 times of validatorBlock timer
            logger.info("rating calculation is starting ...");
            ratingSchedulerService.calculate();

            try {
                ratingSnapshotService.takeRatingSnapshot();
            } catch (Exception e) {
                logger.error("Daily rating snapshot failed", e);
            }
            logger.info("rating calculation done");
        }


        validatorTimerCount++;

        if(validatorTimerCount > 10000) //Just reset it. no need to keep counting
            validatorTimerCount = 0;

        logger.info("fetchPosBlocks with rating calculation done.");

    }

    //Get validators
    public Set<String> getValidators() {
        return delegatedEventBlockProcessor.getValidators();
    }

    public PoolDelegators getTopDelegatorsForValidator(String validator, long start, long end) {
        return delegatedEventBlockProcessor.getTopDelegatorsForValidator(validator, start, end);
    }

    public List<Delegator> getTopDelegators(long start, long end) {
        return delegatedEventBlockProcessor.getTopDelegators(start, end);
    }

    public DelegatorPools getAllStakesOfADelegator(String delegator, boolean ignoreLogoData) {
        return delegatedEventBlockProcessor.getAllStakesOfADelegator(delegator, ignoreLogoData);
    }

    public DelegatorsResult getDelegators(String cursor, boolean isFinished) {
        return delegatedEventBlockProcessor.getDelegators(cursor, isFinished);
    }

    //Get methods stakers
    public List<Staker> getStakers(long start, long end, boolean poolInfo, boolean ignoreLogoData) {
        return soloStakerBlockProcessor.getStakers(start, end, poolInfo, ignoreLogoData);
    }

    public List<Staker> getSoloStakers(long start, long end) {
        return soloStakerBlockProcessor.getSoloStakers(start, end);
    }

    public Staker getStaker(String identity) {
        return soloStakerBlockProcessor.getStaker(identity);
    }

    public BigInteger getInitialDelegationEventReadBlockNo() {
        String lastReadBlockNo = connection.sync().get(LAST_READ_BLOCK_NUMBER_DELEGATION);
        if(lastReadBlockNo != null)
            return new BigInteger(lastReadBlockNo);
        else
            return BigInteger.ZERO;
    }

    public BigInteger getInitialStakersEventReadBlockNo() {
        String lastReadBlockNo = connection.sync().get(LAST_READ_BLOCK_NUMBER_SOLO_STAKERS);
        if(lastReadBlockNo != null)
            return new BigInteger(lastReadBlockNo);
        else
            return BigInteger.ZERO;
    }

    public String getDelegationEventLogLastUpdatedTime() {
        return delegatedEventBlockProcessor.getLastUpdatedTime();
    }

    public String getTopDelegatorsLastUpdatedTime() {
        return delegatedEventBlockProcessor.getTopDelegatorsLastUpdatedTime();
    }

    public String getStakesEventLogLastUpdatedTime() {
        return soloStakerBlockProcessor.getLastUpdatedTime();
    }

    public Set<String> getRunningTimers() {
        return taskScheduleTracker.getRunningTimers();
    }
}
