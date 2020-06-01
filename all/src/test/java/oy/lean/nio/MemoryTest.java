package oy.lean.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {
    public static void main(String[] args) {
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(250000);
        buffer.writeByte(10);
        buffer.release();
    }
}
