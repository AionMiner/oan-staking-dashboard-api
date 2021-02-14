package com.bloxbean.oan.dashboard.staking.rating.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoolAvgRating {
    private String validator;
    private double avgRating;
    private String lastUpdatedTime;
    private long fromBlock;
    private long toBlock;

    //Only set during snapshot
    private long toBlockTime;
}
