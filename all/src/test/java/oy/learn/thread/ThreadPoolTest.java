package oy.learn.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolTest {

    public static void main(String[] args) {
        ExecutorService service = Executors.newFixedThreadPool(1);
        service.submit(()->{
              int i = 1/0 ;
        });
        service.shutdown();
    }

}
