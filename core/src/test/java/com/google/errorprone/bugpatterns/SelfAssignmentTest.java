/*
 * Copyright 2012 The Error Prone Authors.
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
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@RunWith(JUnit4.class)
public class SelfAssignmentTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(SelfAssignment.class, getClass());

  @Test
  public void positiveCases1() {
    compilationHelper
        .addSourceLines(
            "SelfAssignmentPositiveCases1.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * Tests for self assignment
             *
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class SelfAssignmentPositiveCases1 {
              private int a;

              public void test1(int b) {
                // BUG: Diagnostic contains: this.a = b
                this.a = a;
              }

              public void test2(int b) {
                // BUG: Diagnostic contains: remove this line
                a = this.a;
              }

              public void test3() {
                int a = 0;
                // BUG: Diagnostic contains: this.a = a
                a = a;
              }

              public void test4() {
                // BUG: Diagnostic contains: remove this line
                this.a = this.a;
              }

              public void test5() {
                // BUG: Diagnostic contains: this.a = a
                if ((a = a) != 10) {
                  System.out.println("foo");
                }
              }

              // Check that WrappedTreeMap handles folded strings; tested by EndPosTest.
              // See https://code.google.com/p/error-prone/issues/detail?id=209
              public String foldableString() {
                return "foo" + "bar";
              }

              public void testCast() {
                int a = 0;
                // BUG: Diagnostic contains: this.a = (int) a
                a = (int) a;
                // BUG: Diagnostic contains: this.a = (short) a
                a = (short) a;
              }

              public void testCast(int x) {
                // BUG: Diagnostic contains: this.a = (int) x;
                this.a = (int) a;
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void positiveCases2() {
    compilationHelper
        .addSourceLines(
            "SelfAssignmentPositiveCases2.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import static com.google.common.base.Preconditions.checkNotNull;
            import static java.util.Objects.requireNonNull;

            /**
             * Tests for self assignment
             *
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class SelfAssignmentPositiveCases2 {
              private int a;
              private Foo foo;

              // BUG: Diagnostic contains: private static final Object obj
              private static final Object obj = SelfAssignmentPositiveCases2.obj;
              // BUG: Diagnostic contains: private static final Object obj2
              private static final Object obj2 = checkNotNull(SelfAssignmentPositiveCases2.obj2);

              public void test6() {
                Foo foo = new Foo();
                foo.a = 2;
                // BUG: Diagnostic contains: remove this line
                foo.a = foo.a;
                // BUG: Diagnostic contains: checkNotNull(foo.a)
                foo.a = checkNotNull(foo.a);
                // BUG: Diagnostic contains: requireNonNull(foo.a)
                foo.a = requireNonNull(foo.a);
              }

              public void test7() {
                Foobar f = new Foobar();
                f.foo = new Foo();
                f.foo.a = 10;
                // BUG: Diagnostic contains: remove this line
                f.foo.a = f.foo.a;
                // BUG: Diagnostic contains: checkNotNull(f.foo.a)
                f.foo.a = checkNotNull(f.foo.a);
                // BUG: Diagnostic contains: requireNonNull(f.foo.a)
                f.foo.a = requireNonNull(f.foo.a);
              }

              public void test8() {
                foo = new Foo();
                // BUG: Diagnostic contains: remove this line
                this.foo.a = foo.a;
                // BUG: Diagnostic contains: checkNotNull(foo.a)
                this.foo.a = checkNotNull(foo.a);
                // BUG: Diagnostic contains: requireNonNull(foo.a)
                this.foo.a = requireNonNull(foo.a);
              }

              public void test9(Foo fao, Foo bar) {
                // BUG: Diagnostic contains: this.foo = fao
                this.foo = foo;
                // BUG: Diagnostic contains: this.foo = checkNotNull(fao)
                this.foo = checkNotNull(foo);
                // BUG: Diagnostic contains: this.foo = requireNonNull(fao)
                this.foo = requireNonNull(foo);
              }

              public void test10(Foo foo) {
                // BUG: Diagnostic contains: this.foo = foo
                foo = foo;
                // BUG: Diagnostic contains: this.foo = checkNotNull(foo)
                foo = checkNotNull(foo);
                // BUG: Diagnostic contains: this.foo = requireNonNull(foo)
                foo = requireNonNull(foo);
              }

              class Test11 {
                final Foo foo;

                Foo fao;

                Test11(Foo foo) {
                  if (true) {
                    // BUG: Diagnostic contains: this.fao = foo
                    foo = foo;
                  }
                  this.foo = foo;
                }

                public void test11a(Foo foo) {
                  // BUG: Diagnostic contains: this.fao = foo
                  foo = foo;
                }
              }

              private static class Foo {
                int a;
              }

              private static class Bar {
                int a;
              }

              private static class Foobar {
                Foo foo;
                Bar bar;
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "SelfAssignmentNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import static com.google.common.base.Preconditions.checkNotNull;
            import static java.util.Objects.requireNonNull;

            /**
             * Tests for self assignment
             *
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class SelfAssignmentNegativeCases {
              private int a;

              private static int b = StaticClass.b;
              private static final int C = SelfAssignmentNegativeCases.b;
              private static final int D = checkNotNull(SelfAssignmentNegativeCases.C);
              private static final int E = requireNonNull(SelfAssignmentNegativeCases.D);
              private static final int F = StaticClass.getIntArr().length;

              public void test1(int a) {
                int b = SelfAssignmentNegativeCases.b;
                this.a = a;
                this.a = checkNotNull(a);
                this.a = requireNonNull(a);
              }

              public void test2() {
                int a = 0;
                int b = a;
                a = b;
              }

              public void test3() {
                int a = 10;
              }

              public void test4() {
                int i = 1;
                i += i;
              }

              public void test5(SelfAssignmentNegativeCases n) {
                a = n.a;
              }

              public void test6() {
                Foo foo = new Foo();
                Bar bar = new Bar();
                foo.a = bar.a;
                foo.a = checkNotNull(bar.a);
                foo.a = requireNonNull(bar.a);
              }

              public void test7() {
                Foobar f1 = new Foobar();
                f1.foo = new Foo();
                f1.bar = new Bar();
                f1.foo.a = f1.bar.a;
                f1.foo.a = checkNotNull(f1.bar.a);
                f1.foo.a = requireNonNull(f1.bar.a);
              }

              public void test8(SelfAssignmentNegativeCases that) {
                this.a = that.a;
                this.a = checkNotNull(that.a);
                this.a = requireNonNull(that.a);
              }

              public void test9(int a) {
                a += a;
              }

              private static class Foo {
                int a;
              }

              private static class Bar {
                int a;
              }

              private static class Foobar {
                Foo foo;
                Bar bar;
              }

              private static class StaticClass {
                static int b;

                public static int[] getIntArr() {
                  return new int[10];
                }
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void initializerBlock() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              int foo;

              Test(int foo) {}

              {
                // BUG: Diagnostic contains:
                this.foo = foo;
              }
            }\
            """)
        .doTest();
  }
}
