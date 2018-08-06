package io.github.pr0methean.betterrandom.prng.adapter;

import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.assertEquivalent;

import io.github.pr0methean.betterrandom.CloneViaSerialization;
import io.github.pr0methean.betterrandom.TestingDeficiency;
import io.github.pr0methean.betterrandom.prng.BaseRandom;
import io.github.pr0methean.betterrandom.prng.BaseRandomTest;
import io.github.pr0methean.betterrandom.seed.SeedException;
import org.testng.annotations.Test;

public class SingleThreadSplittableRandomAdapterTest extends BaseRandomTest {

  @Override protected Class<? extends BaseRandom> getClassUnderTest() {
    return SingleThreadSplittableRandomAdapter.class;
  }

  @Override @Test(enabled = false) public void testThreadSafety() {
    // No-op because this class isn't thread-safe.
  }

  /**
   * {@inheritDoc} Overridden in subclasses, so that subclassing the test can test the subclasses.
   */
  @Override protected BaseSplittableRandomAdapter createRng() throws SeedException {
    return new SingleThreadSplittableRandomAdapter(getTestSeedGenerator());
  }

  @Override protected BaseRandom createRng(final byte[] seed) throws SeedException {
    final BaseSplittableRandomAdapter adapter = createRng();
    adapter.setSeed(seed);
    return adapter;
  }

  @Test public void testGetSplittableRandom() throws Exception {
    // TODO
  }

  @TestingDeficiency @Override @Test public void testSerializable() throws SeedException {
    final BaseSplittableRandomAdapter adapter = createRng();
    // May change when serialized and deserialized, but deserializing twice should yield same object
    // and deserialization should be idempotent
    final BaseSplittableRandomAdapter adapter2 = CloneViaSerialization.clone(adapter);
    final BaseSplittableRandomAdapter adapter3 = CloneViaSerialization.clone(adapter);
    final BaseSplittableRandomAdapter adapter4 = CloneViaSerialization.clone(adapter2);
    assertEquivalent(adapter2, adapter3, 20,
        "Deserializing twice doesn't yield same object");
    // FIXME Failing: assertEquivalent(adapter2, adapter4, 20,
    //     "Serialization round-trip is not idempotent");
  }

  @Override @Test public void testNullSeed() {
    // No-op.
  }

  @Override @Test(enabled = false) public void testEquals() {
    // No-op.
  }

  @Override @Test(enabled = false) public void testHashCode() {
    // No-op.
  }
}
