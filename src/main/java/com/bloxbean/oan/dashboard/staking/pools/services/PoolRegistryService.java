package com.bloxbean.oan.dashboard.staking.pools.services;

import avm.Address;
import com.bloxbean.oan.dashboard.model.Pool;
import com.bloxbean.oan.dashboard.util.CurlExecutor;
import com.bloxbean.oan.dashboard.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import com.bloxbean.oan.dashboard.common.NetworkConstants;
import com.bloxbean.oan.dashboard.staking.client.StakedAccountSchedulerClient;
import com.bloxbean.oan.dashboard.model.Staker;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.base.util.ByteUtil;
import org.aion4j.avm.helper.api.logs.Slf4jLog;
import org.aion4j.avm.helper.local.LocalAvmNode;
import org.aion4j.avm.helper.remote.RemoteAVMNode;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class PoolRegistryService {
    private final static Logger logger = LoggerFactory.getLogger(PoolRegistryService.class);

    private final static long gas = 2000000L;
    private final static long gasPrice = 100000000000L;

    @Value("${web3rpc_url}")
    private String web3rpcUrl;

    @Inject
    private StakedAccountSchedulerClient stakedAccountSchedulerClient;

    public PoolRegistryService() {
    }

    public List<Pool> getPools() {
        String topics = "0x414453506f6f6c52656769737465726564000000000000000000000000000000"; //ADSPoolRegistered
        //"0x41445344656c6567617465640000000000000000000000000000000000000000";

        String contractAddress = "0xa008e42a76e2e779175c589efdb2a0e742b40d8d421df2b93a8a0b13090c7cc8";
        RemoteAVMNode remoteAVMNode = getRemoteAvmNode();

        String latestBlock = remoteAVMNode.getLatestBlock();
        BigInteger latestBlockBI = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(latestBlock));

        ObjectMapper objectMapper = JsonUtil.getObjectMapper();
        List<Pool> pools = new ArrayList<>();
        //Get pools from json
        String poolsListJson = CurlExecutor.download(NetworkConstants.POOLS_LIST_JSON_URL);
        if(poolsListJson != null && !poolsListJson.isEmpty()) {
            try {

                JsonNode poolsArray =  objectMapper.readTree(poolsListJson);
                for(int i=0; i < poolsArray.size(); i++) {
                    Pool pool = new Pool();
                    pool.setValidatorAddress(poolsArray.get(i).asText());
                    pools.add(pool);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        /** Commenting pools from getLogs for now **/
        /*String res = remoteAVMNode.getLogs("4591124", null, contractAddress, topics, null);

        try {
            pools = PoolInfoLoader.parsePoolsJson(res);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }*/

        //Now let's cross-check staker scheduler service to add any new stakers.
        List<Staker> stakers = stakedAccountSchedulerClient.getStakers(0, -1, false);

        List<Pool> additionalPools = new ArrayList<>();

        if(stakers != null) {
            for (Staker staker : stakers) {
                String validatorAddress = staker.getIdentity();

                if (staker.isSolo()) //If solo ..check next one
                    continue;

                //check if it's already added
                boolean found = false;
                for (Pool pool : pools) {
                    if (validatorAddress.equalsIgnoreCase(pool.getValidatorAddress())) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    logger.info("A new staker found in staker list {} ", validatorAddress);
                    Pool pool = new Pool();
                    pool.setValidatorAddress(validatorAddress);
                    additionalPools.add(pool);
                }
            }

            if(additionalPools.size() == 0)
                logger.info("No new pool found in staker list.");

            if (additionalPools.size() > 0) {
                logger.info("New pool found in staker list. Let's add it." + additionalPools.toString());
                pools.addAll(additionalPools);
            }
        }

        if(pools == null)
            return Collections.EMPTY_LIST;

//        if(pools.size() < registeredPools.length) {
//            pools = new ArrayList<>();
//            for(String validationAdd: registeredPools) {
//                Pool pool = new Pool();
//                pool.setValidatorAddress(validationAdd);
//                pools.add(pool);
//            }
//        }

        List<Pool> updatePools = new ArrayList<>();
        for(Pool pool: pools) {
            if(pool.getValidatorAddress() != null && !pool.getValidatorAddress().isEmpty()) {
                Pool updatedPool = getPoolInfo(pool.getValidatorAddress());
                if(updatedPool != null)
                    updatePools.add(updatedPool);
            }
        }

        return updatePools;
    }

    public Pool getPoolInfo(String poolAddress) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getPoolInfo", new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress))});

        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        byte[] data = abiDecoder.decodeOneByteArray();

        abiDecoder = new ABIDecoder(data);

        Address coinBaseAddress = abiDecoder.decodeOneAddress();
        Integer commission = abiDecoder.decodeOneInteger();
        boolean selfStake = abiDecoder.decodeOneBoolean();
        String metaDataHash = HexUtil.bytesToHexString(abiDecoder.decodeOneByteArray());
        String metaDataUrl = new String(abiDecoder.decodeOneByteArray());

        Pool pool = new Pool();
        pool.setMetadataUrl(metaDataUrl);
        pool.setCommission(commission.toString());
        pool.setValidatorAddress(poolAddress);
        pool.setCoinbaseAddress(getAddressAsHexString(coinBaseAddress));
        pool.setActive(selfStake);

        //Get singing address
        try {
            Address signingAddress = getSigningAddress(poolAddress, null);
            if(signingAddress != null)
                pool.setSigningAddress(getAddressAsHexString(signingAddress));
        } catch (Exception e) {
            logger.error("Error getting signing address for " + poolAddress, e);
        }

        //Get Stake amounts
        BigInteger[] stakes = getTotalStake(poolAddress, null);
        if(stakes != null && stakes.length >= 2) {
            pool.setTotalStake(stakes[0]);
            pool.setPendingStake(stakes[1]);

            pool.setTotalStakeAion(CryptoUtil.ampToAion(pool.getTotalStake()));
            pool.setPendingStakeAion(CryptoUtil.ampToAion(pool.getPendingStake()));
        }

        try {
            //Outstanding rewards
            BigInteger outstandingRewards = getOutstandingRewards(poolAddress, null);
            pool.setOutstandingRewards(outstandingRewards);
            pool.setOutstandingRewardsAion(CryptoUtil.ampToAion(pool.getOutstandingRewards()));
        } catch (Exception e) {
            logger.error("Error getting outstanding rewards", e);
        }

        try {
            //Self stake
            BigInteger selfStakeAmt = getStake(poolAddress, poolAddress, null);
            pool.setSelfStake(selfStakeAmt);
            pool.setSelfStakeAion(CryptoUtil.ampToAion(pool.getSelfStake()));
        } catch (Exception e) {
            logger.error("Error getting selfstake amount", e);
        }

        return pool;
    }

    private String getAddressAsHexString(Address address) {
        if(address == null) return null;

        String add = address.toString();
        if(!add.startsWith("0x"))
            return "0x" + add;
        else
            return add;
    }

    public BigInteger[] getTotalStake(String poolAddress, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getTotalStake", new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress))});
        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                    null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger[] stakeAmounts = abiDecoder.decodeOneBigIntegerArray();

        return stakeAmounts;
    }

    public BigInteger getOutstandingRewards(String poolAddress, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getOutstandingRewards", new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress))});

        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger outstandingRewards = abiDecoder.decodeOneBigInteger();

        return outstandingRewards;
    }

    public BigInteger getStake(String poolAddress, String delegator, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getStake",
                new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress)), new Address(HexUtil.hexStringToBytes(delegator))});

        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger stake = abiDecoder.decodeOneBigInteger();

        return stake;
    }

    public BigInteger getRewards(String poolAddress, String delegator, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getRewards",
                new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress)), new Address(HexUtil.hexStringToBytes(delegator))});

        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger rewards = abiDecoder.decodeOneBigInteger();

        return rewards;
    }

    public BigInteger getSelfStake(String poolAddress, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getSelfStake", new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress))});

        String response = getRemoteAvmNode().call(NetworkConstants.POOL_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger selfStake = abiDecoder.decodeOneBigInteger();

        return selfStake;
    }

    public BigInteger getTotalStake(String blockHeight) {
        String hex = getRemoteAvmNode().getBalance(NetworkConstants.STAKER_REGISTRY_ADDRESS, blockHeight);
        return ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(hex));
    }

    public BigInteger getTotalStakeFromStakeRegistry(String validator, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getTotalStake", new Object[]{new Address(HexUtil.hexStringToBytes(validator))});
        String response = getRemoteAvmNode().call(NetworkConstants.STAKER_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);
        BigInteger stakeAmount = abiDecoder.decodeOneBigInteger();

        return stakeAmount;
    }

    public Address getSigningAddress(String poolAddress, String blockHeight) {
        String encodedMethodCall = LocalAvmNode.encodeMethodCall("getSigningAddress", new Object[]{new Address(HexUtil.hexStringToBytes(poolAddress))});

        String response = getRemoteAvmNode().call(NetworkConstants.STAKER_REGISTRY_ADDRESS,
                null, encodedMethodCall,
                BigInteger.ZERO, gas, gasPrice, blockHeight);

        byte[] responseBytes = HexUtil.hexStringToBytes(response);
        ABIDecoder abiDecoder = new ABIDecoder(responseBytes);

        return abiDecoder.decodeOneAddress();
    }

    private RemoteAVMNode getRemoteAvmNode() {
        RemoteAVMNode remoteAvmNode = new RemoteAVMNode(web3rpcUrl, new Slf4jLog(logger));
        return remoteAvmNode;
    }
}
