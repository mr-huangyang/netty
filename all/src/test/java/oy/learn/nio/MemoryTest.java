package oy.learn.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {
    public static void main(String[] args) {
//        System.out.println(Integer.toBinaryString((1<< Integer.SIZE-3)-1));
//        System.out.println((1L << 31) - 1);
//        System.out.println(Integer.MAX_VALUE);
//        System.out.println(Integer.numberOfLeadingZeros(1024));
//        test_pagesize();
        test1();

//        test_changeBitValue(4,1,1);

    }

    public static void test_changeBitValue(int num, int pos, int val) {

        //pos 表示二进制位的下标，从0开始
        int mask = 1 << pos;
        int a = num & ~mask; //使 num 目标位变成0,其它位保持不变
//        int b =  (val << pos)  ;
        int b =  (val << pos) & mask ;
        System.out.println(  a | b  );
    }

    private static void test1() {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024 * 4);
//        for(int i = 0;i <3 ; i++){
//            PooledByteBufAllocator.DEFAULT.buffer(7000);
//        }
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
//        PooledByteBufAllocator.DEFAULT.buffer(510);
        buffer.writeByte(10);
//        buffer.writeByte(11) ;
//        buffer0.writeByte(10);
        buffer.release();
    }

    private static void test_pagesize() {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(10 );
         PooledByteBufAllocator.DEFAULT.buffer(10 );
        buffer.release();
    }
}
