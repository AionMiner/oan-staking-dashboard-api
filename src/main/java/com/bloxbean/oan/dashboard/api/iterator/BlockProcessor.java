package com.bloxbean.oan.dashboard.api.iterator;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;

import java.math.BigInteger;

public interface BlockProcessor {
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException;
}
