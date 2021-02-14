package com.bloxbean.oan.dashboard.staking.stats;

public interface StakingScheduleTimers {
    //TIMERS start
    public final static String READ_DELEGATION_EVENTS_TIMER = "READ_DELEGATION_EVENTS_TIMER";
    public final static String READ_SOLO_STAKER_EVENTS_TIMER = "READ_SOLO_STAKER_EVENTS_TIMER";
    public final static String TOP_DELEGATORS_TIMER_TIMER = "TOP_DELEGATORS_TIMER";
    public final static String READ_WITHDRAWALS_TIMER_TIMER = "READ_WITHDRAWALS_TIMER";
    public final static String READ_VALIDATOR_BLOCKS_TIMER_TIMER = "READ_VALIDATOR_BLOCKS_TIMER";
    //TIMERS end

    //LOCK NAME
    public final static String RATING_CALCULATION_TIMER = "RATING_CALCULATION_TIMER";
}
