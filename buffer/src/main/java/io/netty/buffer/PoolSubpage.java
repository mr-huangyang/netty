/**
 * Copyright 2012 The Netty Project
 * <p>
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

/**
 * page之间形成一个环形连表
 * <ul>
 *     <li><a href="https://www.cnblogs.com/wuzhenzhao/p/11304496.html">文章-1</a></li>
 *     <li><a href="https://blog.csdn.net/u010412719/article/details/78242224">文章-2</a></li>
 * </ul>
 *  subpage 是对 page的再一次逻辑划分建模。page=8k,按16byte切分成512个块。每一块是否使用通过一个bit位来标记
 *  bit位 使用 long[8]数组表示
 *
 * @param <T>
 */
final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;

    private final int memoryMapIdx;

    /**
     * 分配的内存偏移量:最终的内存地址是 memory.address + offset
     */
    private final int runOffset;

    /**
     * page 分配的内存大小
     */
    private final int pageSize;

    /**
     * 内存使用标识, 每一位表示 maxNumElems中的一个是否已使用
     */
    private final long[] bitmap;

    int elemSize;
    //page 被平均切分的数字
    private int maxNumElems;
    //实际需要使用的bitmap长度
    private int bitmapLength;
    private int nextAvail;
    private int numAvail;

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;


    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     * Special constructor that creates a linked list head
     */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;

        //为什么右移10位？？ 因为最小的块是16字节,pageSize=8k可分成512个最小单位
        //long是64位，8*64=512正好一位对应一个最小内存块
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64

        init(head, elemSize);
    }

    /**
     *
     * @param head
     * @param elemSize
     */
    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            //按首次请求的内存大小切分 pageSize
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;

            //为什么右移6位？
            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                bitmapLength++;
            }

            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;
            }
        }
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();

        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;

        assert (bitmap[q] >>> r & 1) == 0;

        bitmap[q] |= 1L << r;

        if (--numAvail == 0) {
            removeFromPool();
        }

        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} if this subpage is in use.
     * {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        setNextAvail(bitmapIdx);

        if (numAvail++ == 0) {
            addToPool(head);
            return true;
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            if (~bits != 0) {// bits ==0 表示此long有可用的bit
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    @Override
    public String toString() {
        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return String.valueOf('(') + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        return maxNumElems;
    }

    @Override
    public int numAvailable() {
        return numAvail;
    }

    @Override
    public int elementSize() {
        return elemSize;
    }

    @Override
    public int pageSize() {
        return pageSize;
    }
}
