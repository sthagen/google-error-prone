/*
 * Copyright 2015 The Error Prone Authors.
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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author sebastian.h.monte@gmail.com (Sebastian Monte)
 */
@RunWith(JUnit4.class)
public class NullablePrimitiveTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(NullablePrimitive.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "NullablePrimitivePositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import org.jspecify.annotations.Nullable;

            /**
             * @author sebastian.h.monte@gmail.com (Sebastian Monte)
             */
            public class NullablePrimitivePositiveCases {

              // BUG: Diagnostic contains: private  int a
              private @Nullable int a;

              // BUG: Diagnostic contains: ( int a)
              public void method(@Nullable int a) {}

              // BUG: Diagnostic contains: remove
              @Nullable
              public int method() {
                return 0;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "NullablePrimitiveNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import org.jspecify.annotations.Nullable;

            /**
             * @author sebastian.h.monte@gmail.com (Sebastian Monte)
             */
            public class NullablePrimitiveNegativeCases {
              @Nullable Integer a;

              public void method(@Nullable Integer a) {}

              @Nullable
              public Integer method() {
                return Integer.valueOf(0);
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeConstructor() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import javax.annotation.Nullable;

            class Test {
              @Nullable
              public Test() {}
            }
            """)
        .doTest();
  }

  @Test
  public void negativeVoid() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import javax.annotation.Nullable;

            class Test {
              @Nullable
              void f() {}
            }
            """)
        .doTest();
  }

  @Test
  public void positiveArray() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.List;
            import org.checkerframework.checker.nullness.qual.Nullable;

            class Test {
              // BUG: Diagnostic contains:
              List<@Nullable int[]> xs;
            }
            """)
        .doTest();
  }

  // regression test for #418
  @Test
  public void typeParameter() {
    compilationHelper
        .addSourceLines(
            "Nullable.java",
            """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE_USE)
            public @interface Nullable {}
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              // BUG: Diagnostic contains:
              @Nullable int x;

              // BUG: Diagnostic contains:
              @Nullable
              int f() {
                return 42;
              }

              <@Nullable T> int g() {
                return 42;
              }

              int @Nullable [] y;
            }
            """)
        .doTest();
  }

  @Test
  public void positiveNonNull() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.List;
            import org.checkerframework.checker.nullness.qual.NonNull;

            class Test {
              // BUG: Diagnostic contains:
              @NonNull int xs;
            }
            """)
        .doTest();
  }
}
