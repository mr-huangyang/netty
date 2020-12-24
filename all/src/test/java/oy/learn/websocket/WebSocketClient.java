package oy.learn.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author huangyang
 * @Description: ${todo}(这里用一句话描述这个类的作用)
 * @date 2018/05/09 上午10:01
 */
public class WebSocketClient {

    private URI uri;
    private Channel channel;
    private EventLoopGroup group;
    private Listener listener;

    private WebSocketClient(URI uri) {
        this.uri = uri;
    }

    private WebSocketClient(String uri) {
        try {
            this.uri = new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(String msg) {
        channel.writeAndFlush(new TextWebSocketFrame(msg));
    }

    public void ping() {
//        WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
        WebSocketFrame frame = new PingWebSocketFrame();
        channel.writeAndFlush(frame);
    }

    public void close() {
        group.shutdownGracefully();
    }

    public WebSocketClient connect(Listener listener) {
        this.listener = listener;
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port = port(uri);
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new RuntimeException("Only WS(S) is supported.");
        }
        this.group = new NioEventLoopGroup();
        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        try {
            if (ssl) {
                sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } else {
                sslCtx = null;
            }
            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
            final WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, this, listener);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), handler);
                        }
                    });

            this.channel = b.connect(uri.getHost(), port).sync().channel();
            ChannelFuture future = handler.handshakeFuture().sync();
            future.await();

        } catch (Exception e) {
            e.printStackTrace();
            group.shutdownGracefully();
        }
        return this;
    }

    public static WebSocketClient newWebSocket(URI uri, Listener listener) {
        return new WebSocketClient(uri).connect(listener);
    }

    public static WebSocketClient newWebSocket(String uri, Listener listener) {
        return new WebSocketClient(uri).connect(listener);
    }

    private static int port(URI uri) {
        int port;
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(uri.getScheme())) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(uri.getScheme())) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }
        return port;
    }

    public static void main(String[] args) {
//        System.setProperty("http.proxySet", "true");
//        System.setProperty("http.proxyHost", "127.0.0.1");
//        System.setProperty("http.proxyPort", "1086");
//
//        System.setProperty("proxySet", "true");
//        System.setProperty("socksProxyHost", "127.0.0.1");
//        System.setProperty("socksProxyPort", "1086");

        okex();
//        zb();
//        coinex();
//        bitfinex();
//        yex();
//        lbank();
//        gateio();
    }


    static void okex() {
//        String tt = "{'event':'addChannel','channel':'ok_sub_spot_btc_usdt_deals'}";
        String tt = "{'event':'addChannel','channel':'ok_sub_spot_bcn_btc_deals'}";

        String ws = "wss://real.okex.com:10442/ws/v3";
        WebSocketClient.newWebSocket(ws, new Listener(tt));
    }

    static void zb() {
        String tt = "{\"event\":\"addChannel\" , \"channel\":\"btcusdt_trades\"}";
        String ws = "wss://api.zb.cn:9999/websocket";
        WebSocketClient.newWebSocket(ws, new Listener(tt));
    }

    static void coinex() {
//        String tt = "{\"id\":12345,\"method\":\"deals.subscribe\",\"params\":[\"BTCUSDT\"]}";
        String tt = "{\"id\":'12345',\"method\":\"deals.subscribe\",\"params\":[\"BTCUSDT\"]}";
        String ws = "wss://socket.coinex.com/";
        WebSocketClient.newWebSocket(ws, new Listener(tt));


    }

    static void bitfinex(){
        String ping ="{ \"event\":\"ping\", \"cid\": %s}";
        String tt = "{\"channel\":\"trades\",\"event\":\"subscribe\",\"symbol\":\"BTCUSD\"}";
        String ws = "wss://api.bitfinex.com/ws/1";
        WebSocketClient.newWebSocket(ws, new Listener(tt));
//        WebSocketClient.newWebSocket(ws, new Listener(String.format(ping,System.currentTimeMillis())));
    }

    static void yex(){
        String ws = "wss://web.yex.com:9999/market";
        String tt = "{\"param\":\"trades\",\"symbol\":\"BTC_USDT\"}";
        WebSocketClient.newWebSocket(ws, new Listener(tt));

    }

    static void lbank(){
        String ws = "wss://api.lbkex.com/ws/V2/";
        String tt = "{\"action\":\"subscribe\",\"pair\":\"btc_usdt\",\"subscribe\":\"kbar\" , \"kbar\":\"1min\"  }";
        WebSocketClient.newWebSocket(ws, new Listener(tt));

    }

    static void gateio(){
        String ws = "wss://ws.gate.io/v3/";
        String tt = "{\"id\":12312, \"method\":\"trades.subscribe\", \"params\":[ \"BTC_USDT\"]}";
        WebSocketClient.newWebSocket(ws, new Listener( String.format(tt,System.currentTimeMillis())  ));

    }


}
