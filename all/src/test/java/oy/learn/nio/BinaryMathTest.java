package oy.learn.nio;

import org.junit.Test;

/**
 * @author huangyang
 * @Description: (这里用一句话描述这个类的作用)
 * @date 2020/11/22 10:58 上午
 */
public class BinaryMathTest {

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    @Test
    public void test_power2() {
        System.out.println(tableSizeFor(8));
//        System.out.println(tableSizeFor(15));
//        System.out.println(tableSizeFor(33));
    }

    public static final int tableSizeFor(int cap) {
        //  位运算 https://juejin.cn/post/6844903550095458312
        //https://www.zhihu.com/question/38206659
        //https://juejin.cn/entry/6844903459112779784
//        num -= 1;
//        num |= num >> 16;
//        num |= num >> 8;
//        num |= num >> 4;
//        num |= num >> 2;
//        num |= num >> 1;
//        return num + 1;


        int n = cap - 1; // 为什么要减1？？

        System.out.println(n);
        n |= n >>> 1;
        System.out.println(n);
        n |= n >>> 2;
        System.out.println(n);
        n |= n >>> 4;
        System.out.println(n);
        n |= n >>> 8;
        System.out.println(n);
        n |= n >>> 16;
        System.out.println(n);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
}
