package io.github.pr0methean.betterrandom;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Utility methods used only for testing, but by both {@link io.github.pr0methean.betterrandom.prng}
 * and {@link io.github.pr0methean.betterrandom.seed}.
 */
public enum TestUtils {
  ;

  /**
   * Reflectively calls all public constructors, or all public and protected constructors, of the
   * given class with the given parameters. Passes each constructed instance to a consumer.
   * @param <T> {@code clazz} as a type.
   * @param clazz The class whose constructors are to be tested.
   * @param includeProtected Whether to test protected constructors
   * @param params A map of parameter types to values.
   * @param test The consumer to pass the instances to.
   */
  @SuppressWarnings("ObjectAllocationInLoop") public static <T> void testConstructors(
      final Class<? extends T> clazz, final boolean includeProtected,
      final Map<Class<?>, Object> params, final Consumer<? super T> test) {
    for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
      final int modifiers = constructor.getModifiers();
      if (Modifier.isPublic(modifiers) || (includeProtected && Modifier.isProtected(modifiers))) {
        constructor.setAccessible(true);
        final int nParams = constructor.getParameterCount();
        final Class<?>[] parameterTypes = constructor.getParameterTypes();
        final Object[] constructorParams = new Object[nParams];
        try {
          for (int i = 0; i < nParams; i++) {
            constructorParams[i] = params.get(parameterTypes[i]);
          }
          test.accept((T) constructor.newInstance(constructorParams));
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | IllegalArgumentException e) {
          throw new AssertionError(String
              .format("Failed to call%n%s%nwith parameters%n%s", constructor.toGenericString(),
                  Arrays.toString(constructorParams)), e);
        }
      }
    }
  }

  /**
   * Appveyor and the OSX environment on Travis-CI don't currently use enough IP addresses to get
   * heavy random.org usage allowed, so tests that are sufficiently demanding of random.org won't
   * run on those environments.
   * @return true if we're not running on Appveyor or a Travis-CI OSX instance, false if we are.
   */
  @SuppressWarnings("CallToSystemGetenv") @TestingDeficiency
  public static boolean canRunRandomDotOrgLargeTest() {
    return !isAppveyor() && !("osx".equals(System.getenv("TRAVIS_OS_NAME")));
  }

  /**
   * Appveyor doesn't seem to be allowed any random.org usage at all, even with a valid API key.
   * @return true if we're running on Appveyor, false otherwise
   */
  @SuppressWarnings("CallToSystemGetenv") @TestingDeficiency public static boolean isAppveyor() {
    return System.getenv("APPVEYOR") != null;
  }

  public static void assertLessOrEqual(final long actual, final long expected) {
    if (actual > expected) {
      throw new AssertionError(
          String.format("Expected no more than %d but found %d", expected, actual));
    }
  }

  public static void assertLessOrEqual(final double actual, final double expected) {
    if (actual > expected) {
      throw new AssertionError(
          String.format("Expected no more than %f but found %f", expected, actual));
    }
  }

  public static void assertGreaterOrEqual(final long actual, final long expected) {
    if (actual < expected) {
      throw new AssertionError(
          String.format("Expected at least %d but found %d", expected, actual));
    }
  }

  public static void assertGreaterOrEqual(final double actual, final double expected) {
    if (actual < expected) {
      throw new AssertionError(
          String.format("Expected at least %f but found %f", expected, actual));
    }
  }

  public static void assertLess(final double actual, final double expected) {
    if (actual >= expected) {
      throw new AssertionError(
          String.format("Expected less than %f but found %f", expected, actual));
    }
  }
}
