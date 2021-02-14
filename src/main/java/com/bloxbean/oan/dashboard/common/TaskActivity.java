package com.bloxbean.oan.dashboard.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskActivity {
    public final static String TASK_STARTED = "started";
    public final static String TASK_FINISHED = "finished";

    private String id;
    private String name;
    private String action;
    private long timestamp;
    private long timeTaken;

}
