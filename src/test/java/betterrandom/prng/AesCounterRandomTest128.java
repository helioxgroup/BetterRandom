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

import static org.testng.Assert.assertTrue;

import betterrandom.seed.SeedException;
import betterrandom.util.LogPreFormatter;
import java.security.GeneralSecurityException;
import java.util.Random;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testng.annotations.Test;

/**
 * Unit test for the AES RNG.
 *
 * @author Daniel Dyer
 */
public class AesCounterRandomTest128 extends BaseEntropyCountingRandomTest {

  private static final LogPreFormatter LOG = new LogPreFormatter(AesCounterRandomTest128.class);

  @Override
  @Test(timeOut = 15000)
  public void testSetSeed() throws SeedException {
    // can't use a real SeedGenerator since we need longs, so use a Random
    Random masterRNG = new Random();
    long[] seeds = {masterRNG.nextLong(), masterRNG.nextLong(),
        masterRNG.nextLong(), masterRNG.nextLong()};
    long otherSeed = masterRNG.nextLong();
    AesCounterRandom[] rngs = {new AesCounterRandom(16), new AesCounterRandom(16)};
    for (int i = 0; i < 2; i++) {
      for (long seed : seeds) {
        byte[] originalSeed = rngs[i].getSeed();
        assertTrue(originalSeed.length >= 16);
        AesCounterRandom rngReseeded = new AesCounterRandom(originalSeed);
        assertTrue(rngReseeded.isSeeded());
        AesCounterRandom rngReseededOther = new AesCounterRandom(originalSeed);
        rngReseeded.setSeed(seed);
        rngReseededOther.setSeed(otherSeed);
        assert !(rngs[i].equals(rngReseeded));
        assert !(rngReseededOther.equals(rngReseeded));
        assert rngs[i].nextLong() != rngReseeded.nextLong()
            : "setSeed had no effect";
        rngs[i] = rngReseeded;
      }
    }
    assert rngs[0].nextLong() != rngs[1].nextLong()
        : "RNGs converged after 4 setSeed calls";
  }

  @Test(timeOut = 15000)
  public void testMaxSeedLengthOk() {
    assert AesCounterRandom.getMaxKeyLengthBytes() >= 16 :
        "Should allow a 16-byte key";
    assert AesCounterRandom.getMaxKeyLengthBytes() <= 32 :
        "Shouldn't allow a key longer than 32 bytes";
  }

  @Override
  protected BaseEntropyCountingRandom tryCreateRng() throws SeedException {
    return new AesCounterRandom(16);
  }

  @Override
  protected BaseRandom createRng(byte[] seed) throws SeedException {
    return new AesCounterRandom(seed);
  }
}
