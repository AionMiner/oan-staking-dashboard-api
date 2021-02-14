package com.bloxbean.oan.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {
    private String logsBloom;
    private String totalDifficulty;
    private String receiptsRoot;
    private String seed;
    private String extraData;
    private String signature;
    private String nrgUsed;
    private String sealType;
    private String publicKey;
    private String miner;
    private String difficulty;
    private Long number;
    private String gasLimit;
    private String mainChain;
    private String gasUsed;
    private String nrgLimit;
    private String size;
    private String transactionsRoot;
    private String stateRoot;
    private String parentHash;
    private String hash;
    private String timestamp;

    //derived fields
    private long timestampInMillis;
}
