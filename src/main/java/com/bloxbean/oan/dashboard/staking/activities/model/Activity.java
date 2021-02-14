package com.bloxbean.oan.dashboard.staking.activities.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {
    protected String validator;
    protected String action;
    protected String delegator;
    protected BigInteger block;
    protected String txHash;
    protected BigInteger amt;
    protected BigDecimal amtInAion;

    //optional fields
    protected Long id; //only set for few activity
    protected BigInteger fee;
    protected boolean finalized;
    protected BigInteger finalizeBlock;

    //When updated in cache
    protected long blockTime;
    protected String updatedTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Activity activity = (Activity) o;
        return  Objects.equals(validator, activity.validator) &&
                Objects.equals(action, activity.action) &&
                Objects.equals(delegator, activity.delegator) &&
                Objects.equals(block, activity.block) &&
                Objects.equals(txHash, activity.txHash) &&
                Objects.equals(amt, activity.amt);
    }

}
