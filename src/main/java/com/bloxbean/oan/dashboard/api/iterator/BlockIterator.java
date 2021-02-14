package com.bloxbean.oan.dashboard.api.iterator;

import com.bloxbean.oan.dashboard.core.service.ChainService;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class BlockIterator {
    private static final Logger logger = LoggerFactory.getLogger(BlockIterator.class);
    private long EVENT_READ_BLOCK_BATCH_SIZE = 100;
    private final static long CONFIRM_BLOCK_DELTA = 10;

    private final String lastReadBlockKey;
    private final BigInteger initialBlockNo;
    private ChainService chainService;
    private StatefulRedisConnection<String, String> connection;

    private int maxBlocksInOneRun = -1;

    public BlockIterator(ChainService chainService, StatefulRedisConnection<String, String> connection, String lastReadBlockKey, BigInteger initialBlockNo) {
//        this.chainService = chainService;
//        this.connection = connection;
//        this.lastReadBlockKey = lastReadBlockKey;
//        this.initialBlockNo = initialBlockNo;
        this(chainService, connection, lastReadBlockKey, initialBlockNo, -1);
    }

    public BlockIterator(ChainService chainService, StatefulRedisConnection<String, String> connection, String lastReadBlockKey, BigInteger initialBlockNo, int maxBlocksInOneRun) {
        this.chainService = chainService;
        this.connection = connection;
        this.lastReadBlockKey = lastReadBlockKey;
        this.initialBlockNo = initialBlockNo;
        this.maxBlocksInOneRun = maxBlocksInOneRun;
    }

    public void setReadBlockBatchSize(long batchSize) {
        this.EVENT_READ_BLOCK_BATCH_SIZE = batchSize;
    }

    public void iterate(BlockProcessor... processors) {
        BigInteger latestBlock = chainService.getLatestBlock();
        latestBlock = latestBlock.subtract(BigInteger.valueOf(CONFIRM_BLOCK_DELTA));

        BigInteger firstBlockNo = getInitialBlockNo();
        if(BigInteger.ZERO.equals(firstBlockNo))
            firstBlockNo = initialBlockNo;
        else
            firstBlockNo = firstBlockNo.add(BigInteger.valueOf(1));

        //Find max blockNo for this run.
        if(maxBlocksInOneRun != -1) {
            BigInteger maxBlockNo = firstBlockNo.add(BigInteger.valueOf(maxBlocksInOneRun));
            if(maxBlockNo.compareTo(latestBlock) == -1) {
                logger.info("Latest Block no {} ", latestBlock);
                latestBlock = maxBlockNo;
                logger.info("This iterator will run till block: {} as maxBlockInOne run is set. " +
                        "First block {}, Max Block in one run: {}", latestBlock, firstBlockNo, maxBlocksInOneRun);
            }
        }
        
        BigInteger toBlockNo = null;
        while (toBlockNo == null || toBlockNo.compareTo(latestBlock) == -1) {
            toBlockNo = firstBlockNo.add(BigInteger.valueOf(EVENT_READ_BLOCK_BATCH_SIZE));

            if (toBlockNo.compareTo(latestBlock) == 1) {
                toBlockNo = latestBlock;
            }

            if (firstBlockNo.compareTo(toBlockNo) >= 0) {
                System.out.println("Already fetched...");
                break;
            }

            if(logger.isDebugEnabled())
               logger.debug("Log read from block: " + firstBlockNo + " to " + toBlockNo +"   latest: " + latestBlock);

            logger.info("Log read from block: {} to: {}  latest: {} , maxBlockToProcess: {}" , firstBlockNo, toBlockNo, latestBlock, maxBlocksInOneRun);

            for(BlockProcessor processor: processors)
                processor.process(firstBlockNo, toBlockNo);

            firstBlockNo = toBlockNo;

            if(toBlockNo != null && toBlockNo.compareTo(BigInteger.ZERO) == 1) {
                connection.sync().set(lastReadBlockKey, String.valueOf(toBlockNo));
            }
        }


    }

    public BigInteger getInitialBlockNo() {
        String lastReadBlockNo = connection.sync().get(lastReadBlockKey);
        if(lastReadBlockNo != null)
            return new BigInteger(lastReadBlockNo);
        else
            return BigInteger.ZERO;
    }
}
