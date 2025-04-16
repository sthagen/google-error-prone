/*
 * Copyright 2023 The Error Prone Authors.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.BugCheckerRefactoringTestHelper.FixChoosers;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class YodaConditionTest {
  private final BugCheckerRefactoringTestHelper refactoring =
      BugCheckerRefactoringTestHelper.newInstance(YodaCondition.class, getClass());

  private final CompilationTestHelper testHelper =
      CompilationTestHelper.newInstance(YodaCondition.class, getClass());

  @Test
  public void primitive() {
    refactoring
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(int a) {
                return 4 == a;
              }

              boolean notYoda(int a) {
                return a == 4;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(int a) {
                return a == 4;
              }

              boolean notYoda(int a) {
                return a == 4;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparison() {
    testHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              boolean yoda(int a) {
                // BUG: Diagnostic contains: a < 4
                return 4 > a;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparison_noFindingWithAdjacentComparison() {
    testHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              boolean test(int a) {
                return 4 < a && a < 7 && true && false;
              }

              boolean test2(int a) {
                return true && false && 4 < a && a < 7;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void boxedBoolean() {
    refactoring
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(Boolean a) {
                return Boolean.TRUE.equals(a);
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.Objects;

            class Test {
              boolean yoda(Boolean a) {
                return Objects.equals(a, Boolean.TRUE);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void boxedVsUnboxedBoolean() {
    refactoring
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(boolean a) {
                return Boolean.TRUE.equals(a);
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            "class Test {",
            "  boolean yoda(boolean a) {",
            // NOTE: this is a broken fix! We could detect this if it turns out to be an issue in
            // practice.
            "    return a.equals(Boolean.TRUE);",
            "  }",
            "}")
        .allowBreakingChanges()
        .doTest();
  }

  @Test
  public void enums() {
    refactoring
        .addInputLines(
            "E.java",
            """
            enum E {
              A,
              B;

              boolean foo(E e) {
                return this == e;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                return E.A == a;
              }

              boolean notYoda(E a) {
                return a == E.A;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                return a == E.A;
              }

              boolean notYoda(E a) {
                return a == E.A;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void nullIntolerantFix() {
    refactoring
        .addInputLines(
            "E.java",
            """
            enum E {
              A,
              B
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                return E.A.equals(a);
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                return a.equals(E.A);
              }
            }
            """)
        .setFixChooser(FixChoosers.SECOND)
        .doTest();
  }

  @Test
  public void nullTolerantFix() {
    refactoring
        .addInputLines(
            "E.java",
            """
            enum E {
              A,
              B
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                return E.A.equals(a);
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.Objects;

            class Test {
              boolean yoda(E a) {
                return Objects.equals(a, E.A);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void provablyNonNull_nullIntolerantFix() {
    refactoring
        .addInputLines(
            "E.java",
            """
            enum E {
              A,
              B
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                if (a != null) {
                  return E.A.equals(a);
                }
                return true;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              boolean yoda(E a) {
                if (a != null) {
                  return a.equals(E.A);
                }
                return true;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void nullableConstant() {
    testHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private static final String CONST = null;

              public static boolean f() {
                return CONST != null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void nullableYodaCondition() {
    refactoring
        .addInputLines(
            "Test.java",
            """
            class Test {
              private static final String CONST = "hello";

              public static boolean f(String foo) {
                return null != foo;
              }

              public static boolean g() {
                return null != CONST;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              private static final String CONST = "hello";

              public static boolean f(String foo) {
                return foo != null;
              }

              public static boolean g() {
                return CONST != null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void unqualified() {
    testHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.base.Objects;

            class Test {
              @Override
              public boolean equals(Object other) {
                return Objects.equal(this, other);
              }

              public boolean foo(Object other) {
                return equals(other);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeSdkInt() {
    testHelper
        .addSourceLines(
            "Build.java",
            """
            package android.os;

            public class Build {
              public static class VERSION {
                public static final int SDK_INT = 0;
              }
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            import android.os.Build;

            class Test {
              public boolean foo(int x) {
                return Build.VERSION.SDK_INT < x;
              }
            }
            """)
        .doTest();
  }
}
