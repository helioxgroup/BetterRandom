package io.github.pr0methean.betterrandom.prng;

import io.github.pr0methean.betterrandom.TestingDeficiency;
import io.github.pr0methean.betterrandom.util.BinaryUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import org.testng.annotations.Test;

import static io.github.pr0methean.betterrandom.prng.BaseRandom.entropyOfInt;
import static io.github.pr0methean.betterrandom.prng.BaseRandom.entropyOfLong;
import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link BaseRandom} that are not heritable by tests of subclasses.
 */
@Test(testName = "BaseRandom statics")
public class BaseRandomStaticTest {

  @Test(expectedExceptions = InvalidObjectException.class) public void testReadObjectNoData()
      throws IOException, ClassNotFoundException {
    try (final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
        BinaryUtils.convertHexStringToBytes(
            "aced000573720037696f2e6769746875622e7072306d65746865616e2e62657474657272616e646f6d2e" +
            "70726e672e416573436f756e74657252616e646f6d5291dc26ea2fa68a0200007870")))) {
      ((AesCounterRandom) ois.readObject()).nextInt();
    }

  }

  @Test public void testEntropyOfInt() {
    assertEquals(entropyOfInt(0, 1), 0);
    assertEquals(entropyOfInt(0, 2), 1);
    assertEquals(entropyOfInt(0, 1 << 24), 24);
    assertEquals(entropyOfInt(1 << 22, 1 << 24), 24);
    assertEquals(entropyOfInt(-(1 << 24), 0), 24);
    assertEquals(entropyOfInt(-(1 << 24), 1), 25);
  }

  @Test public void testEntropyOfLong() {
    assertEquals(entropyOfLong(0, 1), 0);
    assertEquals(entropyOfLong(0, 2), 1);
    assertEquals(entropyOfLong(0, 1L << 32), 32);
    assertEquals(entropyOfLong(0, 1L << 42), 42);
    assertEquals(entropyOfLong(0, Long.MAX_VALUE), 63);
    assertEquals(entropyOfLong(Long.MIN_VALUE, Long.MAX_VALUE), 64);
    assertEquals(entropyOfLong(1 << 22, 1L << 42), 42);
    assertEquals(entropyOfLong(-(1L << 42), 0), 42);
    assertEquals(entropyOfLong(-(1L << 42), 1), 43);
  }
}
