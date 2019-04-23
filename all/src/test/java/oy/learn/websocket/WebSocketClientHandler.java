package oy.learn.websocket;

/**
 * @author huangyang
 * @Description:
 * @date 2018/05/09 上午9:53
 */


import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private Listener listener;
    private WebSocketClient client;

    public WebSocketClientHandler(final WebSocketClientHandshaker handshaker, WebSocketClient client, Listener listener) {
        this.handshaker = handshaker;
        this.listener = listener;
        this.client = client;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        System.out.println("WebSocket Client disconnected!");
        listener.onClose(client);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            // web socket client connected
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            handshakeFuture.setSuccess();
            listener.onOpen(client);
            return;
        }

        if (msg instanceof FullHttpResponse) {
            final FullHttpResponse response = (FullHttpResponse) msg;
            throw new Exception("Unexpected FullHttpResponse (getStatus=" + response.getStatus() + ", content="
                    + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        final WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {

            final TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();
            listener.onMessage(client, text);
            listener.onPing(client);

        } else if (frame instanceof PingWebSocketFrame) {

            String ping = frame.content().toString(CharsetUtil.UTF_8);
            System.out.println("+++++++   got a ping ,text = " + ping);
            if(ping == null || ping.trim().isEmpty()){
                ctx.writeAndFlush( new PongWebSocketFrame());
            }else {
                ctx.writeAndFlush( new PongWebSocketFrame( Unpooled.copiedBuffer(ping.getBytes(CharsetUtil.UTF_8))));
            }

        } else if (frame instanceof PongWebSocketFrame) {

            System.out.println(frame.content().toString(CharsetUtil.UTF_8));

        } else if (frame instanceof CloseWebSocketFrame) {

            System.out.println("CloseWebSocketFrame");
            ch.close();

        } else if (frame instanceof BinaryWebSocketFrame) {
            String text = frame.content().toString(CharsetUtil.UTF_8);
            listener.onMessage(client, text);
        }

    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        cause.printStackTrace();

        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }

        ctx.close();
    }
}

