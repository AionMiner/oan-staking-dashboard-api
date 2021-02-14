package com.bloxbean.oan.dashboard.staking.rating.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidatorRating {
    private String validator; //validator address. Both pools and solo stakers
    private Rating rating;
    private boolean isSolo;
}
