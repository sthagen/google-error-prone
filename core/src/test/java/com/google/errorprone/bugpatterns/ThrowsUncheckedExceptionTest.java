/*
 * Copyright 2016 The Error Prone Authors.
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

import static com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author yulissa@google.com (Yulissa Arroyo-Paredes)
 */
@RunWith(JUnit4.class)
public final class ThrowsUncheckedExceptionTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ThrowsUncheckedException.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "ThrowsUncheckedExceptionPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.io.IOException;

/**
 * @author yulissa@google.com (Yulissa Arroyo-Paredes)
 */
public class ThrowsUncheckedExceptionPositiveCases {
  // BUG: Diagnostic contains: 'public void doSomething() {'
  public void doSomething() throws IllegalArgumentException {
    throw new IllegalArgumentException("thrown");
  }

  // BUG: Diagnostic contains: 'public void doSomethingElse() {'
  public void doSomethingElse() throws RuntimeException, NullPointerException {
    throw new NullPointerException("thrown");
  }

  // BUG: Diagnostic contains: Unchecked exceptions do not need to be declared
  public void doMore() throws RuntimeException, IOException {
    throw new IllegalArgumentException("thrown");
  }

  // BUG: Diagnostic contains: Unchecked exceptions do not need to be declared
  public void doEverything() throws RuntimeException, IOException, IndexOutOfBoundsException {
    throw new IllegalArgumentException("thrown");
  }

  // BUG: Diagnostic contains: 'public void doBetter() {'
  public void doBetter() throws RuntimeException, AssertionError {
    throw new RuntimeException("thrown");
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "ThrowsUncheckedExceptionNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.io.FileNotFoundException;
            import java.io.IOException;

            /**
             * @author yulissa@google.com (Yulissa Arroyo-Paredes)
             */
            public class ThrowsUncheckedExceptionNegativeCases {
              public void doSomething() {
                throw new IllegalArgumentException("thrown");
              }

              public void doMore() throws IOException {
                throw new FileNotFoundException("thrown");
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void deleteAll() {
    BugCheckerRefactoringTestHelper.newInstance(ThrowsUncheckedException.class, getClass())
        .addInputLines(
            "in/Test.java",
            """
            import java.io.IOError;

            interface Test {
              void f() throws IOError, RuntimeException;
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            import java.io.IOError;

            interface Test {
              void f();
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void deleteLeft() {
    BugCheckerRefactoringTestHelper.newInstance(ThrowsUncheckedException.class, getClass())
        .addInputLines(
            "in/Test.java",
            """
            import java.io.IOError;
            import java.io.IOException;

            interface Test {
              void f() throws IOError, RuntimeException, IOException;
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            import java.io.IOError;
            import java.io.IOException;

            interface Test {
              void f() throws IOException;
            }
            """)
        .doTest();
  }

  @Test
  public void deleteRight() {
    BugCheckerRefactoringTestHelper.newInstance(ThrowsUncheckedException.class, getClass())
        .addInputLines(
            "in/Test.java",
            """
            import java.io.IOError;
            import java.io.IOException;

            interface Test {
              void f() throws IOException, IOError, RuntimeException;
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            import java.io.IOError;
            import java.io.IOException;

            interface Test {
              void f() throws IOException;
            }
            """)
        .doTest();
  }

  @Test
  public void preserveOrder() {
    BugCheckerRefactoringTestHelper.newInstance(ThrowsUncheckedException.class, getClass())
        .addInputLines(
            "in/Test.java",
            """
            import java.io.IOException;

            interface Test {
              void f() throws ReflectiveOperationException, IOException, RuntimeException;
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            import java.io.IOException;

            interface Test {
              void f() throws ReflectiveOperationException, IOException;
            }
            """)
        .doTest();
  }
}
