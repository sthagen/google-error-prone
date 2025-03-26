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

/** Tests for {@link NonCanonicalType}. */
@RunWith(JUnit4.class)
public final class NonCanonicalTypeTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(NonCanonicalType.class, getClass());

  @Test
  public void positive() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.collect.ImmutableMap;

            class Test {
              void test() {
                // BUG: Diagnostic contains: `Map.Entry` was referred to by the non-canonical name
                // `ImmutableMap.Entry`
                ImmutableMap.Entry<?, ?> entry = null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void differingOnlyByPackageName() {
    compilationHelper
        .addSourceLines(
            "foo/A.java",
            """
            package foo;

            public class A {
              public static class B {}
            }
            """)
        .addSourceLines(
            "bar/A.java",
            """
            package bar;

            public class A extends foo.A {}
            """)
        .addSourceLines(
            "D.java",
"""
package bar;

import bar.A;

public interface D {
  // BUG: Diagnostic contains: The type `foo.A.B` was referred to by the non-canonical name
  // `bar.A.B`
  A.B test();
}
""")
        .doTest();
  }

  @Test
  public void notVisibleFromUsageSite() {
    compilationHelper
        .addSourceLines(
            "foo/A.java",
            """
            package foo;

            class A {
              public static class C {}
            }
            """)
        .addSourceLines(
            "foo/B.java",
            """
            package foo;

            public class B extends A {}
            """)
        .addSourceLines(
            "D.java",
            """
            package bar;

            import foo.B;

            public interface D {
              B.C test();
            }
            """)
        .doTest();
  }

  @Test
  public void positiveWithGenerics() {
    compilationHelper
        .addSourceLines(
            "A.java",
            """
            class A<T> {
              class B {}
            }
            """)
        .addSourceLines(
            "AString.java", //
            "class AString extends A<String> {}")
        .addSourceLines(
            "Test.java",
            """
            class Test {
              // BUG: Diagnostic contains: Did you mean 'A.B test() {'
              AString.B test() {
                return null;
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
            import java.util.Map;

            class Test {
              void test() {
                Map.Entry<?, ?> entry = null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void qualifiedName_inLambdaParameter_cantFix() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.function.Function;

            class Test {
              interface Rec extends Function<Rec, Rec> {}

              void run() {
                Rec f = x -> x.apply(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void qualifiedName_ambiguous() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface A {
              interface N {}
            }

            interface B extends A {}

            class C implements D {}

            interface E extends D {
              interface N extends D.N {}
            }

            interface D {
              interface N {}
            }

            class Test extends C implements E {
              // BUG: Diagnostic contains: A.N
              private B.N f() {
                return null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void typeParameter_noFinding() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test<E extends Enum<E>> {
              E test(Class<E> clazz, String name) {
                return E.valueOf(clazz, name);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void arrays() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              int len(String[] xs) {
                return xs.length;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void clazz_noFinding() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void test() {
                var c = boolean.class;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void method_noFinding() {
    compilationHelper
        .addSourceLines(
            "Super.java",
            """
            class Super {
              static void f() {}
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test extends Super {
              void test() {
                Test.f();
              }
            }
            """)
        .doTest();
  }

  // TODO(cushon): the fix for this should be Super<?>.Inner, not Super.Inner
  @Test
  public void innerArray() {
    compilationHelper
        .addSourceLines(
            "Super.java",
            """
            class Super<T> {
              class Inner {}
            }
            """)
        .addSourceLines(
            "Super.java",
            """
            class Sub<T> extends Super<T> {}
            """)
        .addSourceLines(
            "Test.java",
"""
class Test {
  // BUG: Diagnostic contains: `Super.Inner` was referred to by the non-canonical name `Sub.Inner`
  Sub<?>.Inner[] x;
}
""")
        .doTest();
  }

  // see https://github.com/google/error-prone/issues/3639
  @Test
  public void moduleInfo() {
    compilationHelper
        .addSourceLines(
            "module-info.java",
            """
            module testmodule {
              requires java.base;
            }
            """)
        .doTest();
  }

  // https://github.com/google/error-prone/issues/4343
  @Test
  public void typeAnnotation() {
    compilationHelper
        .addSourceLines(
            "Crash.java",
            """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            @interface TA {}

            class Crash {
              class Nested {
                class DoublyNested {}
              }

              class SubNested extends Nested {}

              void foo(Crash.@TA Nested.DoublyNested p) {}

              // BUG: Diagnostic contains: Crash.Nested.DoublyNested
              void bar(Crash.@TA SubNested.DoublyNested p) {}
            }
            """)
        .doTest();
  }
}
