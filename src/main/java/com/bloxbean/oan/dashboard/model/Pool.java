package com.bloxbean.oan.dashboard.model;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Pool {
    private String validatorAddress;
    private String coinbaseAddress;
    private String signingAddress;
    private String commission;
    private String metadataUrl;
    private boolean active;
    private PoolMetaData poolMetaData;

    //stakes
    private BigInteger totalStake;
    private BigInteger pendingStake;
    private BigDecimal totalStakeAion;
    private BigDecimal pendingStakeAion;

    private BigInteger selfStake;
    private BigDecimal selfStakeAion;

    private BigInteger outstandingRewards;
    private BigDecimal outstandingRewardsAion;

    private String mystake = "-1";
    private String myrewards = "-1";

    public String getValidatorAddress() {
        return validatorAddress;
    }

    public void setValidatorAddress(String validatorAddress) {
        this.validatorAddress = validatorAddress;
    }

    public String getCoinbaseAddress() {
        return coinbaseAddress;
    }

    public void setCoinbaseAddress(String coinbaseAddress) {
        this.coinbaseAddress = coinbaseAddress;
    }

    public String getSigningAddress() {
        return signingAddress;
    }

    public void setSigningAddress(String signingAddress) {
        this.signingAddress = signingAddress;
    }

    public String getCommission() {
        return commission;
    }

    public void setCommission(String commission) {
        this.commission = commission;
    }

    public String getMetadataUrl() {
        return metadataUrl;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public PoolMetaData getPoolMetaData() {
        return poolMetaData;
    }

    public void setPoolMetaData(PoolMetaData poolMetaData) {
        this.poolMetaData = poolMetaData;
    }

    public BigInteger getTotalStake() {
        return totalStake;
    }

    public void setTotalStake(BigInteger totalStake) {
        this.totalStake = totalStake;
    }

    public BigInteger getPendingStake() {
        return pendingStake;
    }

    public void setPendingStake(BigInteger pendingStake) {
        this.pendingStake = pendingStake;
    }

    public BigDecimal getTotalStakeAion() {
        return totalStakeAion;
    }

    public void setTotalStakeAion(BigDecimal totalStakeAion) {
        this.totalStakeAion = totalStakeAion;
    }

    public BigDecimal getPendingStakeAion() {
        return pendingStakeAion;
    }

    public void setPendingStakeAion(BigDecimal pendingStakeAion) {
        this.pendingStakeAion = pendingStakeAion;
    }

    public BigInteger getSelfStake() {
        return selfStake;
    }

    public void setSelfStake(BigInteger selfStake) {
        this.selfStake = selfStake;
    }

    public BigDecimal getSelfStakeAion() {
        return selfStakeAion;
    }

    public void setSelfStakeAion(BigDecimal selfStakeAion) {
        this.selfStakeAion = selfStakeAion;
    }

    public BigInteger getOutstandingRewards() {
        return outstandingRewards;
    }

    public void setOutstandingRewards(BigInteger outstandingRewards) {
        this.outstandingRewards = outstandingRewards;
    }

    public BigDecimal getOutstandingRewardsAion() {
        return outstandingRewardsAion;
    }

    public void setOutstandingRewardsAion(BigDecimal outstandingRewardsAion) {
        this.outstandingRewardsAion = outstandingRewardsAion;
    }

    public String getMystake() {
        return mystake;
    }

    public void setMystake(String mystake) {
        this.mystake = mystake;
    }

    public String getMyrewards() {
        return myrewards;
    }

    public void setMyrewards(String myrewards) {
        this.myrewards = myrewards;
    }

    @Override
    public String toString() {
        return "Pool{" +
                "validatorAddress='" + validatorAddress + '\'' +
                ", commission='" + commission + '\'' +
                ", metadataUrl='" + metadataUrl + '\'' +
                ", poolMetaData=" + poolMetaData +
                '}';
    }
}
