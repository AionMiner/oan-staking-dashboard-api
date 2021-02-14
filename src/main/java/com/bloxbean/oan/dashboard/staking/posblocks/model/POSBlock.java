package com.bloxbean.oan.dashboard.staking.posblocks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class POSBlock {
    private String blockNumber;
    private String coinbase;
    private double stakeWeight;
    private long timestamp;
}
