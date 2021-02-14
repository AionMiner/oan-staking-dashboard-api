package com.bloxbean.oan.dashboard.util;

import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.base.util.ByteUtil;
import org.aion4j.avm.helper.util.CryptoUtil;
import org.aion4j.avm.helper.util.HexUtil;
import org.aion4j.avm.helper.util.StringUtils;

import java.math.BigInteger;

public class HexConverter {
    private final static BigInteger THOUSAND = new BigInteger("1000");

    public static BigInteger hexToBigInteger(String hex) {
        if (!StringUtils.isEmpty(hex)) {
            if (hex.startsWith("0x")) {
                hex = hex.substring(2);
            }

            BigInteger bi = new BigInteger(hex, 16);
            return bi;
        } else
            return null;
    }

    public static long hexToTimestampInMillis(String hex) {
        if (!StringUtils.isEmpty(hex)) {
            if (hex.startsWith("0x")) {
                hex = hex.substring(2);
            }

            BigInteger bi = new BigInteger(hex, 16);
            return bi.multiply(THOUSAND).longValue();
        } else
            return 0;
    }

    public static long hexToLong(String hex) {
        return ByteUtil.byteArrayToLong(HexUtil.hexStringToBytes(hex));
    }

    public static void main(String[] args) {
//        byte[] bytes = HexUtil.hexStringToBytes(
//        "2309335e781e31e140fac0230100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//
//        ABIDecoder abiDecoder = new ABIDecoder(bytes);
//        BigInteger amt = abiDecoder.decodeOneBigInteger();
//        System.out.println(abiDecoder.decodeOneBigInteger());
//        System.out.println(CryptoUtil.ampToAion(amt));

        long bi = hexToTimestampInMillis("0x5e2d4617");
        System.out.println(bi);
    }
}
