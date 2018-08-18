package io.github.pr0methean.betterrandom.prng;

import static io.github.pr0methean.betterrandom.TestUtils.assertGreaterOrEqual;

import io.github.pr0methean.betterrandom.TestingDeficiency;
import io.github.pr0methean.betterrandom.prng.RandomTestUtils.EntropyCheckMode;
import io.github.pr0methean.betterrandom.seed.SecureRandomSeedGenerator;
import io.github.pr0methean.betterrandom.seed.SeedException;
import io.github.pr0methean.betterrandom.seed.SeedGenerator;
import java.io.Serializable;
import java.util.Random;
import java.util.function.Supplier;
import org.testng.annotations.Test;

@Test(testName = "ReseedingThreadLocalRandomWrapper:MersenneTwisterRandom")
public class ReseedingThreadLocalRandomWrapperMersenneTwisterTest
    extends ThreadLocalRandomWrapperMersenneTwisterTest {

  private Supplier<? extends BaseRandom> mtSupplier;

  public ReseedingThreadLocalRandomWrapperMersenneTwisterTest() {
    // Must be done first, or else lambda won't be serializable.
    SeedGenerator seedGenerator = getTestSeedGenerator();

    mtSupplier = (Serializable & Supplier<BaseRandom>)
        () -> new MersenneTwisterRandom(seedGenerator);
  }

  @TestingDeficiency
  @Override protected SeedGenerator getTestSeedGenerator() {
    // FIXME: Statistical tests often fail when using semiFakeSeedGenerator
    return SecureRandomSeedGenerator.SECURE_RANDOM_SEED_GENERATOR;
  }

  @Override public void testWrapLegacy() throws SeedException {
    ReseedingThreadLocalRandomWrapper.wrapLegacy(Random::new, getTestSeedGenerator()).nextInt();
  }

  @Override protected EntropyCheckMode getEntropyCheckMode() {
    return EntropyCheckMode.LOWER_BOUND;
  }

  @Override protected Class<? extends BaseRandom> getClassUnderTest() {
    return ReseedingThreadLocalRandomWrapper.class;
  }

  @SuppressWarnings("BusyWait") @Override @Test public void testReseeding() {
    final SeedGenerator testSeedGenerator = getTestSeedGenerator();
    final BaseRandom rng = new ReseedingThreadLocalRandomWrapper(testSeedGenerator, mtSupplier);
    RandomTestUtils.testReseeding(testSeedGenerator, rng, false);
  }

  /** Assertion-free since reseeding may cause divergent output. */
  @Override @Test public void testSetSeedLong() {
    createRng().setSeed(0x0123456789ABCDEFL);
  }

  /** Test for crashes only, since setSeed is a no-op. */
  @Override @Test public void testSetSeedAfterNextLong() throws SeedException {
    final BaseRandom prng = createRng();
    prng.nextLong();
    prng.setSeed(getTestSeedGenerator().generateSeed(16));
    prng.nextLong();
  }

  /** Test for crashes only, since setSeed is a no-op. */
  @Override @Test public void testSetSeedAfterNextInt() throws SeedException {
    final BaseRandom prng = createRng();
    prng.nextInt();
    prng.setSeed(getTestSeedGenerator().generateSeed(16));
    prng.nextInt();
  }

  /** setSeedGenerator doesn't work on this class and shouldn't pretend to. */
  @Override @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testRandomSeederThreadIntegration() {
    createRng().setSeedGenerator(SecureRandomSeedGenerator.SECURE_RANDOM_SEED_GENERATOR);
  }
  
  @Override @Test public void testSetSeedGeneratorNoOp() {
    createRng().setSeedGenerator(getTestSeedGenerator());
  }

  @Override protected BaseRandom createRng() throws SeedException {
    return new ReseedingThreadLocalRandomWrapper(getTestSeedGenerator(), mtSupplier);
  }
}
