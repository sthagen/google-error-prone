/*
 * Copyright 2018 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.errorprone.bugpatterns.time;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link JavaInstantGetSecondsGetNano}.
 *
 * @author kak@google.com (Kurt Alfred Kluever)
 */
@RunWith(JUnit4.class)
public class JavaInstantGetSecondsGetNanoTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(JavaInstantGetSecondsGetNano.class, getClass());

  @Test
  public void getSecondsWithGetNanos() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              public static void foo(Instant instant) {
                long seconds = instant.getEpochSecond();
                int nanos = instant.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsWithGetNanosInReturnType() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import com.google.common.collect.ImmutableMap;
            import java.time.Instant;

            public class TestCase {
              public static int foo(Instant instant) {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                return instant.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsWithGetNanosInReturnType2() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
"""
package test;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;

public class TestCase {
  public static ImmutableMap<String, Object> foo(Instant instant) {
    return ImmutableMap.of("seconds", instant.getEpochSecond(), "nanos", instant.getNano());
  }
}
""")
        .doTest();
  }

  @Test
  public void getSecondsWithGetNanosDifferentScope() {
    // Ideally we would also catch cases like this, but it requires scanning "too much" of the class
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              public static void foo(Instant instant) {
                long seconds = instant.getEpochSecond();
                if (true) {
                  // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                  int nanos = instant.getNano();
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsWithGetNanosInDifferentMethods() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              public static void foo(Instant instant) {
                long seconds = instant.getEpochSecond();
              }

              public static void bar(Instant instant) {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = instant.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsOnly() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              public static void foo(Instant instant) {
                long seconds = instant.getEpochSecond();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoOnly() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              public static void foo(Instant instant) {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = instant.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoInMethodGetSecondsInClassVariable() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              private static final Instant INSTANT = Instant.EPOCH;
              private static final long seconds = INSTANT.getEpochSecond();

              public static void foo() {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = INSTANT.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsOnlyInStaticBlock() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              static {
                long seconds = Instant.EPOCH.getEpochSecond();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoOnlyInStaticBlock() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              static {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = Instant.EPOCH.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsOnlyInClassBlock() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              private final long seconds = Instant.EPOCH.getEpochSecond();
              private final int nanos = Instant.EPOCH.getNano();
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoOnlyInClassBlock() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
              private final int nanos = Instant.EPOCH.getNano();
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoInInnerClassGetSecondsInMethod() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              private static final Instant INSTANT = Instant.EPOCH;

              public static void foo() {
                long seconds = INSTANT.getEpochSecond();
                Object obj =
                    new Object() {
                      @Override
                      public String toString() {
                        // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                        return String.valueOf(INSTANT.getNano());
                      }
                    };
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoInInnerClassGetSecondsInClassVariable() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              Instant INSTANT = Instant.EPOCH;
              long seconds = INSTANT.getEpochSecond();
              Object obj =
                  new Object() {
                    // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                    long nanos = INSTANT.getNano();
                  };
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoInMethodGetSecondsInLambda() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              private static final Instant INSTANT = Instant.EPOCH;

              public static void foo() {
                Runnable r = () -> INSTANT.getEpochSecond();
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = INSTANT.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getSecondsInLambda() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;
            import java.util.function.Supplier;

            public class TestCase {
              private static final Instant INSTANT = Instant.EPOCH;

              public void foo() {
                doSomething(() -> INSTANT.getEpochSecond());
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                int nanos = INSTANT.getNano();
              }

              public void doSomething(Supplier<Long> supplier) {}
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoInLambda() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Instant;

            public class TestCase {
              private static final Instant INSTANT = Instant.EPOCH;

              public static void foo() {
                // BUG: Diagnostic contains: JavaInstantGetSecondsGetNano
                Runnable r = () -> INSTANT.getNano();
                long seconds = INSTANT.getEpochSecond();
              }
            }
            """)
        .doTest();
  }
}
