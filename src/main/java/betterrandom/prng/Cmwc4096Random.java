// ============================================================================
//   Copyright 2006-2012 Daniel W. Dyer
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
// ============================================================================
package betterrandom.prng;

import betterrandom.EntropyCountingRandom;
import betterrandom.RepeatableRandom;
import betterrandom.seed.DefaultSeedGenerator;
import betterrandom.seed.SeedException;
import betterrandom.seed.SeedGenerator;
import betterrandom.util.BinaryUtils;
import java.util.Arrays;
import java.util.Random;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * <p>A Java version of George Marsaglia's <a href="http://school.anhb.uwa.edu.au/personalpages/kwessen/shared/Marsaglia03.html">Complementary
 * Multiply With Carry (CMWC) RNG</a>. This is a very fast PRNG with an extremely long period
 * (2^131104).  It should be used in preference to the {@link MersenneTwisterRandom} when a very
 * long period is required.</p>
 *
 * <p>One potential drawback of this RNG is that it requires significantly more seed data than the
 * other RNGs provided by Uncommons Maths.  It requires just over 16 kilobytes, which may be a
 * problem if your are obtaining seed data from a slow or limited entropy source. In contrast, the
 * Mersenne Twister requires only 128 bits of seed data.</p>
 *
 * <p><em>NOTE: Because instances of this class require 16-kilobyte seeds, it is not possible to
 * seed this RNG using the {@link #setSeed(long)} method inherited from {@link Random}.  Calls to
 * this method will have no effect. Instead the seed must be set by a constructor.</em></p>
 *
 * @author Daniel Dyer
 * @since 1.2
 */
public class Cmwc4096Random extends BaseEntropyCountingRandom implements RepeatableRandom {

  private static final int SEED_SIZE_BYTES = 16384; // Needs 4,096 32-bit integers.

  private static final long A = 18782L;
  private static final long serialVersionUID = 1731465909906078875L;

  private int[] state;
  private int carry = 362436; // TO DO: This should be randomly generated.
  private int index = 4095;

  /**
   * Creates a new RNG and seeds it using the default seeding strategy.
   */
  public Cmwc4096Random() throws SeedException {
    this(DefaultSeedGenerator.INSTANCE.generateSeed(SEED_SIZE_BYTES));
  }


  /**
   * Seed the RNG using the provided seed generation strategy.
   *
   * @param seedGenerator The seed generation strategy that will provide the seed value for this
   * RNG.
   * @throws SeedException If there is a problem generating a seed.
   */
  public Cmwc4096Random(SeedGenerator seedGenerator) throws SeedException {
    this(seedGenerator.generateSeed(SEED_SIZE_BYTES));
  }


  /**
   * Creates an RNG and seeds it with the specified seed data.
   *
   * @param seed The seed data used to initialise the RNG.
   */
  public Cmwc4096Random(byte[] seed) {
    super(seed);
    setSeed(seed);
  }


  /**
   * {@inheritDoc}
   */
  public byte[] getSeed() {
    return seed.clone();
  }

  @EnsuresNonNull({"this.seed", "state"})
  @Override
  public void setSeed(@UnknownInitialization Cmwc4096Random this, byte[] seed) {
    assert entropyBits != null : "@AssumeAssertion(nullness)";
    if (seed == null || seed.length != SEED_SIZE_BYTES) {
      throw new IllegalArgumentException("CMWC RNG requires 16kb of seed data.");
    }
    this.seed = seed.clone();
    state = BinaryUtils.convertBytesToInts(seed);
    entropyBits.set(SEED_SIZE_BYTES * 8);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected int next(int bits) {
    lock.lock();
    try {
      index = (index + 1) & 4095;
      long t = A * (state[index] & 0xFFFFFFFFL) + carry;
      carry = (int) (t >> 32);
      int x = ((int) t) + carry;
      if (x < carry) {
        x++;
        carry++;
      }
      state[index] = 0xFFFFFFFE - x;
      recordEntropySpent(bits);
      return state[index] >>> (32 - bits);
    } finally {
      lock.unlock();
    }
  }

  @SuppressWarnings("NonFinalFieldReferenceInEquals")
  @Override
  public boolean equals(Object other) {
    return other instanceof Cmwc4096Random
        && Arrays.equals(seed, ((Cmwc4096Random) other).seed);
  }

  @SuppressWarnings("NonFinalFieldReferencedInHashCode")
  @Override
  public int hashCode() {
    return Arrays.hashCode(seed);
  }

  @Override
  public int getNewSeedLength() {
    return SEED_SIZE_BYTES;
  }
}
