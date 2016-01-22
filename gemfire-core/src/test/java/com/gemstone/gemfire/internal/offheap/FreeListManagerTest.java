package com.gemstone.gemfire.internal.offheap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class FreeListManagerTest {
  static {
    ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
  }

  private final SimpleMemoryAllocatorImpl ma = mock(SimpleMemoryAllocatorImpl.class);
  private final UnsafeMemoryChunk slab = new UnsafeMemoryChunk(1024*1024*5);
  private final OffHeapMemoryStats stats = mock(OffHeapMemoryStats.class);
  private final ChunkFactory cf = new GemFireChunkFactory();
  private FreeListManager freeListManager;
  

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    when(ma.getSlabs()).thenReturn(new UnsafeMemoryChunk[] {slab});
    when(ma.getTotalMemory()).thenReturn((long) slab.getSize());
    when(ma.getStats()).thenReturn(stats);
    when(ma.getChunkFactory()).thenReturn(cf);
    
    this.freeListManager = new FreeListManager(ma);
  }

  @After
  public void tearDown() throws Exception {
    slab.release();
  }

  @Test
  public void usedMemoryIsZeroOnDefault() {
    assertEquals(0, this.freeListManager.getUsedMemory());
  }

  @Test
  public void freeMemoryIsAllocatorTotalOnDefault() {
    assertEquals(ma.getTotalMemory(), this.freeListManager.getFreeMemory());
  }
  
  @Test
  public void allocateTinyChunkHasCorrectSize() {
    Chunk c = this.freeListManager.allocate(10, null);
    assertNotNull(c);
    assertEquals(10, c.getDataSize());
    assertEquals(24, c.getSize());
  }

  @Test
  public void allocateTinyChunkWithExplicitTypeHasCorrectSize() {
    Chunk c = this.freeListManager.allocate(10, GemFireChunk.TYPE);
    assertNotNull(c);
    assertEquals(10, c.getDataSize());
    assertEquals(24, c.getSize());
  }
  
  @Test(expected = AssertionError.class)
  public void allocateZeroThrowsAssertion() {
    this.freeListManager.allocate(0, null);
  }
  
  @Test(expected = AssertionError.class)
  public void allocateNegativeThrowsAssertion() {
    this.freeListManager.allocate(-123, null);
  }
}
