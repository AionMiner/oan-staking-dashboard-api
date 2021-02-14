package com.bloxbean.oan.dashboard;

import com.bloxbean.oan.dashboard.util.HexConverter;

public class HexTest {

    public static void main(String[] args) {
        long val = HexConverter.hexToLong("000000000000000000000000000000000000000000000000000000000000077d");
        System.out.println(val);
    }
}
