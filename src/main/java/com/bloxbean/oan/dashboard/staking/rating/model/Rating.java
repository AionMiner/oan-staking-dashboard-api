package com.bloxbean.oan.dashboard.staking.rating.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Rating {
    private double rating;   //totalRating / count
    private double blockWeight;
    private double stakeWeight;
    private long blockNumber;
    private long totalBlocks;
    private long poolBlocks;
    private long timestamp;

    private double weightedAvgRating; //This is totalBlockWeight / totalStakeWeight
    private boolean estimatedStakeWeight;
}
