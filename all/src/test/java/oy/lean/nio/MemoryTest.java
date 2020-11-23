package oy.lean.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {
    public static void main(String[] args) {
//        System.out.println(Integer.numberOfLeadingZeros(1024));
//        test_pagesize();
        test1();

    }

    private static void test1() {
        ByteBuf buffer = null; //PooledByteBufAllocator.DEFAULT.buffer(7000);
        for(int i = 0;i <3 ; i++){
            PooledByteBufAllocator.DEFAULT.buffer(7000);
        }
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
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024 * 7);
        buffer.release();
    }
}
