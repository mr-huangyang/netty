package oy.learn.websocket;

import io.netty.handler.codec.json.JsonObjectDecoder;

/**
 * @author huangyang
 * @Description: ${todo}(这里用一句话描述这个类的作用)
 * @date 2018/05/09 下午2:03
 */
public  class Listener {

    private String command;
    public Listener(String command){
        this.command = command;
    }

    public void onOpen(WebSocketClient client){
        client.send(command);
    }

    public void onMessage(WebSocketClient client, String message) {



//        if(message.contains("\"ping\":")){
//            client.send(String.format( "{\"pong\": \"%s\"}"  , ""  ));
//            return;
//        }
        System.out.println(message);
    }

    public void onPing(WebSocketClient client) {
        client.ping();
    }

    public void onClose(WebSocketClient client) {
        client.close();
        //发送关闭事件，通知重新启动一个新的连接
        client.connect(this);
    }

    public void onFailure() {
        //发送失败事件，通知重试

    }


}
