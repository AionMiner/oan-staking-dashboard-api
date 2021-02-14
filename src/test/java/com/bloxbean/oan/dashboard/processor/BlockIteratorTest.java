package com.bloxbean.oan.dashboard.processor;

import com.bloxbean.oan.dashboard.api.iterator.BlockProcessor;
import com.bloxbean.oan.dashboard.core.exception.Web3CallException;

import java.math.BigInteger;

public class BlockIteratorTest {

    public void testMaxBlockNo() {
        BlockProcessor blockProcessor = new BlockProcessor() {
            @Override
            public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
                System.out.println("From : " + fromBlock + "  To block: " + toBlock);
            }
        };

        //BlockIterator blockIterator = new BlockIterator()
    }
}
