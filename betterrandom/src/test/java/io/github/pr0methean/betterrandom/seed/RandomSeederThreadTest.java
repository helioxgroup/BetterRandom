package io.github.pr0methean.betterrandom.seed;

import io.github.pr0methean.betterrandom.prng.RandomTestUtils;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;
import org.testng.annotations.Test;

import static io.github.pr0methean.betterrandom.seed.RandomSeederThread.stopAllEmpty;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RandomSeederThreadTest {

  private static final long TEST_SEED = 0x0123456789ABCDEFL;
  private static final int TEST_OUTPUT_SIZE = 20;

  private static final boolean ON_LINUX
      = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
          .contains("nux");

  @Test(timeOut = 25_000) public void testAddRemoveAndIsEmpty() throws Exception {
    final Random prng = new Random(TEST_SEED);
    final byte[] bytesWithOldSeed = new byte[TEST_OUTPUT_SIZE];
    prng.nextBytes(bytesWithOldSeed);
    prng.setSeed(TEST_SEED); // Rewind
    final SeedGenerator seedGenerator = new FakeSeedGenerator("testAddRemoveAndIsEmpty");
    try {
      assertTrue(RandomSeederThread.isEmpty(seedGenerator));
      RandomSeederThread.add(seedGenerator, prng);
      assertFalse(RandomSeederThread.isEmpty(seedGenerator));
      sleepUninterruptibly(100_000_000); // FIXME: Why does this sleep get interrupted?!
      assertFalse(RandomSeederThread.isEmpty(seedGenerator));
    } finally {
      RandomTestUtils.removeAndAssertEmpty(seedGenerator, prng);
    }
    final byte[] bytesWithNewSeed = new byte[TEST_OUTPUT_SIZE];
    prng.nextBytes(bytesWithNewSeed);
    if (ON_LINUX) {
      // FIXME: Fails without the Thread.sleep call
      assertFalse(Arrays.equals(bytesWithOldSeed, bytesWithNewSeed));
    }
  }

  @Test public void testStopIfEmpty() {
    final SeedGenerator seedGenerator = new FakeSeedGenerator("testStopIfEmpty");
    final Random prng = new Random();
    RandomSeederThread.add(seedGenerator, prng);
    RandomSeederThread.stopIfEmpty(seedGenerator);
    assertTrue(RandomSeederThread.hasInstance(seedGenerator));
    RandomTestUtils.removeAndAssertEmpty(seedGenerator, prng);
  }

  @Test public void testStopAllEmpty() {
    final SeedGenerator neverAddedTo = new FakeSeedGenerator("neverAddedTo");
    final SeedGenerator addedToAndRemoved = new FakeSeedGenerator("addedToAndRemoved");
    final SeedGenerator addedToAndLeft = new FakeSeedGenerator("addedToAndLeft");
    final Random addedAndRemoved = new Random();
    final Random addedAndLeft = new Random();
    RandomSeederThread.add(addedToAndRemoved, addedAndRemoved);
    RandomSeederThread.remove(addedToAndRemoved, addedAndRemoved);
    RandomSeederThread.add(addedToAndLeft, addedAndLeft);
    assertFalse(RandomSeederThread.hasInstance(neverAddedTo));
    assertTrue(RandomSeederThread.hasInstance(addedToAndRemoved));
    assertTrue(RandomSeederThread.hasInstance(addedToAndLeft));
    stopAllEmpty();
    assertFalse(RandomSeederThread.hasInstance(neverAddedTo));
    assertFalse(RandomSeederThread.hasInstance(addedToAndRemoved));
    assertTrue(RandomSeederThread.hasInstance(addedToAndLeft));
    addedAndLeft.nextInt(); // prevent GC before this point
  }

  private static void assertOneThreadPriority7(String expectedName) {
    final Thread[] threads = new Thread[10 + Thread.activeCount()];
    final int nThreads = Thread.enumerate(threads);
    int found = 0;
    for (int i = 0; i < nThreads; i++) {
      if (expectedName.equals(threads[i].getName())) {
        assertEquals(threads[i].getPriority(), 7);
      }
    }
    assertEquals(found, 1);
  }

  private void sleepUninterruptibly(long nanos) {
    long curTime = System.nanoTime();
    long endTime = curTime + nanos;
    do {
      LockSupport.parkNanos(endTime - curTime);
      curTime = System.nanoTime();
    } while (curTime < endTime);
  }
}
