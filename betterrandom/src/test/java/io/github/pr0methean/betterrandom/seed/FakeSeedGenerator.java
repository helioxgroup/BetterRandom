package io.github.pr0methean.betterrandom.seed;

public class FakeSeedGenerator implements SeedGenerator {

  private static final long serialVersionUID = 2310664903337315190L;

  @Override
  public byte[] generateSeed(final int length) throws SeedException {
    return new byte[length];
  }
}
