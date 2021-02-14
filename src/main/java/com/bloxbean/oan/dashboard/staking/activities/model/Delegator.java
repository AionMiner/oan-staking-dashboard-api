package com.bloxbean.oan.dashboard.staking.activities.model;

public class Delegator {
    private String address;
    private double stakeAmtInAion;

    public Delegator() {

    }

    public Delegator(String address, double stakeAmtInAion) {
        this.address = address;
        this.stakeAmtInAion = stakeAmtInAion;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getStakeAmtInAion() {
        return stakeAmtInAion;
    }

    public void setStakeAmtInAion(double stakeAmtInAion) {
        this.stakeAmtInAion = stakeAmtInAion;
    }
}
