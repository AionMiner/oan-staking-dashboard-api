package com.bloxbean.oan.dashboard.staking.rating.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RatingReponse {
    private String lastUpdatedTime;
    private long total;
    private double avgRating;
    private List<Rating> ratings;
}
