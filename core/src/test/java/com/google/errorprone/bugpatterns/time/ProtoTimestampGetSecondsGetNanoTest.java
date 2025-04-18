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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ProtoTimestampGetSecondsGetNano}.
 *
 * @author kak@google.com (Kurt Alfred Kluever)
 */
@RunWith(JUnit4.class)
@Ignore("b/130667208")
public class ProtoTimestampGetSecondsGetNanoTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ProtoTimestampGetSecondsGetNano.class, getClass());

  @Test
  public void getSecondsWithGetNanos() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static void foo(Timestamp timestamp) {
                long seconds = timestamp.getSeconds();
                int nanos = timestamp.getNanos();
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
            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static int foo(Timestamp timestamp) {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                return timestamp.getNanos();
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
import com.google.protobuf.Timestamp;

public class TestCase {
  public static ImmutableMap<String, Object> foo(Timestamp timestamp) {
    return ImmutableMap.of("seconds", timestamp.getSeconds(), "nanos", timestamp.getNanos());
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static void foo(Timestamp timestamp) {
                long seconds = timestamp.getSeconds();
                if (true) {
                  // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                  int nanos = timestamp.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static void foo(Timestamp timestamp) {
                long seconds = timestamp.getSeconds();
              }

              public static void bar(Timestamp timestamp) {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = timestamp.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static void foo(Timestamp timestamp) {
                long seconds = timestamp.getSeconds();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              public static void foo(Timestamp timestamp) {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = timestamp.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();
              private static final long seconds = TIMESTAMP.getSeconds();

              public static void foo() {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = TIMESTAMP.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              static {
                long seconds = Timestamp.getDefaultInstance().getSeconds();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              static {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = Timestamp.getDefaultInstance().getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              private final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();
              private final long seconds = TIMESTAMP.getSeconds();
              private final int nanos = TIMESTAMP.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
              private final int nanos = Timestamp.getDefaultInstance().getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();

              public static void foo() {
                long seconds = TIMESTAMP.getSeconds();
                Object obj =
                    new Object() {
                      @Override
                      public String toString() {
                        // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                        return String.valueOf(TIMESTAMP.getNanos());
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              Timestamp TIMESTAMP = Timestamp.getDefaultInstance();
              long seconds = TIMESTAMP.getSeconds();
              Object obj =
                  new Object() {
                    // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                    long nanos = TIMESTAMP.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();

              public static void foo() {
                Runnable r = () -> TIMESTAMP.getSeconds();
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = TIMESTAMP.getNanos();
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

            import com.google.protobuf.Timestamp;
            import java.util.function.Supplier;

            public class TestCase {
              private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();

              public void foo() {
                doSomething(() -> TIMESTAMP.getSeconds());
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                int nanos = TIMESTAMP.getNanos();
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

            import com.google.protobuf.Timestamp;

            public class TestCase {
              private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();

              public static void foo() {
                // BUG: Diagnostic contains: ProtoTimestampGetSecondsGetNano
                Runnable r = () -> TIMESTAMP.getNanos();
                long seconds = TIMESTAMP.getSeconds();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getMessageGetSecondsGetNanos() {
    compilationHelper
        .addSourceLines(
            "test/TestCase.java",
            """
            package test;

            import com.google.errorprone.bugpatterns.time.Test.DurationTimestamp;

            public class TestCase {
              public static void foo(DurationTimestamp durationTimestamp) {
                long seconds = durationTimestamp.getTestTimestamp().getSeconds();
                int nanos = durationTimestamp.getTestTimestamp().getNanos();
              }
            }
            """)
        .doTest();
  }
}
