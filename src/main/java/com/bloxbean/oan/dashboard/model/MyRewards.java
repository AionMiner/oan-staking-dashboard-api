package com.bloxbean.oan.dashboard.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class MyRewards {
    private String address;
    private List<PoolStake> poolStakes = new ArrayList<>();

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<PoolStake> getPoolStakes() {
        return poolStakes;
    }

    public void setPoolStakes(List<PoolStake> poolStakes) {
        this.poolStakes = poolStakes;
    }

    public static class PoolStake {
        private String validator;
        private BigInteger stake;
        private BigInteger rewards;
        private BigDecimal stakeAion;
        private BigDecimal rewardsAion;

        public String getValidator() {
            return validator;
        }

        public void setValidator(String validator) {
            this.validator = validator;
        }

        public BigInteger getStake() {
            return stake;
        }

        public void setStake(BigInteger stake) {
            this.stake = stake;
        }

        public BigInteger getRewards() {
            return rewards;
        }

        public void setRewards(BigInteger rewards) {
            this.rewards = rewards;
        }

        public BigDecimal getStakeAion() {
            return stakeAion;
        }

        public void setStakeAion(BigDecimal stakeAion) {
            this.stakeAion = stakeAion;
        }

        public BigDecimal getRewardsAion() {
            return rewardsAion;
        }

        public void setRewardsAion(BigDecimal rewardsAion) {
            this.rewardsAion = rewardsAion;
        }
    }
}
