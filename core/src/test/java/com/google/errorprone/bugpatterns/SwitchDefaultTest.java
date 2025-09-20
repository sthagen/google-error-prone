/*
 * Copyright 2017 The Error Prone Authors.
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
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link SwitchDefault}Test */
@RunWith(JUnit4.class)
public class SwitchDefaultTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(SwitchDefault.class, getClass());

  private final BugCheckerRefactoringTestHelper testHelper =
      BugCheckerRefactoringTestHelper.newInstance(SwitchDefault.class, getClass());

  @Test
  public void refactoring_groupAndCase() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  default:
                  case 0:
                    return;
                  case 1:
                    return;
                }
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  case 1:
                    return;
                  case 0:
                  default:
                    return;
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void refactoring_case() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  case 2:
                    return;
                  case 1:
                  case 0:
                  default:
                    return;
                }
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  case 2:
                    return;
                  case 1:
                  case 0:
                  default:
                    return;
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void refactoring_group() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  case 0:
                  default:
                    return;
                  case 1:
                    return;
                }
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              void f(int i) {
                switch (i) {
                  case 1:
                    return;
                  case 0:
                  default:
                    return;
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void refactoring_fallthrough() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  default:
                    return false;
                  case 0:
                  case 1:
                    System.err.println();
                }
                return true;
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  case 0:
                  case 1:
                    System.err.println();
                    break;
                  default:
                    return false;
                }
                return true;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void refactoring_fallthroughEmpty() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  default:
                    return false;
                  case 0:
                  case 1:
                }
                return true;
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  case 0:
                  case 1:
                    break;
                  default:
                    return false;
                }
                return true;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void refactoring_outOfOrder() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  case 0:
                    return true;
                  default: // fall through
                  case 1: // fall through
                }
                return false;
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              boolean f(int i) {
                switch (i) {
                  case 0:
                    return true;
                  case 1: // fall through
                  default: // fall through
                }
                return false;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void newNotation_validDefault() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public static void nothing1() {}

              public static void nothing2() {}

              public static void nothing3() {}

              public static void switchDefaultCrash(int i) {
                switch (i) {
                  case 0 -> nothing1();
                  case 1 -> nothing2();
                  default -> nothing3();
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void newNotation_changeOrder() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            class Test {
              public static void nothing1() {}

              public static void nothing2() {}

              public static void nothing3() {}

              public static void switchDefaultCrash(int i) {
                switch (i) {
                  default -> nothing3();
                  case 0 -> nothing1();
                  case 1 -> nothing2();
                }
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              public static void nothing1() {}

              public static void nothing2() {}

              public static void nothing3() {}

              public static void switchDefaultCrash(int i) {
                switch (i) {
                  case 0 -> nothing1();
                  case 1 -> nothing2();
                  default -> nothing3();
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void arrowSwitch_noDefault() {
    compilationHelper
        .addSourceLines(
            "Foo.java",
            """
            sealed interface Foo {
              final class Bar implements Foo {}

              final class Baz implements Foo {}
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void f(Foo i) {
                switch (i) {
                  case Foo.Bar bar -> {}
                  case Foo.Baz baz -> {}
                }
              }
            }
            """)
        .doTest();
  }
}
