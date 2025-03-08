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

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link AnnotationPosition} bugpattern.
 *
 * @author ghm@google.com (Graeme Morgan)
 */
@RunWith(JUnit4.class)
public final class AnnotationPositionTest {

  private final BugCheckerRefactoringTestHelper refactoringHelper =
      BugCheckerRefactoringTestHelper.newInstance(AnnotationPosition.class, getClass())
          .addInputLines(
              "TypeUse.java",
              """
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target({ElementType.TYPE_USE})
              @interface TypeUse {
                String value() default "";
              }
              """)
          .expectUnchanged()
          .addInputLines(
              "EitherUse.java",
              """
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.TYPE})
              @interface EitherUse {
                String value() default "";
              }
              """)
          .expectUnchanged()
          .addInputLines(
              "NonTypeUse.java", //
              "@interface NonTypeUse {}")
          .expectUnchanged();

  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(AnnotationPosition.class, getClass())
          .addSourceLines(
              "TypeUse.java",
              """
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.TYPE})
              @interface TypeUse {
                String value() default "";
              }
              """)
          .addSourceLines(
              "NonTypeUse.java", //
              "@interface NonTypeUse {}")
          .addSourceLines(
              "EitherUse.java",
              """
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.TYPE})
              @interface EitherUse {
                String value() default "";
              }
              """);

  @Test
  public void nonTypeAnnotation() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              public @Override boolean equals(Object o);
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              @Override public boolean equals(Object o);
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedJavadoc() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              @NonTypeUse
              /** Javadoc! */
              public void foo();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc! */
              @NonTypeUse
              public void foo();
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedJavadoc_treeAlreadyHasJavadoc_noSuggestion() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              /** Actually Javadoc. */
              @NonTypeUse
              /** Javadoc! */
              public void foo();
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedJavadoc_withComment() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              @NonTypeUse
              /** Javadoc! */
              // TODO: fix
              public void foo();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc! */
              @NonTypeUse
              // TODO: fix
              public void foo();
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void negatives() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc */
              @NonTypeUse
              public boolean foo();

              @NonTypeUse
              public boolean bar();

              public @EitherUse boolean baz();

              /** Javadoc */
              @NonTypeUse
              // comment
              public boolean quux();
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void negative_parameter() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              public boolean foo(final @NonTypeUse String s);
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              public boolean foo(@NonTypeUse final String s);
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void typeAnnotation() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc */
              public @NonTypeUse @EitherUse String foo();

              /** Javadoc */
              public @EitherUse @NonTypeUse String bar();

              public @EitherUse
              /** Javadoc */
              @NonTypeUse String baz();

