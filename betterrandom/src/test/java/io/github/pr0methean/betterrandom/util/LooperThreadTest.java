package io.github.pr0methean.betterrandom.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableMap;
import io.github.pr0methean.betterrandom.MockException;
import io.github.pr0methean.betterrandom.TestUtils;
import io.github.pr0methean.betterrandom.TestingDeficiency;
import io.github.pr0methean.betterrandom.prng.RandomTestUtils;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.lang.Thread.State;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SuppressWarnings("ClassLoaderInstantiation")
public class LooperThreadTest {

  private static final String THREAD_NAME = "LooperThread for serialization test";
  private static final String GROUP_NAME = SerializableThreadGroup.class.getSimpleName();
  private static final long STACK_SIZE = 1_234_567;
  private static final Field THREAD_STACK_SIZE;
  private static final Field THREAD_TARGET;
  private static final AtomicLong iterationsRun = new AtomicLong();
  private static final AtomicBoolean shouldThrow = new AtomicBoolean(false);
  private static final AtomicBoolean exceptionHandlerRun = new AtomicBoolean(false);
  private static final Runnable TARGET = (Serializable & Runnable) () -> {
    if (shouldThrow.get()) {
      throw new MockException();
    }
    iterationsRun.addAndGet(1);
  };

  static {
    try {
      THREAD_STACK_SIZE = Thread.class.getDeclaredField("stackSize");
      THREAD_STACK_SIZE.setAccessible(true);
      THREAD_TARGET = Thread.class.getDeclaredField("target");
      THREAD_TARGET.setAccessible(true);
    } catch (final NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testAllPublicConstructors()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    TestUtils.testAllPublicConstructors(LooperThread.class, ImmutableMap.of(
        ThreadGroup.class, new SerializableThreadGroup(),
        Runnable.class, TARGET,
        String.class, "Test LooperThread",
        long.class, STACK_SIZE
    ), thread -> CloneViaSerialization.clone(thread).start());
  }

  @BeforeTest
  public void setUp() {
    iterationsRun.set(0);
    shouldThrow.set(false);
    exceptionHandlerRun.set(false);
  }

  @SuppressWarnings("CallToThreadRun")
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testMustOverrideIterate() {
    new LooperThread().run();
  }

  @Test
  public void testSerializable_notStarted() {
    final LooperThread thread = new SkeletonLooperThread();
    final LooperThread copy = CloneViaSerialization.clone(thread);
    assertNotSame(thread, copy);
    assertEquals(State.NEW, copy.getState());
  }

  @Test
  public void testSerializable_alreadyExited() {
    final LooperThread thread = new SkeletonLooperThread();
    thread.start();
    try {
      thread.join();
    } catch (final InterruptedException expected) {
    }
    final LooperThread copy = CloneViaSerialization.clone(thread);
    assertNotSame(thread, copy);
    assertEquals(State.TERMINATED, copy.getState());
    try {
      copy.start();
      fail("Shouldn't be able to start a thread that's already terminated");
    } catch (final IllegalThreadStateException expected) {
    }
  }

  @SuppressWarnings("argument.type.incompatible")
  @Test
  public void testSerializable_nonSerializableState()
      throws InterruptedException, MalformedURLException, IllegalAccessException {
    final LooperThread thread = new SkeletonLooperThread(() -> {
    });
    thread.setContextClassLoader(new MockClassLoader());
    thread.setUncaughtExceptionHandler((thread_, throwable) -> exceptionHandlerRun.set(true));
    final LooperThread copy = CloneViaSerialization.clone(thread);
    assertNotSame(copy, thread);
    assertSame(copy.getContextClassLoader(), Thread.currentThread().getContextClassLoader());
    shouldThrow.set(true);
    copy.start();
    copy.join();
    assertFalse(exceptionHandlerRun.get());
  }

  @SuppressWarnings("dereference.of.nullable")
  @Test
  public void testSerializable_serializableState()
      throws InterruptedException, IllegalAccessException {
    final LooperThread thread = new LooperThread(new SerializableThreadGroup(), TARGET, THREAD_NAME,
        STACK_SIZE);
    thread.setContextClassLoader(new SerializableClassLoader());
    thread.setUncaughtExceptionHandler(new SerializableUncaughtExceptionHandler());
    thread.setPriority(2);
    thread.setDaemon(true);
    final LooperThread copy = CloneViaSerialization.clone(thread);
    assertNotSame(copy, thread);
    assertTrue(copy.getContextClassLoader() instanceof SerializableClassLoader);
    assertTrue(copy.getUncaughtExceptionHandler() instanceof SerializableUncaughtExceptionHandler);
    assertEquals(2, copy.getPriority());
    assertTrue(copy.isDaemon());
    assertEquals(THREAD_NAME, copy.getName());
    assertTrue(copy.getThreadGroup() instanceof SerializableThreadGroup);
    assertEquals(GROUP_NAME, copy.getThreadGroup().getName());
    assertEquals(STACK_SIZE, THREAD_STACK_SIZE.get(copy));
    shouldThrow.set(true);
    copy.start();
    copy.join();
    assertTrue(exceptionHandlerRun.get());
  }

  @TestingDeficiency
  @Test(enabled = false)
  public void testDefaultUncaughtExceptionHandler() throws InterruptedException {
    final AtomicBoolean defaultHandlerCalled = new AtomicBoolean(false);
    final UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
    try {
      Thread.setDefaultUncaughtExceptionHandler(
          (thread, throwable) -> defaultHandlerCalled.set(true));
      final FailingLooperThread failingThread = new FailingLooperThread();
      failingThread.start();
      failingThread.join();
      Thread.sleep(1000);
      assertTrue(defaultHandlerCalled.get());
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(oldHandler);
    }
  }

  /**
   * Intermediate used to give {@link SerializableThreadGroup} a parameterless super constructor for
   * deserialization purposes.
   */
  private static class SerializableThreadGroupSurrogate extends ThreadGroup {

    public SerializableThreadGroupSurrogate() {
      super(GROUP_NAME);
    }
  }

  private static class SerializableThreadGroup extends SerializableThreadGroupSurrogate
      implements Serializable {

    private static final long serialVersionUID = 4660069266898564395L;

    public SerializableThreadGroup() {
    }
  }

  private static class SkeletonLooperThread extends LooperThread {

    private static final long serialVersionUID = -6863326140536988360L;

    public SkeletonLooperThread() {
    }

    public SkeletonLooperThread(final Runnable target) {
      super(target);
    }

    public SkeletonLooperThread(final ThreadGroup group, final Runnable target, final String name,
        final long stackSize) {
      super(group, target, name, stackSize);
    }

    @RequiresNonNull({"group", "target", "name"})
    @Override
    protected LooperThread readResolveConstructorWrapper() throws InvalidObjectException {
      return new SkeletonLooperThread(group, target, name, stackSize);
    }

    @Override
    public boolean iterate() throws InterruptedException {
      TARGET.run();
      return iterationsRun.get() < 100;
    }
  }

  private static class FailingLooperThread extends LooperThread {

    private static final long serialVersionUID = -1882343225722025757L;

    public FailingLooperThread() {
      super("FailingLooperThread");
    }

    @Override
    public boolean iterate() {
      throw new MockException();
    }
  }

  private static class SerializableUncaughtExceptionHandler implements UncaughtExceptionHandler,
      Serializable {

    private static final long serialVersionUID = -4761296548510628117L;

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
      exceptionHandlerRun.set(true);
    }
  }

  @SuppressWarnings("CustomClassloader")
  private static class MockClassLoader extends ClassLoader {

  }

  @SuppressWarnings("CustomClassloader")
  private static class SerializableClassLoader extends ClassLoader implements Serializable {

    private static final long serialVersionUID = -5540517522704769624L;
  }
}
