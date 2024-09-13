/*
 * Copyright 2019 The Error Prone Authors.
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

/** Tests for {@link JavaLocalTimeGetNano}. */
@RunWith(JUnit4.class)
public final class JavaLocalTimeGetNanoTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(JavaLocalTimeGetNano.class, getClass());

  @Test
  public void getSecondWithGetNano() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            package test;

            import java.time.LocalTime;

            public class Test {
              public static void foo(LocalTime localTime) {
                long seconds = localTime.getSecond();
                int nanos = localTime.getNano();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void getNanoWithNoGetSeconds() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.time.LocalTime;

            public class Test {
              public static int foo(LocalTime localTime) {
                // BUG: Diagnostic contains:
                return localTime.getNano();
              }
            }
            """)
        .doTest();
  }
}
