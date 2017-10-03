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
package io.github.pr0methean.betterrandom.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Connects to <a href="https://www.random.org/clients/http/" target="_top">random.org's old API</a> (via HTTPS)
 * and downloads a set of random bits to use as seed data.  It is generally better to use the {@link
 * DevRandomSeedGenerator} where possible, as it should be much quicker. This seed generator is most
 * useful on Microsoft Windows without Cygwin, and other platforms that do not provide {@literal
 * /dev/random}.</p>
 * <p>Random.org collects randomness from atmospheric noise using 9 radios, located at undisclosed
 * addresses in Dublin and Copenhagen and tuned to undisclosed AM/FM frequencies. (The secrecy is
 *  intended to help prevent tampering with the output using a well-placed radio transmitter, and the
 * use of AM/FM helps ensure that any such tampering would cause illegal interference with broadcasts
 *  and quickly attract regulatory attention.) Note that random.org limits the supply of free random
 *  numbers to any one IP address; if you operate from a fixed address (at least if you use IPv4), you
 *  can <a href="https://www.random.org/quota/">check your quota and buy more</a>.
 *
 * @author Daniel Dyer
 * @author Chris Hennick
 */
public enum RandomDotOrgSeedGenerator implements SeedGenerator {

  /**
   * This version of the client may make HTTP requests as fast as your computer is capable of
   * sending them. Since it is inherently spammy, it is recommended only when you know your usage is
   * light and/or no other source of randomness will do.
   */
  RANDOM_DOT_ORG_SEED_GENERATOR(false),
  
  /**
   * Upon a failed request, this version of the client requires that you wait 10 seconds before
   * trying again. The {@link DefaultSeedGenerator} uses it and will fall back to the {@link
   * SecureRandomSeedGenerator} during this waiting period.
   */
  RATE_LIMITED_ON_FAIL(true);

  public static final Clock CLOCK = Clock.systemUTC();
  public static final int MAX_CACHE_SIZE = 1024;
  private static final String BASE_URL = "https://www.random.org";
  /**
   * The URL from which the random bytes are retrieved.
   */
  @SuppressWarnings("HardcodedFileSeparator")
  private static final String RANDOM_URL =
      BASE_URL + "/integers/?num={0,number,0}&min=0&max=255&col=1&base=16&format=plain&rnd=new";
  /**
   * Used to identify the client to the random.org service.
   */
  private static final String USER_AGENT = RandomDotOrgSeedGenerator.class.getName();
  /**
   * Random.org does not allow requests for more than 10k integers at once.
   */
  private static final int GLOBAL_MAX_REQUEST_SIZE = 10000;
  private static final Duration COOLDOWN_ON_FAILURE = Duration.ofSeconds(10);
  private static final Lock cacheLock = new ReentrantLock();
  private static Instant EARLIEST_NEXT_ATTEMPT = Instant.MIN;
  private static byte[] cache = new byte[MAX_CACHE_SIZE];
  private static int cacheOffset = cache.length;
  private static int maxRequestSize = GLOBAL_MAX_REQUEST_SIZE;

  /**
   * If true, don't attempt to contact random.org again for COOLDOWN_ON_FAILURE after an
   * IOException
   */
  private final boolean rateLimitOnFailure;

  RandomDotOrgSeedGenerator(final boolean rateLimitOnFailure) {
    this.rateLimitOnFailure = rateLimitOnFailure;
  }

  /**
   * @param requiredBytes The preferred number of bytes to request from random.org. The
   *     implementation may request more and cache the excess (to avoid making lots of small
   *     requests). Alternatively, it may request fewer if the required number is greater than that
   *     permitted by random.org for a single request.
   * @throws IOException If there is a problem downloading the random bits.
   */
  @SuppressWarnings("NumericCastThatLosesPrecision")
  private static void refreshCache(final int requiredBytes) throws IOException {
    cacheLock.lock();
    try {
      int numberOfBytes = Math.max(requiredBytes, cache.length);
      numberOfBytes = Math.min(numberOfBytes, maxRequestSize);
      if (numberOfBytes != cache.length) {
        cache = new byte[numberOfBytes];
        cacheOffset = numberOfBytes;
      }
      final URL url = new URL(MessageFormat.format(RANDOM_URL, numberOfBytes));
      final URLConnection connection = url.openConnection();
      connection.setRequestProperty("User-Agent", USER_AGENT);

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(connection.getInputStream()))) {
        int index = -1;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          ++index;
          cache[index] = (byte) Integer.parseInt(line, 16);
          // Can't use Byte.parseByte, since it expects signed
        }
        if (index < (cache.length - 1)) {
          throw new IOException("Insufficient data received.");
        }
        cacheOffset = 0;
      }
    } finally {
      cacheLock.unlock();
    }
  }

  /**
   * Sets the maximum request size that we will expect random.org to allow. If more than {@link
   * #GLOBAL_MAX_REQUEST_SIZE}, will be set to that value instead.
   *
   * @param maxRequestSize the new maximum request size in bytes.
   */
  public static void setMaxRequestSize(int maxRequestSize) {
    maxRequestSize = Math.min(maxRequestSize, GLOBAL_MAX_REQUEST_SIZE);
    final int maxNewCacheSize = Math.min(maxRequestSize, MAX_CACHE_SIZE);
    cacheLock.lock();
    try {
      final int sizeChange = maxNewCacheSize - cache.length;
      if (sizeChange > 0) {
        final byte[] newCache = new byte[maxNewCacheSize];
        final int newCacheOffset = cacheOffset + sizeChange;
        System.arraycopy(cache, cacheOffset, newCache, newCacheOffset, cache.length - cacheOffset);
        cache = newCache;
      }
      RandomDotOrgSeedGenerator.maxRequestSize = maxRequestSize;
    } finally {
      cacheLock.unlock();
    }
  }

  @Override
  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void generateSeed(final byte[] seedData) throws SeedException {
    if (!isWorthTrying()) {
      throw new SeedException("Not retrying so soon after an IOException");
    }
    final int length = seedData.length;
    cacheLock.lock();
    try {
      int count = 0;
      while (count < length) {
        if (cacheOffset < cache.length) {
          final int numberOfBytes = Math.min(length - count, cache.length - cacheOffset);
          System.arraycopy(cache, cacheOffset, seedData, count, numberOfBytes);
          count += numberOfBytes;
          cacheOffset += numberOfBytes;
        } else {
          refreshCache(length - count);
        }
      }
    } catch (final IOException ex) {
      EARLIEST_NEXT_ATTEMPT = CLOCK.instant().plus(COOLDOWN_ON_FAILURE);
      throw new SeedException("Failed downloading bytes from " + BASE_URL, ex);
    } catch (final SecurityException ex) {
      // Might be thrown if resource access is restricted (such as in an applet sandbox).
      throw new SeedException("SecurityManager prevented access to " + BASE_URL, ex);
    } finally {
      cacheLock.unlock();
    }
  }

  @Override
  public boolean isWorthTrying() {
    return !rateLimitOnFailure || !EARLIEST_NEXT_ATTEMPT.isAfter(CLOCK.instant());
  }

  @Override
  public String toString() {
    return BASE_URL;
  }
}
