package oy.lean.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {
    public static void main(String[] args) {
//        test_pagesize();
        test1();

    }

    private static void test1() {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(250);
        final ByteBuf buffer0 = PooledByteBufAllocator.DEFAULT.buffer(251);
        buffer.writeByte(10);
        buffer.writeByte(100);
//        buffer0.writeByte(10);
        buffer.release();
        buffer0.release();
    }

    private static void test_pagesize() {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024 * 7);
        buffer.release();
    }
}