              public @EitherUse static @NonTypeUse int quux() {
                return 1;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc */
              @NonTypeUse public @EitherUse String foo();
              /** Javadoc */
              @NonTypeUse public @EitherUse String bar();
              /** Javadoc */
              @NonTypeUse public @EitherUse String baz();
              @NonTypeUse public static @EitherUse int quux() { return 1; }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void variables() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              public @EitherUse static
              /** Javadoc */
              @NonTypeUse int foo = 1;
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              /** Javadoc */
              @NonTypeUse public static @EitherUse int foo = 1;
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void classes() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            public @NonTypeUse interface Test {}
            """)
        .addOutputLines(
            "Test.java",
            """
            @NonTypeUse
            public interface Test {}
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void class_typeUseBeforeModifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java", //
            "public @EitherUse interface Test {}")
        .addOutputLines(
            "Test.java",
            """
            @EitherUse
            public interface Test {}
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void class_intermingledJavadoc() {
    refactoringHelper
        .addInputLines(
            "Test.java", //
            "@NonTypeUse public /** Javadoc */ final class Test {}")
        .addOutputLines(
            "Test.java",
            """
            /** Javadoc */
            @NonTypeUse public final class Test {}
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void betweenModifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              public @EitherUse static @NonTypeUse int foo() {
                return 1;
              }

              public @EitherUse @NonTypeUse static int bar() {
                return 1;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface Test {
              @NonTypeUse public static @EitherUse int foo() { return 1; }
              @NonTypeUse public static @EitherUse int bar() { return 1; }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void betweenModifiersWithValue() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            class Test {
              public final @EitherUse("foo") int foo(final int a) {
                return 1;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              public final @EitherUse("foo") int foo(final int a) { return 1; }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedComments() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
interface Test {
  public @EitherUse
  /** Javadoc */
  @NonTypeUse String baz();

  /* a */ public /* b */ @EitherUse /* c */ static /* d */ @NonTypeUse /* e */ int quux() {
    return 1;
  }
}
""")
        .addOutputLines(
            "Test.java",
            """
interface Test {
  /** Javadoc */
  @NonTypeUse public @EitherUse String baz();
  /* a */ @NonTypeUse public /* b */ /* c */ static @EitherUse /* d */ /* e */ int quux() { return 1; }
}
""")
        .doTest(TEXT_MATCH);
  }

  @Test
  public void messages() {
    helper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains: @Override is not a TYPE_USE annotation
              public @Override boolean equals(Object o);

              // BUG: Diagnostic contains: @Override, @NonTypeUse are not TYPE_USE annotations
              public @Override @NonTypeUse int hashCode();

              @NonTypeUse
              /** Javadoc */
              // BUG: Diagnostic contains: Javadocs should appear before any modifiers
              public boolean bar();
            }
            """)
        .doTest();
  }

  @Test
  public void diagnostic() {
    helper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains: is a TYPE_USE
              public @EitherUse static int foo = 1;
            }
            """)
        .doTest();
  }

  // TODO(b/168625474): 'sealed' doesn't have a TokenKind
  @Test
  public void sealedInterface() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            /** Javadoc! */
            sealed @Deprecated interface Test {
              final class A implements Test {}
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            /** Javadoc! */
            sealed @Deprecated interface Test {
              final class A implements Test {}
            }
            """)
        .setArgs("--enable-preview", "--release", Integer.toString(Runtime.version().feature()))
        .doTest(TEXT_MATCH);
  }

  @Test
  public void typeArgument_annotationOfEitherUse_canRemainBefore() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface T {
              @EitherUse
              <T> T f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface T {
              @EitherUse <T> T f();
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void typeArgument_typeUseAnnotation_movesAfter() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface T {
              @TypeUse
              <T> T f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface T {
              <T> @TypeUse T f();
            }
            """)
        .doTest();
  }

  @Test
  public void genericsWithBounds() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import java.util.List;

            interface T {
              @TypeUse
              <T extends List<T>> T f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.List;
            interface T {
              <T extends List<T>> @TypeUse T f();
            }
            """)
        .doTest();
  }

  @Test
  public void typeUseAndNonTypeUse_inWrongOrder() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface T {
              @TypeUse
              @NonTypeUse
              T f();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            interface T {
              @NonTypeUse @TypeUse T f();
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void annotationOfEitherUse_isAllowedToRemainBeforeModifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            interface T {
              @NonTypeUse
              @EitherUse
              public T a();

              @NonTypeUse
              public @EitherUse T b();
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void constructor() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import javax.inject.Inject;

            class T {
              @Inject
              T(int x) {}

              @Inject
              T() {
                System.err.println();
              }
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void parameters_withAnnotationsOutOfOrder() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            class T {
              Object foo(@TypeUse @NonTypeUse Object a) {
                return null;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class T {
              Object foo(@NonTypeUse @TypeUse Object a) {
                return null;
              }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void parameters_withInterspersedModifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            class T {
              Object foo(@TypeUse final Object a) {
                return null;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class T {
              Object foo(final @TypeUse Object a) {
                return null;
              }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void varKeyword() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import com.google.errorprone.annotations.Var;

            class T {
              void m() {
                @Var var x = 1;
                x = 2;
              }
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void recordAnnotation() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            public record Test(String bar) {
              @SuppressWarnings("unused")
              public Test {}
            }
            """)
        .expectUnchanged()
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedJavadoc_enum() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            enum Test {
              @NonTypeUse
              /** Javadoc! */
              ONE;
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            enum Test {
              /** Javadoc! */
              @NonTypeUse
              ONE;
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void interspersedJavadoc_variableNoModifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            class Test {
              @NonTypeUse
              /** Javadoc! */
              int x;
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              /** Javadoc! */
              @NonTypeUse
              int x;
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void variable_genericType_modifiers() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import java.util.List;

            class Test {
              @TypeUse private List<?> x;
              @EitherUse private List<?> y;
              @NonTypeUse private List<?> z;
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.List;
            class Test {
              private @TypeUse List<?> x;
              private @EitherUse List<?> y;
              @NonTypeUse private List<?> z;
            }
            """)
        .doTest(TEXT_MATCH);
  }

  @Test
  public void twoNonTypeAnnotation() {
    helper
        .addSourceLines(
            "AnotherNonTypeUse.java", //
            "@interface AnotherNonTypeUse {}")
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              @NonTypeUse
              // BUG: Diagnostic contains: [AnnotationPosition] @AnotherNonTypeUse is not
              public @AnotherNonTypeUse void f();
            }
            """)
        .doTest();
  }
}
