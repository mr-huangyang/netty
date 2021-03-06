/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 * <p>
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 * <p>
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 * <p>
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 * <p>
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 * <p>
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 * <p>
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 * <p>
 * depth=maxOrder is the last level and the leafs consist of pages
 * <p>
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 * <p>
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 * memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 * is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 * <p>
 * As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 * <p>
 * Initialization -
 * In the beginning we construct the memoryMap array by storing the depth of a node at each node
 * i.e., memoryMap[id] = depth_of_id
 * <p>
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 * some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 * is thus marked as unusable
 * <p>
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 * <p>
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 * <p>
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 * note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 * <p>
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information (i.e, 2 byte vals) as a short value in memoryMap
 * <p>
 * memoryMap[id]= (depth_of_id, x)
 * where as per convention defined above
 * the second value (i.e, x) indicates that the first node which is free to be allocated is at depth x (from root)
 * <br/>
 * Ref:
 * <a href="https://juejin.im/post/5ca4a5e051882543b16e33aa">chunk1</a>
 * <a href="https://www.jianshu.com/p/c4bd37a3555b">chunk2</a>
 * <p>
 * Q1: 内存如何管理？
 * <p>
 * Q2: 内存节点被分配后如何标识已分配，这一块内存如何与bytebuf绑定？
 * <p>
 * 1: PoolChunk 代表向系统申请的一块内存，在内部会将内存组织成一棵树
 * 2: chunk 本身是一个连表结点 {@link PoolChunkList}
 *
 *
 *                      o      depth=0  id = 1
 *                    o   o    depth=1  id = 2 = 2^1
 *                  o  o o o   depth=2  id = 4 = 2^2
 *                             depth=3  id = 8 = 2^3
 *                             depth=4  id = 16 = 2^4
 *                             .....
 *                             depth=9 id = 2048 = 2^9   size=32k=2^15
 *                             depth=10 id = 2048 = 2^10   size=16k=2^14
 *                             depth=11 id = 2048 = 2^11   size=8k=2^13
 *
 *      1:每层节点数等于首节点的下标
 *
 * 3: 2个重要的方法 allocate  initBuf
 */
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;
    //管理的内存对象
    final T memory;
    final boolean unpooled;

    //memoryMap depthMap 存入的是树所有节点所在的层号 default 4096
    /**
     * 1: 完全二叉树在数组中从下标1开始,方便计算内存节点
     * 2：每一层的首节点下标正好表示该层节点数： 1 2 4 8 16
     * 3：（层号）^ 2 = 该层节点数 ， 2^（n+1） -1 表示所有节点数
     */
    private final byte[] memoryMap; //值会在内存被分配后变更
    private final byte[] depthMap;


    /**
     * 对 page 进一步的管理 subpage就是对page的细化
     * 关联 {@link PoolArena}中的tiny sub page
     */
    private final PoolSubpage<T>[] subpages;


    /**
     * Used to determine if the requested capacity is equal to or greater than pageSize.
     */
    private final int subpageOverflowMask;
    //leaf node size : default  8k
    private final int pageSize;
    //smallest page pow: 8K=2^13 pageShifts=13
    private final int pageShifts; //default 13
    private final int maxOrder; // default 11
    private final int chunkSize;
    private final int log2ChunkSize;
    private final int maxSubpageAllocs; // default 2048
    /**
     * Used to mark memory as unusable
     */
    private final byte unusable;

    private int freeBytes;

    /**
     * {@link PoolChunkList}.add0()函数
     */
    PoolChunkList<T> parent;
    //chunk 列表 配合 PoolChunkList 使用
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        //叶子节点的大小
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];

        //** 为什么从 1 开始 ? 利用下标位移运算快速定位到树某层的首位节点
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs);
    }

    /**
     * Creates a special chunk that is not pooled.
     */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    @Override
    public int usage() {
        final int freeBytes = this.freeBytes;
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    /**
     * 核心方法
     *
     * @param normCapacity
     * @return
     */
    long allocate(int normCapacity) {
        //为何不用 >= 运算 ？ 性能低？
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize
            return allocateRun(normCapacity);
        } else {
            return allocateSubpage(normCapacity);
        }
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
//            System.out.println(id);
            //无符号右移直接得到父级 index
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            //父节点的层号取相邻节点小值
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
//            System.out.println("parentId=" + parentId + " val = " + val);
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            if (val1 == logChild && val2 == logChild) {
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
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
        int initial = -(1 << d); // has last d bits = 0 and rest all = 1
        byte val = value(id);

        //内存已用完
        if (val > d) { // unusable
            return -1;
        }

        /**
         * 连续分配4次8k内存id变化
         1 2 4 8 16 32 64 128 256 512 1024 2048
         1 2 4 8 16 32 64 128 256 512 1024 2048 2049
         1 2 4 8 16 32 64 128 256 512 1024 1025 2050
         1 2 4 8 16 32 64 128 256 512 1024 1025 2050 2051
         */

        //当节点层号>= d时 判断  (id & initial) == 0
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
//            System.out.println(id);
            id <<= 1;
            val = value(id);
            if (val > d) { //找到一个已被分配过的节点
//                System.out.println(id);
                // id = id ^ 1 (^位相同取0，不同取1)
                id ^= 1; //找到右边的兄弟节点 . 2048^1=2049 2049^1=2048  x^1表示偶数+1，奇数-1
                val = value(id); //id越界？？
            }
        }

        //找到一个可用的节点
//        System.out.println(id);
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);

        //设置节点已使用 unusable = 12
        setValue(id, unusable); // mark as unusable
        //一个节点被分配后，上级节点的层号设置成与未分配子节点层号相同。 chunk完全分配后，所有节点层号都为12
        updateParentsAlloc(id);

        return id;
    }

    /**
     * Allocate a run of pages (>=1)
     * 根据申请的内存大小，找到一个合适的内存节点，返回节点号
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {

        //计算内存所在树的层: 最大层号 - (标准内存大小值对应的log2对数值-最小节点内存的log2对数值)
        //计算normCapacity 所在的层: 假设normCapacity=8K=2^13
        // 11 = 11 - (13-13)
        // 10 = 11 - (14-13)
        //  9 = 11 - (15-13)
        int d = maxOrder - (log2(normCapacity) - pageShifts);

        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    /**
     * Create/ initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created/ initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateSubpage(int normCapacity) {

        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);

        synchronized (head) {
            int d = maxOrder; // subpages are only be allocated from pages i.e., leaves
            int id = allocateNode(d);
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;


            //初始化 page
            int subpageIdx = subpageIdx(id);
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {
                //创建小内存 page
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;
            } else {
                //重用已存在的subpage
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();
        }
    }

    /**
     * page: free bytes增加，节点标记从12变回原有的层号，更新父节点层号
     * subpage: 标记位置为0
     *
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    void free(long handle) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) { // free a subpage
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            synchronized (head) {
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);
        setValue(memoryMapIdx, depth(memoryMapIdx));
        updateParentsFree(memoryMapIdx);
    }

    /**
     * 根据 allocate 方法返回的内存起始地址，初始化byte buff
     *
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

    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, bitmapIdx(handle), reqCapacity);
    }

    /**
     * #oy-memory: 用page 构造 byte buf
     *
     * @param buf
     * @param handle
     * @param bitmapIdx
     * @param reqCapacity
     */
    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
                this, handle,
                runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, reqCapacity, subpage.elemSize,
                arena.parent.threadCache());
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    /**
     * 取一个数的2对数  2^10 = 1024  log2(1024) = 10
     *
     * @param val
     * @return
     */
    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    /**
     * 根据节点号，计算出某节点的内存大小
     * 计算原理：每一层的节点内存和=总内存 ,每一层首节点index == 该层节点数 因此有公式 v * 2^d = 16M=2^24
     *
     * @param id
     * @return
     */
    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        // default log2chuncksize = 24  8k = 2^13  16m= 2^24
        // 2^13 * 2^x = 2^24  -> 2^x = 2^(24-13) = 2^11 x=11刚好是leaf page 所在的层号
        // 2^d * 2^x = 2^24 -> x=24-d=log2ChunkSize-depth(id) -> 1<<x

        return 1 << log2ChunkSize - depth(id);
    }

    /**
     * 计算此节点在内存中的偏移量，从0开始
     * @param id
     * @return
     */
    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        // id ^ (1 << depth(id));
        //1<<depth(id) = 2^d = 表示当层的节点个数 = 也是该层的首节点下标数 = ...000100...
        //同一层节点下标二进制位数一致都以..001开头
        int shift = id ^ 1 << depth(id); // 等于 id-( 1<< depth(id)  )
        return shift * runLength(id);
    }

    /**
     * 计算 page 要在座的位置
     *
     * @param memoryMapIdx
     * @return
     */
    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        return freeBytes;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage())
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }
}
