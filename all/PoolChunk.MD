PoolChunk是Netty内存管理最为核心的类。它的整体思想是将一块内存组织成逻辑上的平衡二叉树，以数组存储树的节点。
核心方法 allocateRun(...) , allocateNode(...), initBuf(...)

```java

final class PoolChunk<T> implements PoolChunkMetric {

    //管理的内存对象
    final T memory;
    /**
     * 1: 完全二叉树在数组中从下标1开始
     * 2：每一层的首节点下标正好表示该层节点数： 1 2 4 8 16
     * 3：（层号 -1）^ 2 = 该层节点数 ， 2^（n+1） -1 表示所有节点数
     */
    private final byte[] memoryMap; //值会在内存被分配后变更
    private final byte[] depthMap; //保存节点所在树的高度值

    //chunk 列表 配合 PoolChunkList 使用
    PoolChunk<T> prev;
    PoolChunk<T> next;



    /**
     * Allocate a run of pages (>=1)
     * 根据申请的内存大小，找到一个合适的内存节点，返回节点号
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {
        //计算内存所在树的层: 理解这个公式的原理
        int d = maxOrder - (log2(normCapacity) - pageShifts);

        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    /**
     * Algorithm to allocate an index in memoryMap when we query for a free node
     * at depth d
     *
     * @param d depth  层号从0开始 0表示第一层 ，11 表示第12层
     * @return index in memoryMap
     */
    private int allocateNode(int d) {
        int id = 1;
        int initial = - (1 << d); // has last d bits = 0 and rest all = 1
        byte val = value(id);

        //内存已用完
        if (val > d) { // unusable
            return -1;
        }

        //当节点层号>= d时 判断  (id & initial) == 0
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;
            val = value(id);
            if (val > d) { //找到一个已被分配过的节点
                // id = id ^ 1 ^ 位相同取0，不同取1
                id ^= 1; //找到右边的兄弟节点 . id += 1 是否一样？？
                val = value(id); 
            }
        }
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);

        //设置节点已使用 unusable = 12
        setValue(id, unusable); // mark as unusable
        //一个节点被分配后，上级节点的层号设置成与未分配子节点层号相同。 chunk完全分配后，所有节点层号都为12
        updateParentsAlloc(id);

        return id;
    }
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            //无符号右移直接得到父级 index
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            //父节点的层号取相邻节点小值
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    /**
     * 根据 allocate 方法返回的内存起始地址，初始化byte buff
     * @param buf
     * @param handle
     * @param reqCapacity
     */
    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {

        int memoryMapIdx = memoryMapIdx(handle);

        int bitmapIdx = bitmapIdx(handle);

        //通过 bitmapIdx区分 tinypage
        if (bitmapIdx == 0) {
            //val  表示层号
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            //这里根据 节点号 计算 内存偏移与长度
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx),
                     arena.parent.threadCache());
        } else {
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

}

```

```text
      树的高度从0开始
           o                 depth=0,该层首节点在depthMap/memoryMap中的下标为1=2^0
        o     o              depth=1,该层首节点在depthMap/memoryMap中的下标为2=2^1
      o   o o   o
      ....
  o   ....    ...    o       depth=11  该层首节点在depthMap/memoryMap中的下标为2048=2^11

                             树的每层节点数 = 该层首节点在数组中的下标值

```

