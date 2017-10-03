package io.github.pr0methean.betterrandom.util.spliterator;

import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.SIZED;

import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An unordered, concurrent spliterator (possibly unboxed) that invokes a supplier (possibly
 * unboxed) to get its values and has a preset size.
 *
 * @param <TSupplier> The supplier type.
 * @param <TConsumer> The consumer type that {@link #tryAdvance(Object)} receives.
 * @param <TSplitInto> The return type of {@link #trySplit()}.
 *
 * @author Chris Hennick
 */
public abstract class AbstractSupplierSpliterator<TSupplier, TConsumer, TSplitInto> {

  protected final TSupplier supplier;
  private final AtomicLong remaining;
  private final AtomicLong splitsRemaining;
  private final boolean sized;

  /**
   * Create an instance.
   *
   * @param size The maximum number of values to output.
   * @param supplier The supplier to wrap.
   */
  public AbstractSupplierSpliterator(final long size, final TSupplier supplier) {
    this(new AtomicLong(size), new AtomicLong(Long.SIZE - Long.numberOfLeadingZeros(size)),
        supplier, true);
  }

  /**
   * Used to share the AtomicLongs between partitions.
   *
   * @param remaining An {@link AtomicLong} (shared between splits) that stores how many more
   *     items will be output by {@link #tryAdvance(Object)}.
   * @param splitsRemaining An {@link AtomicLong} (shared between splits) that stores how many
   *     more times the {@link #trySplit()} command will return non-null.
   * @param supplier The supplier to wrap.
   * @param sized Should be true if this is the root spliterator, and false otherwise (since the
   *     root spliterator stores the total size, and concurrent splits may compete to exhaust it).
   */
  protected AbstractSupplierSpliterator(final AtomicLong remaining,
      final AtomicLong splitsRemaining,
      final TSupplier supplier, boolean sized) {
    this.remaining = remaining;
    this.splitsRemaining = splitsRemaining;
    this.supplier = supplier;
    this.sized = sized;
  }

  @SuppressWarnings("override.return.invalid") // actually is nullable in the interface
  /**
   * @see Spliterator#trySplit()
   * @return a descendant spliterator, or null if this spliterator refuses to be split any further.
   */
  public @Nullable TSplitInto trySplit() {
    //LOG.logStackTrace(Level.INFO, Thread.currentThread().getStackTrace());
    return ((splitsRemaining.getAndDecrement() <= 0) || (remaining.get() <= 0))
        ? null
        : internalSplit(remaining, splitsRemaining);
  }

  /**
   * Should wrap a constructor that calls {@link #AbstractSupplierSpliterator(AtomicLong,
   * AtomicLong, Object, boolean)}.
   * @param remaining An {@link AtomicLong} (shared between splits) that stores how many more
   *     items will be output by {@link #tryAdvance(Object)}.
   * @param splitsRemaining An {@link AtomicLong} (shared between splits) that stores how many
   *     more times the {@link #trySplit()} command will return non-null.
   * @return a descendant spliterator.
   */
  protected abstract TSplitInto internalSplit(AtomicLong remaining, AtomicLong splitsRemaining);

  /**
   * @see Spliterator#estimateSize()
   * @return the total number of items remaining in the root spliterator, and thus an upper bound
   * on the number available to any one descendant.
   */
  public long estimateSize() {
    return remaining.get();
  }

  /**
   * @see Spliterator#characteristics()
   * @return {@code IMMUTABLE | NONNULL | SIZED} in the root spliterator, and
   * {@code IMMUTABLE | NONNULL} in descendants.
   */
  public int characteristics() {
    return IMMUTABLE | NONNULL | (sized ? SIZED : 0);
  }

  /**
   * @see Spliterator#tryAdvance(Consumer)
   * @param action the consumer that will receive the next output value if there is one.
   * @return true if an output was produced and consumed.
   */
  public boolean tryAdvance(final TConsumer action) {
    //LOG.logStackTrace(Level.INFO, Thread.currentThread().getStackTrace());
    if (remaining.decrementAndGet() >= 0) {
      internalSupplyAndAccept(action);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Should be {@code action.accept(supplier.get())} or an equivalent.
   * @param action the consumer that will receive the next output value if there is one.
   */
  protected abstract void internalSupplyAndAccept(TConsumer action);
}
