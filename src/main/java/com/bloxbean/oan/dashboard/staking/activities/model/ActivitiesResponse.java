package com.bloxbean.oan.dashboard.staking.activities.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitiesResponse<T extends Activity> {
    private long total;

    private List<T> activities;
}
