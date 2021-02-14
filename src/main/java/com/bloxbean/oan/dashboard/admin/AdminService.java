package com.bloxbean.oan.dashboard.admin;

import io.lettuce.core.api.StatefulRedisConnection;
import org.aion4j.avm.helper.util.HexUtil;
import org.aion4j.avm.helper.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Singleton
public class AdminService {

    private final static String ADMIN_PASSWORD_KEY = "admin.key";

    @Inject
    private StatefulRedisConnection<String,String> connection;

    public String generateNewAdminKey(String authKey) throws NoSuchAlgorithmException {

        if(verifyAdminAuthKey(authKey)) {
            String pwd = UUID.randomUUID().toString();
            String hash = md5Hash(pwd);

            connection.sync().set(ADMIN_PASSWORD_KEY, hash);
            return pwd;
        } else {
            return null;
        }
    }

    public boolean verifyAdminAuthKey(String authKey) throws NoSuchAlgorithmException {
        String storedHash = connection.sync().get(ADMIN_PASSWORD_KEY); //No key set yet, so allow
        if(StringUtils.isEmpty(storedHash))
            return true;

        String pwdHash = md5Hash(authKey);

        if(pwdHash.equalsIgnoreCase(storedHash))
            return true;
        else
            return false;
    }

    private String md5Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(input.getBytes());
        byte[] digest = md.digest();

        return HexUtil.bytesToHexString(digest);
    }
}
