package io.github.pr0methean.betterrandom.prng;

import com.google.common.base.MoreObjects;
import io.github.pr0methean.betterrandom.util.BinaryUtils;
import io.github.pr0methean.betterrandom.util.Byte16ArrayArithmetic;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;

/**
 * Non-linear random number generator based on a cipher that encrypts an incrementing counter.
 * fed. Subclasses must specify the key length for a given total seed length; whatever part
 * of the seed does not become the key, becomes the counter's initial value. When reseeded with a
 * seed of less than the maximum key length, the new seed is combined with the existing key using a
 * hash algorithm specified by the subclass.
 *
 * @author Daniel Dyer
 * @author Chris Hennick
 */
public abstract class CipherCounterRandom extends BaseRandom {
  private static final long serialVersionUID = 5949778642428995210L;
  protected final byte[] currentBlock;
  protected volatile byte[] counter;
  protected volatile int index;
  protected transient byte[] addendDigits;
  // WARNING: Don't initialize any instance fields at declaration; they may be initialized too late!
  @SuppressWarnings("InstanceVariableMayNotBeInitializedByReadObject")
  protected transient Cipher
      cipher;
  private volatile byte[] counterInput;
  private volatile boolean seeded;
  private transient MessageDigest hash;

  public CipherCounterRandom(byte[] seed) {
    super(seed);
    currentBlock = new byte[getBytesAtOnce()];
  }

  /**
   * Returns the maximum length in bytes of a key for this PRNG's cipher. If the seed is longer than
   * this, part of it becomes the counter's initial value. Otherwise, the full seed becomes the key
   * and the counter is initially zero.
   * @return the maximum length in bytes of a key.
   */
  public abstract int getMaxKeyLengthBytes();

  protected abstract int getKeyLength(byte[] input);

  public abstract int getCounterSizeBytes();

  public abstract int getBlocksAtOnce();

  public int getBytesAtOnce() {
    return getCounterSizeBytes() * getBlocksAtOnce();
  }

  public abstract int getMaxTotalSeedLengthBytes();

  @Override public MoreObjects.ToStringHelper addSubclassFields(final MoreObjects.ToStringHelper original) {
    return original.add("counter", BinaryUtils.convertBytesToHexString(counter))
        .add("cipher", cipher)
        .add("index", index);
  }

  @Override protected void initTransientFields() {
    super.initTransientFields();
    addendDigits = new byte[getCounterSizeBytes()];
    if (counter == null) {
      counter = new byte[getCounterSizeBytes()];
    }
    if (counterInput == null) {
      counterInput = new byte[getBytesAtOnce()];
    }
    cipher = createCipher();
    hash = createHash();
  }

  protected abstract MessageDigest createHash();

  protected abstract Cipher createCipher();

  /**
   * Generates BLOCKS_AT_ONCE 128-bit (16-byte) blocks. Copies them to currentBlock.
   * @throws IllegalStateException If there is a problem with the cipher that generates the
   *     random data.
   */
  protected void nextBlock() {
    int blocks = getBlocksAtOnce();
    for (int i = 0; i < blocks; i++) {
      Byte16ArrayArithmetic.addInto(counter, Byte16ArrayArithmetic.ONE);
      System.arraycopy(counter, 0, counterInput, i * AesCounterRandom.COUNTER_SIZE_BYTES, AesCounterRandom.COUNTER_SIZE_BYTES);
    }
    try {
      cipher.doFinal(counterInput, 0, getBytesAtOnce(), currentBlock);
    } catch (final GeneralSecurityException ex) {
      // Should never happen.  If initialisation succeeds without exceptions
      // we should be able to proceed indefinitely without exceptions.
      throw new IllegalStateException("Failed creating next random block.", ex);
    }
  }

  @Override protected final int next(final int bits) {
    lock.lock();
    int result;
    try {
      if ((getBytesAtOnce() - index) < 4) {
        nextBlock();
        index = 0;
      }
      result = BinaryUtils.convertBytesToInt(currentBlock, index);
      index += 4;
    } finally {
      lock.unlock();
    }
    return result >>> (32 - bits);
  }

  /**
   * {@inheritDoc} If the seed is not of the maximum length, it is combined with the existing seed
   * using SHA-256.
   */
  @Override public void setSeed(final byte[] seed) {
    checkNotTooLong(seed);
    final byte[] key;
    if (seed.length == getMaxTotalSeedLengthBytes()) {
      key = seed.clone();
    } else {
      lock.lock();
      boolean weAreSeeded;
      try {
        weAreSeeded = seeded;
      } finally {
        lock.unlock();
      }
      if (weAreSeeded) {
        // Extend the key
        final byte[] newSeed = new byte[this.seed.length + seed.length];
        System.arraycopy(this.seed, 0, newSeed, 0, this.seed.length);
        System.arraycopy(seed, 0, newSeed, this.seed.length, seed.length);
        final int keyLength = getKeyLength(newSeed);
        if (newSeed.length > keyLength) {
          final byte[] digest = hash.digest(newSeed);
          key = digest.length > keyLength ? Arrays.copyOf(digest, keyLength) : digest;
        } else {
          key = newSeed;
        }
      } else {
        key = seed.clone();
      }
    }
    lock.lock();
    try {
      setSeedInternal(key);
      entropyBits.addAndGet(8L * (seed.length - key.length));
    } finally {
      lock.unlock();
    }
  }

  private void checkNotTooLong(final byte[] seed) {
    int maxLength = getMaxTotalSeedLengthBytes();
    if (seed.length > maxLength) {
      throw new IllegalArgumentException(String.format(
          "Seed length is %d bytes; maximum is %d bytes", seed.length, maxLength));
    }
  }

  /**
   * Combines the given seed with the existing seed using SHA-256.
   */
  public void setSeed(final long seed) {
    if (superConstructorFinished) {
      final byte[] seedBytes = BinaryUtils.convertLongToBytes(seed);
      setSeed(seedBytes);
    }
  }

  @Override protected void setSeedInternal(final byte[] seed) {
    checkNotTooLong(seed);
    if (seed.length < 16) {
      throw new IllegalArgumentException(String.format(
          "Seed length is %d bytes; need at least 16 bytes", seed.length));
    }
    super.setSeedInternal(seed);
    // determine how much of seed can go to key
    final int keyLength = getKeyLength(seed);
    final byte[] key = (seed.length == keyLength) ? seed : Arrays.copyOfRange(seed, 0, keyLength);
    // rest goes to counter
    final int bytesToCopyToCounter = seed.length - keyLength;
    if (bytesToCopyToCounter > 0) {
      System.arraycopy(seed, keyLength, counter, 0, bytesToCopyToCounter);
    }
    System.arraycopy(Byte16ArrayArithmetic.ZERO, 0, counter, bytesToCopyToCounter,
        getCounterSizeBytes() - bytesToCopyToCounter);
    try {
      setKey(key);
    } catch (final InvalidKeyException e) {
      throw new InternalError("Invalid key: " + Arrays.toString(key), e);
    }
    if (currentBlock != null) {
      index = getBytesAtOnce();
    } // else it'll be initialized in ctor
    seeded = true;
  }

  protected abstract void setKey(byte[] key) throws InvalidKeyException;
}