/*
 * Copyright 2021 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.nullness;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link NullablePrimitiveArray}Test */
@RunWith(JUnit4.class)
public class NullablePrimitiveArrayTest {

  private final BugCheckerRefactoringTestHelper testHelper =
      BugCheckerRefactoringTestHelper.newInstance(NullablePrimitiveArray.class, getClass());

  @Test
  public void typeAnnotation() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;
            import org.checkerframework.checker.nullness.qual.NonNull;

            abstract class Test {
              @Nullable
              abstract byte[] f();

              abstract @Nullable byte[] g();

              abstract void h(@Nullable byte[] x);

              abstract void i(@Nullable byte @Nullable [] x);

              abstract void j(@Nullable byte... x);

              abstract void k(@Nullable byte[][][] x);

              abstract void l(@NonNull byte[] x);
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;
            import org.checkerframework.checker.nullness.qual.NonNull;

            abstract class Test {
              abstract byte @Nullable [] f();

              abstract byte @Nullable [] g();

              abstract void h(byte @Nullable [] x);

              abstract void i(byte @Nullable [] x);

              abstract void j(byte @Nullable ... x);

              abstract void k(byte @Nullable [][][] x);

              abstract void l(byte @NonNull [] x);
            }
            """)
        .doTest();
  }

  @Test
  public void typeAnnotationWithOtherAnnotation() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;

            abstract class Test {
              @SuppressWarnings("SomeOtherChecker") // unrelated annotation
              @Nullable
              abstract byte[] f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;

            abstract class Test {
              @SuppressWarnings("SomeOtherChecker") // unrelated annotation
              abstract byte @Nullable [] f();
            }
            """)
        .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
  }

  @Test
  public void typeAnnotationWithOtherNullnessAnnotationDoesNotSuggestDoubleAnnotation() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import javax.annotation.CheckForNull;
            import org.checkerframework.checker.nullness.qual.Nullable;

            abstract class Test {
              @CheckForNull
              @Nullable
              abstract byte[] f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import javax.annotation.CheckForNull;
            import org.checkerframework.checker.nullness.qual.Nullable;

            abstract class Test {
              @CheckForNull
              abstract byte[] f();
            }
            """)
        .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
  }

  @Test
  public void negative() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import javax.annotation.Nullable;

            abstract class Test {
              @Nullable
              abstract Object[] f();

              abstract @Nullable Object[] g();

              abstract void h(@Nullable Object[] x);
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void alreadyAnnotatedForNullness() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;
            import org.checkerframework.checker.nullness.qual.NonNull;

            abstract class Test {
              abstract void f(@Nullable int @NonNull [] x);
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import org.checkerframework.checker.nullness.qual.Nullable;
            import org.checkerframework.checker.nullness.qual.NonNull;

            abstract class Test {
              abstract void f(int @NonNull [] x);
            }
            """)
        .doTest();
  }

  @Test
  public void declarationAnnotation() {
    testHelper
        .addInputLines(
            "Test.java",
            """
            import javax.annotation.Nullable;

            abstract class Test {
              @Nullable
              abstract byte[] f();

              abstract @Nullable byte[] g();

              abstract void h(@Nullable byte[] x);
            }
            """)
        .expectUnchanged()
        .doTest();
  }
}
