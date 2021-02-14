package com.bloxbean.oan.dashboard.model;

import com.bloxbean.oan.dashboard.model.PoolMetaData;

public class Staker {
    private String identity;
    private String signingAddress;
    private String coinbaseAddress;
    private String managementAddress;

    private double stakeAmtInAion;
    private boolean isSolo;
    private PoolMetaData poolMetaData; //For pool validators

    public Staker() {

    }

    public Staker(String address, double stakeAmtInAion) {
        this.identity = address;
        this.stakeAmtInAion = stakeAmtInAion;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getSigningAddress() {
        return signingAddress;
    }

    public void setSigningAddress(String signingAddress) {
        this.signingAddress = signingAddress;
    }

    public String getCoinbaseAddress() {
        return coinbaseAddress;
    }

    public void setCoinbaseAddress(String coinbaseAddress) {
        this.coinbaseAddress = coinbaseAddress;
    }

    public String getManagementAddress() {
        return managementAddress;
    }

    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }

    public double getStakeAmtInAion() {
        return stakeAmtInAion;
    }

    public void setStakeAmtInAion(double stakeAmtInAion) {
        this.stakeAmtInAion = stakeAmtInAion;
    }

    public boolean isSolo() {
        return isSolo;
    }

    public void setSolo(boolean solo) {
        isSolo = solo;
    }

    public PoolMetaData getPoolMetaData() {
        return poolMetaData;
    }

    public void setPoolMetaData(PoolMetaData poolMetaData) {
        this.poolMetaData = poolMetaData;
    }
}
