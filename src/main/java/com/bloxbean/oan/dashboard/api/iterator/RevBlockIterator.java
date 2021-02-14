package com.bloxbean.oan.dashboard.api.iterator;

import com.bloxbean.oan.dashboard.core.exception.Web3CallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class RevBlockIterator {
    private static final Logger logger = LoggerFactory.getLogger(RevBlockIterator.class);
    private long EVENT_READ_BLOCK_BATCH_SIZE = 100;

    private BigInteger lastBlock; //End  block to traverse in rev order
    private BigInteger firstBlock;

    public RevBlockIterator(BigInteger lastBlock, BigInteger firstBlock) {
        this.lastBlock = lastBlock;
        this.firstBlock = firstBlock;
    }

    public void setReadBlockBatchSize(long batchSize) {
        this.EVENT_READ_BLOCK_BATCH_SIZE = batchSize;
    }

    public void iterate(BlockProcessor... processors) {

        if(logger.isDebugEnabled()) {
            logger.debug("last block no: ------ " + lastBlock);
            logger.debug("first block no: --------- " + firstBlock);
        }

        int counter = 1;
        
        BigInteger prevBlockNo = null;
        while (prevBlockNo == null || prevBlockNo.compareTo(firstBlock) == 1) {
            prevBlockNo = lastBlock.subtract(BigInteger.valueOf(EVENT_READ_BLOCK_BATCH_SIZE - 1));

            if (prevBlockNo.compareTo(firstBlock) < 0) {
                prevBlockNo = firstBlock;
            }

            if (lastBlock.compareTo(prevBlockNo) <= 0) {
                System.out.println("Already fetched...");
                break;
            }

            if(logger.isDebugEnabled())
               logger.debug("Log read from block: " + lastBlock + " to " + prevBlockNo);

            System.out.println("Log read from block: " + lastBlock + " to " + prevBlockNo );

            for(BlockProcessor processor: processors)
                processor.process(prevBlockNo, lastBlock);

            lastBlock = prevBlockNo.subtract(BigInteger.valueOf(1));

            System.out.println("Batch >>> " + counter++);
        }


    }

    public BigInteger getLastBlock() {
        return lastBlock;
    }

    public void setLastBlock(BigInteger lastBlock) {
        this.lastBlock = lastBlock;
    }

    public BigInteger getFirstBlock() {
        return firstBlock;
    }

    public void setFirstBlock(BigInteger firstBlock) {
        this.firstBlock = firstBlock;
    }

    //    public BigInteger getInitialBlockNo() {
//        String lastReadBlockNo = connection.sync().get(lastReadBlockKey);
//        if(lastReadBlockNo != null)
//            return new BigInteger(lastReadBlockNo);
//        else
//            return BigInteger.ZERO;
//    }

    public static void main(String[] args) {
        RevBlockIterator revBlockIterator = new RevBlockIterator(BigInteger.valueOf(671), BigInteger.ZERO);
//        revBlockIterator.setFirstBlock(BigInteger.ZERO);
//        revBlockIterator.setLastBlock(BigInteger.valueOf(671));
        revBlockIterator.setReadBlockBatchSize(8);
        BlockProcessor testBlockProcessor = new BlockProcessor() {

            @Override
            public void process(BigInteger fromBlock, BigInteger toBlock) throws Web3CallException {
                logger.info("Process {} to {}", fromBlock, toBlock);
            }
        };

        revBlockIterator.iterate(testBlockProcessor);
    }
}
