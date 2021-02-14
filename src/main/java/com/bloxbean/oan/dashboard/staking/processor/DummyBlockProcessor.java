package com.bloxbean.oan.dashboard.staking.processor;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;

import java.math.BigInteger;

public class DummyBlockProcessor implements BlockProcessor {
    @Override
    public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
        System.out.println("Processing blocks from : " + fromBlock + " to " + toBlock);
    }
}
