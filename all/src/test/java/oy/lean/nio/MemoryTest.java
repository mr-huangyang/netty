package oy.lean.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.math.BigDecimal;

public class MemoryTest {
    public static void main(String[] args) {
        test2();
    }

    private static void test1() {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(250);
        final ByteBuf buffer0 = PooledByteBufAllocator.DEFAULT.buffer(251);
        buffer.writeByte(10);
        buffer0.writeByte(10);
        buffer.release();
        buffer0.release();
    }

    private static void test2() {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024 * 7);
        buffer.release();
    }
}
