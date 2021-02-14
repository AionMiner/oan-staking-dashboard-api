package com.bloxbean.oan.dashboard.staking.activities.model;

import com.bloxbean.oan.dashboard.model.PoolMetaData;

import java.util.ArrayList;
import java.util.List;

public class DelegatorPools {
    private String delegator;
    private List<Stake> stakes = new ArrayList<>();

    public String getDelegator() {
        return delegator;
    }

    public void setDelegator(String delegator) {
        this.delegator = delegator;
    }

    public List<Stake> getStakes() {
        return stakes;
    }

    public void addStake(Stake stake) {
        this.stakes.add(stake);
    }

    public static class Stake {
        private String validator;
        private PoolMetaData poolMetaData;
        private double stakeAmtInAion;
        private double commission;

        public Stake(String validator, double stakeAmtInAion) {
            this.validator = validator;
            this.stakeAmtInAion = stakeAmtInAion;
        }

        public String getValidator() {
            return validator;
        }

        public void setValidator(String validator) {
            this.validator = validator;
        }

        public double getStakeAmtInAion() {
            return stakeAmtInAion;
        }

        public void setStakeAmtInAion(double stakeAmtInAion) {
            this.stakeAmtInAion = stakeAmtInAion;
        }

        public PoolMetaData getPoolMetaData() {
            return poolMetaData;
        }

        public void setPoolMetaData(PoolMetaData poolMetaData) {
            this.poolMetaData = poolMetaData;
        }

        public double getCommission() {
            return commission;
        }

        public void setCommission(double commission) {
            this.commission = commission;
        }
    }
}
