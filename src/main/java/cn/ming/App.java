package cn.ming;

import app.cash.nostrino.crypto.PubKey;
import app.cash.nostrino.crypto.SecKey;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import java.security.SecureRandom;

/**
 * @author Ming
 */
public class App {
    private static String pk = "";
    private static String sk = "";
    private static Integer numberOfWorkers = 8;
    private static String arbRpcUrl = "";
    private static Integer difficulty = 21;

    Log log = LogFactory.get(App.class);

    public static void main(String[] args) {
        PubKey pubKey = PubKey.Companion.parse(pk);
        SecKey secKey = SecKey.Companion.parse(sk);
    }

    /**
     * 生成随机字符串
     *
     * @param length
     * @return
     */
    private static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder randomString = new StringBuilder(length);
        SecureRandom secureRandom = new SecureRandom();

        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(characters.length());
            randomString.append(characters.charAt(index));
        }

        return randomString.toString();
    }

    private static void getLastEventIdForever() {

    }

    private static void getLatestArbBlock() {

    }
}
