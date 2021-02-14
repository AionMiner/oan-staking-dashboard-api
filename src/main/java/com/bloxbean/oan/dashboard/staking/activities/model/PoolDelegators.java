package com.bloxbean.oan.dashboard.staking.activities.model;

import java.util.ArrayList;
import java.util.List;

public class PoolDelegators {
    private String validator;
    private double stateAmtInAion;
    private long total;

    private List<Staker> delegators = new ArrayList<>();

    public String getValidator() {
        return validator;
    }

    public void setValidator(String validator) {
        this.validator = validator;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<Staker> getDelegators() {
        return delegators;
    }

    public void addDelegator(Staker staker) {
        delegators.add(staker);
    }

    public double getStateAmtInAion() {
        return stateAmtInAion;
    }

    public void setStateAmtInAion(double stateAmtInAion) {
        this.stateAmtInAion = stateAmtInAion;
    }

    public static class Staker {
        private String delegator;
        private double stakeAmtInAion;

        public Staker() {
        }

        public Staker(String delegator, double balance) {
            this.delegator = delegator;
            this.stakeAmtInAion = balance;
        }

        public String getDelegator() {
            return delegator;
        }

        public void setDelegator(String delegator) {
            this.delegator = delegator;
        }

        public double getStakeAmtInAion() {
            return stakeAmtInAion;
        }

        public void setStakeAmtInAion(double stakeAmtInAion) {
            this.stakeAmtInAion = stakeAmtInAion;
        }
    }

}
