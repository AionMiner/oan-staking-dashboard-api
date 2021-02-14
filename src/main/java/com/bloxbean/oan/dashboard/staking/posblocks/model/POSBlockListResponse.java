package com.bloxbean.oan.dashboard.staking.posblocks.model;

import java.util.List;

public class POSBlockListResponse {
    private String validator;
    private String coinbaseAddress;
    private long total;

    private List<POSBlock> blocks;
}
