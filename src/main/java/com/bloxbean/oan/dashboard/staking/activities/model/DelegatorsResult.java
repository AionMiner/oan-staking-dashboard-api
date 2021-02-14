package com.bloxbean.oan.dashboard.staking.activities.model;

import java.util.List;

public class DelegatorsResult {
    private long total;
    private List<String> delegators;
    private String cursor;
    private boolean isFinished;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<String> getDelegators() {
        return delegators;
    }

    public void setDelegators(List<String> delegators) {
        this.delegators = delegators;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished(boolean finished) {
        isFinished = finished;
    }
}
