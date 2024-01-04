package cn.ming;

import app.cash.nostrino.crypto.PubKey;
import app.cash.nostrino.crypto.SecKey;
import app.cash.nostrino.model.Event;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.setting.Setting;
import okio.ByteString;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Ming
 */
public class App {
    private static Log log = LogFactory.get(App.class);

    private static String pk = "";
    private static String sk = "";
    private static Integer numberOfWorkers = 8;
    private static String[] arbRpcUrls = new String[0];
    private static String eventId = "";
    private static String blockHeight = "";
    private static String blockHash = "";
    private static String content = "{\"p\":\"nrc-20\",\"op\":\"mint\",\"tick\":\"noss\",\"amt\":\"10\"}";

    private static ExecutorService executorService;

    public static void main(String[] args) throws Exception {
        loadConfig();

        executorService.submit(() -> updateEventId());
        executorService.submit(() -> getLatestArbBlock());
        Thread.sleep(3000);

        PubKey pubKey = PubKey.Companion.parse(pk);
        SecKey secKey = SecKey.Companion.parse(sk);

        while (true) {
            Event dataEvent = getDataEvent();
            String nonce = generateRandomString(13);
            dataEvent.getTags()
                     .add(List.of("nonce", nonce, "21"));
            String _id = SecureUtil.sha256(dataEvent.toJson());
            if (!_id.startsWith("00000")) {
                continue;
            }
            String jsonData = dataEvent.toJson();
            byte[] bytes = jsonData.getBytes(StandardCharsets.UTF_8);
            ByteString sign = secKey.sign(ByteString.of(bytes).sha256());
            Event event = new Event(
                    ByteString.encodeUtf8(_id),
                    pubKey.getKey(),
                    dataEvent.getCreatedAt(),
                    dataEvent.getKind(),
                    dataEvent.getTags(),
                    dataEvent.getContent(),
                    sign
            );
            //log.info(event.toJson());
            postEvent(_id, event);
        }
    }

    private static void loadConfig() {
        Setting setting = new Setting("miner.setting");
        String pubKey = setting.getStr("pk");
        if (StrUtil.isBlank(pubKey)) {
            log.error("缺少pk配置");
            System.exit(0);
        }
        pk = pubKey;
        String safeKey = setting.getStr("sk");
        if (StrUtil.isBlank(safeKey)) {
            log.error("缺少sk配置");
            System.exit(0);
        }
        sk = safeKey;
        String[] arbRpcUrls = setting.getStrings("arbRpcUrls");
        if (null == arbRpcUrls || arbRpcUrls.length == 0) {
            log.error("缺少sk配置");
            System.exit(0);
        }
        Integer threads = setting.getInt("threads");
        if (null != threads) {
            numberOfWorkers = threads;
        }
        executorService = Executors.newFixedThreadPool(numberOfWorkers + 2);
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

    private static Event getDataEvent() {
        ByteString pubKey = ByteString.encodeUtf8(pk);
        List<List<String>> tags = new ArrayList<>();
        tags.add(List.of("p", "9be107b0d7218c67b4954ee3e6bd9e4dba06ef937a93f684e42f730a0c3d053c"));
        tags.add(List.of("e",
                "51ed7939a984edee863bfbb2e66fdc80436b000a8ddca442d83e6a2bf1636a95",
                "wss://relay.noscription.org/",
                "root"));
        tags.add(List.of("e",
                eventId,
                "wss://relay.noscription.org/",
                "reply"));
        tags.add(List.of("seq_witness", blockHeight, blockHash));
        Event data = new Event(
                ByteString.encodeUtf8("0"),
                pubKey,
                Instant.now(),
                1,
                tags,
                content,
                ByteString.EMPTY
        );
        return data;
    }

    private static int rpcIdx = 0;

    private static String getArbRpcUrl() {
        rpcIdx = rpcIdx >= 50000 ? 0 : rpcIdx;
        return arbRpcUrls[rpcIdx++ % arbRpcUrls.length];
    }

    private static void getLatestArbBlock() {
        String arbRpcUrl = getArbRpcUrl();
        Web3j web3j = Web3j.build(new HttpService(arbRpcUrl));
        while (true) {
            try {
                // 获取最新区块
                EthBlock.Block latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                                                  .send()
                                                  .getBlock();
                // 输出区块信息
                BigInteger number = latestBlock.getNumber();
                String hash = latestBlock.getHash();
                blockHeight = number.toString();
                blockHash = hash;
                //log.info("最新EVM区块高度: {}, 区块hash: {}", number, hash);
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("获取evm最新区块失败", e);
            }
        }
    }

    /**
     * 更新 eventId
     */
    private static void updateEventId() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Sec-WebSocket-Version", "13");
        headers.put("Connection", "Upgrade");
        headers.put("Upgrade", "websocket");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Cache-Control", "no-cache");
        headers.put("Origin", "https://noscription.org");
        headers.put("Pragma", "no-cache");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.117 Safari/537.36");

        Draft draft = new Draft_17();
        draft.setParseMode(WebSocket.Role.CLIENT);
        URI uri;
        try {
            uri = new URI("ws://report-worker-2.noscription.org/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        new ZgxWssUtil(uri
                , draft
                , headers
                , 1000
        ) {
            @Override
            public void onClose(int arg0, String arg1, boolean arg2) {
                log.info(String.format("onClose:【%s】【%s】【%s】", arg0, arg1, arg2));
            }

            @Override
            public void onError(Exception arg0) {
                log.error(String.format("onError:%s", arg0));
            }

            @Override
            public void onMessage(String arg0) {
                try {
                    // 这里拿eventId
                    eventId = JSONUtil.parseObj(arg0).getStr("eventId");
                    //log.info("wss onMessage: {}", arg0);
                } catch (Exception e) {
                    log.error(e);
                }
            }

            @Override
            public void onOpen(ServerHandshake arg0) {
                log.info(String.format("onOpen:%s", arg0));
            }
        }.connect();
    }

    private static void postEvent(String _id, Event event) {
        String url = "https://api-worker.noscription.org/inscribe/postEvent";
        HttpResponse response = HttpUtil.createPost(url)
                                        .header("authority", "api-worker.noscription.org")
                                        .header("accept", "application/json, text/plain, */*")
                                        .header("accept-language", "zh-CN,zh;q=0.9")
                                        .header("content-type", "application/json")
                                        .header("origin", "https://noscription.org")
                                        .header("referer", "https://noscription.org/")
                                        .header("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                                        .header("sec-ch-ua-mobile", "?0")
                                        .header("sec-ch-ua-platform", "\"macOS\"")
                                        .header("sec-fetch-dest", "empty")
                                        .header("sec-fetch-mode", "cors")
                                        .header("sec-fetch-site", "same-site")
                                        .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .body(event.toJson())
                                        .execute();
        log.info("post event id: {}, response: {}", _id, response.body());
    }
}
