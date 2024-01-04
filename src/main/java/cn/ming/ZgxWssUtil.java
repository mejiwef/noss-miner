package cn.ming;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;

import javax.net.ssl.*;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author Ming
 */
abstract class ZgxWssUtil extends WebSocketClient {

    public ZgxWssUtil(URI serverURI) {
        super(serverURI);
        if (serverURI.toString().contains("wss://"))
            trustAllHosts(this);
    }

    public ZgxWssUtil(URI serverURI, Draft draft) {
        super(serverURI, draft);
        if (serverURI.toString().contains("wss://"))
            trustAllHosts(this);
    }

    public ZgxWssUtil(URI serverURI, Draft draft, Map<String, String> headers, int connecttimeout) {
        super(serverURI, draft, headers, connecttimeout);
        if (serverURI.toString().contains("wss://"))
            trustAllHosts(this);
    }

    final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };


    static void trustAllHosts(ZgxWssUtil appClient) {
        System.out.println("start...");
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // TODO Auto-generated method stub

            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // TODO Auto-generated method stub

            }
        }};

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            appClient.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
