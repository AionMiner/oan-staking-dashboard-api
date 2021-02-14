package com.bloxbean.oan.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CurlExecutor {
    private static Logger logger = LoggerFactory.getLogger(CurlExecutor.class);

    public static String download(String url) {
        //cURL Command: curl -u admin:admin -X POST -F cmd="lockPage" -F path="/content/geometrixx/en/toolbar/contacts" -F "_charset_"="utf-8" http://localhost:4502/bin/wcmcommand

        //Equivalent command conversion for Java execution
        String[] command = { "curl", "-L", "-X", "GET", url };

        ProcessBuilder process = new ProcessBuilder(command);
        Process p;
        try {
            p = process.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            String result = builder.toString();
            //System.out.print(result);
            return result;

        } catch (IOException e) {
            System.out.print("error");
            logger.error("Error downloading metadata.json");
            return null;
        }
    }

    public static void main(String[] args) {
        String content = CurlExecutor.download("https://pages.bloxbean.com/stake/metadata.json");
        System.out.println(content);
    }
}