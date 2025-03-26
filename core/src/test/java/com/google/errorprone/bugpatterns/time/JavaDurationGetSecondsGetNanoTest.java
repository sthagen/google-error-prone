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
 * Tests for {@link JavaDurationGetSecondsGetNano}.
 *
 * @author kak@google.com (Kurt Alfred Kluever)
 */
@RunWith(JUnit4.class)
public class JavaDurationGetSecondsGetNanoTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(JavaDurationGetSecondsGetNano.class, getClass());

  @Test
  public void getSecondsWithGetNanos() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import java.time.Duration;

            public class TestCase {
              public static void foo(Duration duration) {
                long seconds = duration.getSeconds();
                int nanos = duration.getNano();
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
            import java.time.Duration;

            public class TestCase {
              public static int foo(Duration duration) {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                return duration.getNano();
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
import java.time.Duration;

public class TestCase {
  public static ImmutableMap<String, Object> foo(Duration duration) {
    return ImmutableMap.of("seconds", duration.getSeconds(), "nanos", duration.getNano());
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

            import java.time.Duration;

            public class TestCase {
              public static void foo(Duration duration) {
                long seconds = duration.getSeconds();
                if (true) {
                  // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                  int nanos = duration.getNano();
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

            import java.time.Duration;

            public class TestCase {
              public static void foo(Duration duration) {
                long seconds = duration.getSeconds();
              }

              public static void bar(Duration duration) {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = duration.getNano();
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

            import java.time.Duration;

            public class TestCase {
              public static void foo(Duration duration) {
                long seconds = duration.getSeconds();
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

            import java.time.Duration;

            public class TestCase {
              public static void foo(Duration duration) {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = duration.getNano();
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

            import java.time.Duration;

            public class TestCase {
              private static final Duration DURATION = Duration.ZERO;
              private static final long seconds = DURATION.getSeconds();

              public static void foo() {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = DURATION.getNano();
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

            import java.time.Duration;

            public class TestCase {
              static {
                long seconds = Duration.ZERO.getSeconds();
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

            import java.time.Duration;

            public class TestCase {
              static {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = Duration.ZERO.getNano();
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

            import java.time.Duration;

            public class TestCase {
              private final long seconds = Duration.ZERO.getSeconds();
              private final int nanos = Duration.ZERO.getNano();
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

            import java.time.Duration;

            public class TestCase {
              // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
              private final int nanos = Duration.ZERO.getNano();
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

            import java.time.Duration;

            public class TestCase {
              private static final Duration DURATION = Duration.ZERO;

              public static void foo() {
                long seconds = DURATION.getSeconds();
                Object obj =
                    new Object() {
                      @Override
                      public String toString() {
                        // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                        return String.valueOf(DURATION.getNano());
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

            import java.time.Duration;

            public class TestCase {
              Duration DURATION = Duration.ZERO;
              long seconds = DURATION.getSeconds();
              Object obj =
                  new Object() {
                    // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                    long nanos = DURATION.getNano();
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

            import java.time.Duration;

            public class TestCase {
              private static final Duration DURATION = Duration.ZERO;

              public static void foo() {
                Runnable r = () -> DURATION.getSeconds();
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = DURATION.getNano();
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

            import java.time.Duration;
            import java.util.function.Supplier;

            public class TestCase {
              private static final Duration DURATION = Duration.ZERO;

              public void foo() {
                doSomething(() -> DURATION.getSeconds());
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                int nanos = DURATION.getNano();
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

            import java.time.Duration;

            public class TestCase {
              private static final Duration DURATION = Duration.ZERO;

              public static void foo() {
                // BUG: Diagnostic contains: JavaDurationGetSecondsGetNano
                Runnable r = () -> DURATION.getNano();
                long seconds = DURATION.getSeconds();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void bothUsedWithinALambda() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
"""
package test;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.function.Supplier;

public class TestCase {
  public static Supplier<ImmutableMap<String, Object>> foo(Duration duration) {
    return () -> ImmutableMap.of("seconds", duration.getSeconds(), "nanos", duration.getNano());
  }
}
""")
        .doTest();
  }
}
