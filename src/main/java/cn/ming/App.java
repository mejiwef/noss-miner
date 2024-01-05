package cn.ming;

import app.cash.nostrino.crypto.PubKey;
import app.cash.nostrino.crypto.SecKey;
import app.cash.nostrino.model.Event;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
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
import java.net.InetSocketAddress;
import java.net.Proxy;
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
    private static String blockHeight = "0";
    private static String blockHash = "";
    private static String content = "{\"p\":\"nrc-20\",\"op\":\"mint\",\"tick\":\"noss\",\"amt\":\"10\"}";

    private static ExecutorService executorService;
    private static TimedCache<String, Boolean> cache = new TimedCache<>(300000);

    public static void main(String[] args) throws Exception {
        loadConfig();

        executorService.submit(() -> updateEventId());
        executorService.submit(() -> getLatestArbBlock());
        Thread.sleep(3000);

        PubKey pubKey = PubKey.Companion.parse(pk);
        SecKey secKey = SecKey.Companion.parse(sk);

        while (true) {
            if (cache.size() > 1) {
                Thread.sleep(100);
                continue;
            }
            String requestId = RandomUtil.randomString(16);
            cache.put(requestId, Boolean.FALSE);
            for (int i = 0; i < numberOfWorkers; i++) {
                Runnable task = createTask(requestId, pubKey, secKey);
                executorService.submit(task);
            }
        }
    }

    private static Runnable createTask(String requestId, PubKey pubKey, SecKey secKey) {
        return () -> {
            while (true) {
                if (null == cache.get(requestId) || cache.get(requestId)) {
                    break;
                }
                try {
                    Event dataEvent = getDataEvent(pubKey);
                    String nonce = generateRandomString(11);
                    dataEvent.getTags()
                             .add(List.of("nonce", nonce, "21"));
                    JSONObject jsonObject = JSONUtil.parseObj(dataEvent.toJson());
                    jsonObject.remove("id");
                    jsonObject.remove("sig");
                    String _id = SecureUtil.sha256(jsonObject.toString());
                    if (getPow(_id) < 21) {
                        continue;
                    }
                    ByteString sign = secKey.sign(ByteString.of(_id.getBytes(StandardCharsets.UTF_8)).sha256());
                    Event event = new Event(
                            ByteString.encodeUtf8(_id),
                            dataEvent.getPubKey(),
                            dataEvent.getCreatedAt(),
                            dataEvent.getKind(),
                            dataEvent.getTags(),
                            dataEvent.getContent(),
                            sign
                    );
                    //log.info(event.toJson());
                    postEvent(_id, event);
                    cache.remove(requestId);
                    break;
                } catch (Exception e) {
                    log.error(e);
                }
            }
        };
    }

    private static int getPow(String hex) {
        int count = 0;
        for (int i = 0; i < hex.length(); i++) {
            int nibble = Integer.parseInt(String.valueOf(hex.charAt(i)), 16);
            if (nibble == 0) {
                count += 4;
            } else {
                count += Integer.numberOfLeadingZeros(nibble) - 28;
                break;
            }
        }
        return count;
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
        String[] arbRpcUrlList = setting.getStrings("arbRpcUrls");
        if (null == arbRpcUrlList || arbRpcUrlList.length == 0) {
            log.error("缺少sk配置");
            System.exit(0);
        }
        arbRpcUrls = arbRpcUrlList;
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
        String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder randomString = new StringBuilder(length);
        SecureRandom secureRandom = new SecureRandom();

        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(characters.length());
            randomString.append(characters.charAt(index));
        }

        return randomString.toString();
    }

    private static Event getDataEvent(PubKey pubKey) {
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
                ByteString.EMPTY,
                pubKey.getKey(),
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
        while (true) {
            try {
                String arbRpcUrl = getArbRpcUrl();
                Web3j web3j = Web3j.build(new HttpService(arbRpcUrl));
                // 获取最新区块
                EthBlock.Block latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                                                  .send()
                                                  .getBlock();
                // 输出区块信息
                BigInteger number = latestBlock.getNumber();
                String hash = latestBlock.getHash();
                if (number.compareTo(new BigInteger(blockHeight)) > 0) {
                    blockHeight = number.toString();
                    blockHash = hash;
                }
                log.info("最新EVM区块高度: {}, 区块hash: {}", blockHeight, blockHash);
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
                    log.info("wss onMessage: {}", arg0);
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
        JSONObject data = new JSONObject();
        data.put("event", JSONUtil.parseObj(event.toJson()));
        String url = "https://api-worker.noscription.org/inscribe/postEvent";
        //Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10809));
        HttpResponse response = HttpUtil.createPost(url)
                                        //.setProxy(proxy)
                                        .header("Content-Type", "application/json")
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
                                        .header("Sec-ch-ua", "\"Not A(Brand\";v=\"99\", \"Microsoft Edge\";v=\"121\", \"Chromium\";v=\"121\"")
                                        .header("Sec-ch-ua-mobile", "?0")
                                        .header("Sec-ch-ua-platform", "\"Windows\"")
                                        .header("Sec-fetch-dest", "empty")
                                        .header("Sec-fetch-mode", "cors")
                                        .header("Sec-fetch-site", "same-site")
                                        .body(data.toString())
                                        .execute();
        log.info("post event id: {}, response: {}", _id, response.body());
    }
}
