/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.internal.offheap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.googlecode.catchexception.CatchException.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.OutOfOffHeapMemoryException;
import com.gemstone.gemfire.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class FreeListManagerTest {
  static {
    ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
  }

  private final int DEFAULT_SLAB_SIZE = 1024*1024*5;
  private final SimpleMemoryAllocatorImpl ma = mock(SimpleMemoryAllocatorImpl.class);
  private final OffHeapMemoryStats stats = mock(OffHeapMemoryStats.class);
  private FreeListManager freeListManager;
  

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    when(ma.getStats()).thenReturn(stats);
  }

  @After
  public void tearDown() throws Exception {
    if (this.freeListManager != null) {
      this.freeListManager.freeSlabs();
    }
  }
  
  private static FreeListManager createFreeListManager(SimpleMemoryAllocatorImpl ma, AddressableMemoryChunk[] slabs) {
    return new TestableFreeListManager(ma, slabs);
  }
  
  private void setUpSingleSlabManager() {
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {slab});
  }

  @Test
  public void usedMemoryIsZeroOnDefault() {
    setUpSingleSlabManager();
    assertThat(this.freeListManager.getUsedMemory()).isZero();
  }

  @Test
  public void freeMemoryIsSlabSizeOnDefault() {
    setUpSingleSlabManager();
    assertThat(this.freeListManager.getFreeMemory()).isEqualTo(DEFAULT_SLAB_SIZE);
  }
  
  @Test
  public void totalMemoryIsSlabSizeOnDefault() {
    setUpSingleSlabManager();
    assertThat(this.freeListManager.getTotalMemory()).isEqualTo(DEFAULT_SLAB_SIZE);
  }
  
  @Test
  public void allocateTinyChunkHasCorrectSize() {
    setUpSingleSlabManager();
    int tinySize = 10;

    Chunk c = this.freeListManager.allocate(tinySize);
    
    validateChunkSizes(c, tinySize);
  }
  
  private void validateChunkSizes(Chunk c, int dataSize) {
    assertThat(c).isNotNull();
    assertThat(c.getDataSize()).isEqualTo(dataSize);
    assertThat(c.getSize()).isEqualTo(computeExpectedSize(dataSize));
  }

  @Test
  public void allocateTinyChunkFromFreeListHasCorrectSize() {
    setUpSingleSlabManager();
    int tinySize = 10;
    
    Chunk c = this.freeListManager.allocate(tinySize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    c = this.freeListManager.allocate(tinySize);

    validateChunkSizes(c, tinySize);
  }
  
  @Test
  public void allocateTinyChunkFromEmptyFreeListHasCorrectSize() {
    setUpSingleSlabManager();
    int dataSize = 10;
    
    Chunk c = this.freeListManager.allocate(dataSize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    this.freeListManager.allocate(dataSize);
    // free list will now be empty
    c = this.freeListManager.allocate(dataSize);

    validateChunkSizes(c, dataSize);
  }

  @Test
  public void allocateHugeChunkHasCorrectSize() {
    setUpSingleSlabManager();
    int hugeSize = FreeListManager.MAX_TINY+1;

    Chunk c = this.freeListManager.allocate(hugeSize);

    validateChunkSizes(c, hugeSize);
  }
  
  @Test
  public void allocateHugeChunkFromEmptyFreeListHasCorrectSize() {
    setUpSingleSlabManager();
    int dataSize = FreeListManager.MAX_TINY+1;
    
    Chunk c = this.freeListManager.allocate(dataSize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    this.freeListManager.allocate(dataSize);
    // free list will now be empty
    c = this.freeListManager.allocate(dataSize);
    
    validateChunkSizes(c, dataSize);
  }

  @Test
  public void allocateHugeChunkFromFragmentWithItemInFreeListHasCorrectSize() {
    setUpSingleSlabManager();
    int dataSize = FreeListManager.MAX_TINY+1+1024;
    
    Chunk c = this.freeListManager.allocate(dataSize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    dataSize = FreeListManager.MAX_TINY+1;
    c = this.freeListManager.allocate(dataSize);
    
    validateChunkSizes(c, dataSize);
  }
  @Test
  public void freeTinyMemoryDefault() {
    setUpSingleSlabManager();
    
    assertThat(this.freeListManager.getFreeTinyMemory()).isZero();
  }
  @Test
  public void freeTinyMemoryEqualToChunkSize() {
    setUpSingleSlabManager();
    int dataSize = 10;
    
    Chunk c = this.freeListManager.allocate(dataSize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    
    assertThat(this.freeListManager.getFreeTinyMemory()).isEqualTo(computeExpectedSize(dataSize));
  }
   
  @Test
  public void freeHugeMemoryDefault() {
    setUpSingleSlabManager();
    
    assertThat(this.freeListManager.getFreeHugeMemory()).isZero();
  }
  @Test
  public void freeHugeMemoryEqualToChunkSize() {
    setUpSingleSlabManager();
    int dataSize = FreeListManager.MAX_TINY+1+1024;
    
    Chunk c = this.freeListManager.allocate(dataSize);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    
    assertThat(this.freeListManager.getFreeHugeMemory()).isEqualTo(computeExpectedSize(dataSize));
  }
  
  @Test
  public void freeFragmentMemoryDefault() {
    setUpSingleSlabManager();
    
    assertThat(this.freeListManager.getFreeFragmentMemory()).isEqualTo(DEFAULT_SLAB_SIZE);
  }
  
  @Test
  public void freeFragmentMemorySomeOfFragmentAllocated() {
    setUpSingleSlabManager();
    Chunk c = this.freeListManager.allocate(DEFAULT_SLAB_SIZE/4-8);
    
    assertThat(this.freeListManager.getFreeFragmentMemory()).isEqualTo((DEFAULT_SLAB_SIZE/4)*3);
  }
  
  @Test
  public void freeFragmentMemoryMostOfFragmentAllocated() {
    setUpSingleSlabManager();
    Chunk c = this.freeListManager.allocate(DEFAULT_SLAB_SIZE-8-8);
    
    assertThat(this.freeListManager.getFreeFragmentMemory()).isZero();
  }
  
  private int computeExpectedSize(int dataSize) {
    return ((dataSize + Chunk.OFF_HEAP_HEADER_SIZE + 7) / 8) * 8;
  }

  @Test(expected = AssertionError.class)
  public void allocateZeroThrowsAssertion() {
    setUpSingleSlabManager();
    this.freeListManager.allocate(0);
  }
  
  @Test
  public void allocateFromMultipleSlabs() {
    int SMALL_SLAB = 16;
    int MEDIUM_SLAB = 128;
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(MEDIUM_SLAB), 
        slab});
    this.freeListManager.allocate(SMALL_SLAB-8+1);
    this.freeListManager.allocate(DEFAULT_SLAB_SIZE-8);
    this.freeListManager.allocate(SMALL_SLAB-8+1);
    assertThat(this.freeListManager.getFreeMemory()).isEqualTo(SMALL_SLAB*2+MEDIUM_SLAB-((SMALL_SLAB+8)*2));
    assertThat(this.freeListManager.getUsedMemory()).isEqualTo(DEFAULT_SLAB_SIZE+(SMALL_SLAB+8)*2);
    assertThat(this.freeListManager.getTotalMemory()).isEqualTo(DEFAULT_SLAB_SIZE+MEDIUM_SLAB+SMALL_SLAB*2);
  }
  
  @Test
  public void compactWithLargeChunkSizeReturnsFalse() {
    int SMALL_SLAB = 16;
    int MEDIUM_SLAB = 128;
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(MEDIUM_SLAB), 
        slab});
    ArrayList<Chunk> chunks = new ArrayList<>();
    chunks.add(this.freeListManager.allocate(SMALL_SLAB-8+1));
    chunks.add(this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8));
    chunks.add(this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8));
    chunks.add(this.freeListManager.allocate(SMALL_SLAB-8+1));
    for (Chunk c: chunks) {
      Chunk.release(c.getMemoryAddress(), this.freeListManager);
    }
    
    assertThat(this.freeListManager.compact(DEFAULT_SLAB_SIZE+1)).isFalse();
  }
  
  @Test
  public void compactWithChunkSizeOfMaxSlabReturnsTrue() {
    int SMALL_SLAB = 16;
    int MEDIUM_SLAB = 128;
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(MEDIUM_SLAB), 
        slab});
    ArrayList<Chunk> chunks = new ArrayList<>();
    chunks.add(this.freeListManager.allocate(SMALL_SLAB-8+1));
    chunks.add(this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8));
    chunks.add(this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8));
    chunks.add(this.freeListManager.allocate(SMALL_SLAB-8+1));
    for (Chunk c: chunks) {
      Chunk.release(c.getMemoryAddress(), this.freeListManager);
    }
    
    assertThat(this.freeListManager.compact(DEFAULT_SLAB_SIZE)).isTrue();
    //assertThat(this.freeListManager.getFragmentList()).hasSize(4); // TODO intermittently fails because Fragments may be merged
  }
  
  @Test
  public void compactWithLiveChunks() {
    int SMALL_SLAB = 16;
    int MEDIUM_SLAB = 128;
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(MEDIUM_SLAB), 
        slab});
    ArrayList<Chunk> chunks = new ArrayList<>();
    chunks.add(this.freeListManager.allocate(SMALL_SLAB-8+1));
    this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8);
    chunks.add(this.freeListManager.allocate(DEFAULT_SLAB_SIZE/2-8));
    this.freeListManager.allocate(SMALL_SLAB-8+1);
    for (Chunk c: chunks) {
      Chunk.release(c.getMemoryAddress(), this.freeListManager);
    }
    
    assertThat(this.freeListManager.compact(DEFAULT_SLAB_SIZE/2)).isTrue();
  }
  
  @Test
  public void compactAfterAllocatingAll() {
    setUpSingleSlabManager();
    Chunk c = freeListManager.allocate(DEFAULT_SLAB_SIZE-8);
    
    assertThat(this.freeListManager.compact(1)).isFalse();
    // call compact twice for extra code coverage
    assertThat(this.freeListManager.compact(1)).isFalse();
    assertThat(this.freeListManager.getFragmentList()).isEmpty();
  }
  
  @Test
  public void compactWithEmptyTinyFreeList() {
    setUpSingleSlabManager();
    Fragment originalFragment = this.freeListManager.getFragmentList().get(0);
    Chunk c = freeListManager.allocate(16);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    c = freeListManager.allocate(16);
    
    assertThat(this.freeListManager.compact(1)).isTrue();
    assertThat(this.freeListManager.getFragmentList()).hasSize(1);
    Fragment compactedFragment = this.freeListManager.getFragmentList().get(0);
    assertThat(compactedFragment.getSize()).isEqualTo(originalFragment.getSize()-(16+8));
    assertThat(compactedFragment.getMemoryAddress()).isEqualTo(originalFragment.getMemoryAddress()+(16+8));
  }
  
  @Test
  public void allocationsThatLeaveLessThanMinChunkSizeFreeInAFragment() {
    int SMALL_SLAB = 16;
    int MEDIUM_SLAB = 128;
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(SMALL_SLAB), 
        new UnsafeMemoryChunk(MEDIUM_SLAB), 
        slab});
    this.freeListManager.allocate(DEFAULT_SLAB_SIZE-8-(Chunk.MIN_CHUNK_SIZE-1));
    this.freeListManager.allocate(MEDIUM_SLAB-8-(Chunk.MIN_CHUNK_SIZE-1));
    
    assertThat(this.freeListManager.compact(SMALL_SLAB)).isTrue();
  }
 @Test
  public void maxAllocationUsesAllMemory() {
    setUpSingleSlabManager();

    this.freeListManager.allocate(DEFAULT_SLAB_SIZE-8);

    assertThat(this.freeListManager.getFreeMemory()).isZero();
    assertThat(this.freeListManager.getUsedMemory()).isEqualTo(DEFAULT_SLAB_SIZE);
  }


  @Test
  public void overMaxAllocationFails() {
    setUpSingleSlabManager();
    OutOfOffHeapMemoryListener ooohml = mock(OutOfOffHeapMemoryListener.class);
    when(this.ma.getOutOfOffHeapMemoryListener()).thenReturn(ooohml);

    catchException(this.freeListManager).allocate(DEFAULT_SLAB_SIZE-7);

    verify(ooohml).outOfOffHeapMemory(caughtException());
  }
  
  @Test(expected = AssertionError.class)
  public void allocateNegativeThrowsAssertion() {
    setUpSingleSlabManager();
    this.freeListManager.allocate(-123);
  }
  
  @Test
  public void hugeMultipleLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyHugeMultiple(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("HUGE_MULTIPLE must be >= 0 and <= " + FreeListManager.HUGE_MULTIPLE + " but it was -1");
    }
  }
  @Test
  public void hugeMultipleGreaterThan256IsIllegal() {
    try {
      FreeListManager.verifyHugeMultiple(257);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("HUGE_MULTIPLE must be >= 0 and <= 256 but it was 257");
    }
  }
  @Test
  public void hugeMultipleof256IsLegal() {
    FreeListManager.verifyHugeMultiple(256);
  }
  
  @Test
  public void offHeapFreeListCountLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapFreeListCount(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("gemfire.OFF_HEAP_FREE_LIST_COUNT must be >= 1.");
    }
  }
  @Test
  public void offHeapFreeListCountOfZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapFreeListCount(0);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("gemfire.OFF_HEAP_FREE_LIST_COUNT must be >= 1.");
    }
  }
  @Test
  public void offHeapFreeListCountOfOneIsLegal() {
    FreeListManager.verifyOffHeapFreeListCount(1);
  }
  @Test
  public void offHeapAlignmentLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("gemfire.OFF_HEAP_ALIGNMENT must be a multiple of 8");
    }
  }
  @Test
  public void offHeapAlignmentNotAMultipleOf8IsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(9);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("gemfire.OFF_HEAP_ALIGNMENT must be a multiple of 8");
    }
  }
  @Test
  public void offHeapAlignmentGreaterThan256IsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(256+8);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("gemfire.OFF_HEAP_ALIGNMENT must be <= 256");
    }
  }
  @Test
  public void offHeapAlignmentOf256IsLegal() {
    FreeListManager.verifyOffHeapAlignment(256);
  }
  
  @Test
  public void okToReuseNull() {
    setUpSingleSlabManager();
    assertThat(this.freeListManager.okToReuse(null)).isTrue();
  }
  
  @Test
  public void okToReuseSameSlabs() {
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    UnsafeMemoryChunk[] slabs = new UnsafeMemoryChunk[] {slab};
    this.freeListManager = createFreeListManager(ma, slabs);
    assertThat(this.freeListManager.okToReuse(slabs)).isTrue();
  }
  @Test
  public void notOkToReuseDifferentSlabs() {
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    UnsafeMemoryChunk[] slabs = new UnsafeMemoryChunk[] {slab};
    this.freeListManager = createFreeListManager(ma, slabs);
    UnsafeMemoryChunk[] slabs2 = new UnsafeMemoryChunk[] {slab};
    assertThat(this.freeListManager.okToReuse(slabs2)).isFalse();
  }
  @Test
  public void firstSlabAlwaysLargest() {
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {
        new UnsafeMemoryChunk(10), 
        new UnsafeMemoryChunk(100)});
    assertThat(this.freeListManager.getLargestSlabSize()).isEqualTo(10);
  }

  @Test
  public void findSlab() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    assertThat(this.freeListManager.findSlab(address)).isEqualTo(0);
    assertThat(this.freeListManager.findSlab(address+9)).isEqualTo(0);
    catchException(this.freeListManager).findSlab(address-1);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage("could not find a slab for addr " + (address-1));
    catchException(this.freeListManager).findSlab(address+10);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage("could not find a slab for addr " + (address+10));
  }
  
  @Test
  public void findSecondSlab() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    UnsafeMemoryChunk slab = new UnsafeMemoryChunk(DEFAULT_SLAB_SIZE);
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {slab, chunk});
    assertThat(this.freeListManager.findSlab(address)).isEqualTo(1);
    assertThat(this.freeListManager.findSlab(address+9)).isEqualTo(1);
    catchException(this.freeListManager).findSlab(address-1);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage("could not find a slab for addr " + (address-1));
    catchException(this.freeListManager).findSlab(address+10);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage("could not find a slab for addr " + (address+10));
  }
  
  @Test
  public void validateAddressWithinSlab() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address, -1)).isTrue();
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address+9, -1)).isTrue();
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address-1, -1)).isFalse();
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address+10, -1)).isFalse();
  }
  
  @Test
  public void validateAddressAndSizeWithinSlab() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address, 1)).isTrue();
    assertThat(this.freeListManager.validateAddressAndSizeWithinSlab(address, 10)).isTrue();
    catchException(this.freeListManager).validateAddressAndSizeWithinSlab(address, 0);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage(" address 0x" + Long.toString(address+0-1, 16) + " does not address the original slab memory");
    catchException(this.freeListManager).validateAddressAndSizeWithinSlab(address, 11);
    assertThat((Exception)caughtException())
    .isExactlyInstanceOf(IllegalStateException.class)
    .hasMessage(" address 0x" + Long.toString(address+11-1, 16) + " does not address the original slab memory");
  }
  
  @Test
  public void descriptionOfOneSlab() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    long endAddress = address+10;
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    StringBuilder sb = new StringBuilder();
    this.freeListManager.getSlabDescriptions(sb);
    assertThat(sb.toString()).isEqualTo("[" + Long.toString(address, 16) + ".." + Long.toString(endAddress, 16) + "] ");
  }

  @Test
  public void orderBlocksContainsFragment() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(10);
    long address = chunk.getMemoryAddress();
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    List<MemoryBlock> ob = this.freeListManager.getOrderedBlocks();
    assertThat(ob).hasSize(1);
    assertThat(ob.get(0).getMemoryAddress()).isEqualTo(address);
    assertThat(ob.get(0).getBlockSize()).isEqualTo(10);
  }
  
  @Test
  public void orderBlocksContainsTinyFree() {
    UnsafeMemoryChunk chunk = new UnsafeMemoryChunk(64);
    long address = chunk.getMemoryAddress();
    this.freeListManager = createFreeListManager(ma, new UnsafeMemoryChunk[] {chunk});
    Chunk c = this.freeListManager.allocate(24);
    Chunk c2 = this.freeListManager.allocate(24);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);

    List<MemoryBlock> ob = this.freeListManager.getOrderedBlocks();
    assertThat(ob).hasSize(3);
//    assertThat(ob.get(0).getMemoryAddress()).isEqualTo(address);
  }

  /**
   * Just like Fragment except that the first time allocate is called
   * it returns false indicating that the allocate failed.
   * In a real system this would only happen if a concurrent allocate
   * happened. This allows better code coverage.
   */
  private static class TestableFragment extends Fragment {
    private boolean allocateCalled = false;
    public TestableFragment(long addr, int size) {
      super(addr, size);
    }
    @Override
    public boolean allocate(int oldOffset, int newOffset) {
      if (!allocateCalled) {
        allocateCalled = true;
        return false;
      }
      return super.allocate(oldOffset, newOffset);
    }
  }
  private static class TestableFreeListManager extends FreeListManager {
    @Override
    protected Fragment createFragment(long addr, int size) {
      return new TestableFragment(addr, size);
    }

    public TestableFreeListManager(SimpleMemoryAllocatorImpl ma, AddressableMemoryChunk[] slabs) {
      super(ma, slabs);
    }
    
  }
}
