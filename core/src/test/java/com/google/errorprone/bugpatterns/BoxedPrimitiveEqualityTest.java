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

package com.google.errorprone.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author cushon@google.com (Liam Miller-Cushon)
 */
@RunWith(JUnit4.class)
public class BoxedPrimitiveEqualityTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(BoxedPrimitiveEquality.class, getClass());

  @Test
  public void positive() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              boolean f(Boolean a, Boolean b) {
                // BUG: Diagnostic contains:
                return a == b;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negative_forNumber() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              boolean f(Number a, Number b) {
                return a == b;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negative() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              boolean f(boolean a, boolean b) {
                return a == b;
              }

              boolean g(String a, String b) {
                return a == b;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparedToStaticField_noFinding() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private static final Number SENTINEL = 1L;

              boolean f(Number a) {
                return a == SENTINEL;
              }
            }
            """)
        .doTest();
  }

  // regression test for #415
  @Test
  public void parenthesized() {
    compilationHelper
        .addSourceLines(
            "Test.java",
"""
class Test {
  void f() {
    final Long constValue = Long.valueOf(1000L);
    Long assignedValue;
    // BUG: Diagnostic contains: (!(assignedValue = Long.valueOf(1000L)).equals(constValue))
    boolean retVal = ((assignedValue = Long.valueOf(1000L)) != constValue);
  }
}
""")
        .doTest();
  }

  @Test
  public void atomic() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.concurrent.atomic.AtomicInteger;

            class Test {
              boolean f(AtomicInteger a, AtomicInteger b) {
                return a == b;
              }
            }
            """)
        .doTest();
  }
}
