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

package com.google.errorprone.util;

import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Kinds.KindSelector;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FindIdentifiers}. */
@RunWith(JUnit4.class)
public class FindIdentifiersTest {

  /** A {@link BugChecker} that prints all identifiers in scope at a call to String.format(). */
  @BugPattern(
      severity = SeverityLevel.ERROR,
      summary = "Prints all identifiers in scope at a call to String.format()")
  public static class PrintIdents extends BugChecker implements MethodInvocationTreeMatcher {
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
      if (MethodMatchers.staticMethod()
          .onClass("java.lang.String")
          .named("format")
          .matches(tree, state)) {
        return buildDescription(tree)
            .setMessage(FindIdentifiers.findAllIdents(state).toString())
            .build();
      }
      return Description.NO_MATCH;
    }
  }

  @Test
  public void findAllIdentsLocals() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String s1 = "";
                String s2 = "";
                // BUG: Diagnostic contains: [s1, s2]
                String.format(s1 + s2);
                String s3 = "";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsLocalsEnclosedTreeNode() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String s1 = "";
                String s2 = "";
                if (true)
                  // BUG: Diagnostic contains: [s1, s2]
                  String.format(s1 + s2);
                String s3 = "";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsLocalsOuterScope() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String s1 = "";
                while (true) {
                  // BUG: Diagnostic contains: [s1]
                  String.format(s1);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsParams() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt(String s1, String s2) {
                // BUG: Diagnostic contains: [s1, s2]
                String.format(s1 + s2);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsMixedLocalsAndParams() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt(String s1) {
                String s2 = s1;
                while (true) {
                  String s3 = s1;
                  // BUG: Diagnostic contains: [s3, s2, s1]
                  String.format(s3 + s2 + s1);
                  String s4 = s1;
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsFields() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private static String s1;
              private String s2;

              private void doIt() {
                // BUG: Diagnostic contains: [s1, s2]
                String.format(s1 + s2);
              }

              private static void staticDoIt() {
                // BUG: Diagnostic contains: [s1]
                String.format(s1);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInheritedFieldsSamePackage() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/Super.java",
            """
            package pkg;

            public class Super {
              public String s1;
              protected String s2;
              String s3;
              private String s4;
              public static String s5;
              protected static String s6;
              static String s7;
              private static String s8;
            }
            """)
        .addSourceLines(
            "pkg/Sub.java",
            """
            package pkg;

            public class Sub extends Super {
              private void doIt() {
                // BUG: Diagnostic contains: [s1, s2, s3, s5, s6, s7]
                String.format(s1 + s2 + s3 + s5 + s6 + s7);
              }

              private static void doItStatically() {
                // BUG: Diagnostic contains: [s5, s6, s7]
                String.format(s5 + s6 + s7);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInheritedFieldsDifferentPackage() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg1/Super.java",
            """
            package pkg1;

            public class Super {
              public String s1;
              protected String s2;
              String s3;
              private String s4;
              public static String s5;
              protected static String s6;
              static String s7;
              private static String s8;
            }
            """)
        .addSourceLines(
            "pkg2/Sub.java",
            """
            package pkg2;

            import pkg1.Super;

            public class Sub extends Super {
              private void doIt() {
                // BUG: Diagnostic contains: [s1, s2, s5, s6]
                String.format(s1 + s2 + s5 + s6);
              }

              private static void doItStatically() {
                // BUG: Diagnostic contains: [s5, s6]
                String.format(s5 + s6);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInheritedFieldsSameTopLevel() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Holder.java",
            """
            public class Holder {
              class Super {
                private String s1;
              }

              class Sub extends Super {
                private String s2;

                private void doIt() {
                  // BUG: Diagnostic contains: [s2]
                  String.format(s2);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticNestedClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Outer.java",
            """
            class Outer {
              private String s1;
              private static String s2;

              static class Inner {
                private String s3;
                private static final String s4 = "s4";

                private void doIt() {
                  // BUG: Diagnostic contains: [s3, s4, s2]
                  String.format(s3 + s4 + s2);
                }

                private static void doItStatically() {
                  // BUG: Diagnostic contains: [s4, s2]
                  String.format(s4 + s2);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInnerClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Outer.java",
            """
            class Outer {
              private String s1;
              private static String s2;

              class Inner {
                private String s3;
                private static final String s4 = "s4";

                class EvenMoreInner {
                  private String s5;
                  private static final String s6 = "s6";

                  private void doIt() {
                    // BUG: Diagnostic contains: [s5, s6, s3, s4, s1, s2]
                    String.format(s5 + s6 + s3 + s4 + s1 + s2);
                  }
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticMethodWithLocalClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private String s1;
              private static String s2;

              private static void doIt() {
                class Helper {
                  private String s3;
                  private static final String s4 = "s6";

                  void reallyDoIt() {
                    // BUG: Diagnostic contains: [s3, s4, s2]
                    String.format(s4 + s3 + s2);
                  }
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticLambda() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private String s1;
              private static String s2;
              // BUG: Diagnostic contains: [s2]
              private static Runnable r = () -> String.format(s2);
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticLambdaWithLocalClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private String s1;
              private static String s2;
              private static Runnable doIt =
                  () -> {
                    class Helper {
                      private String s3;

                      void reallyDoIt() {
                        // BUG: Diagnostic contains: [s3, s2]
                        String.format(s3 + s2);
                      }
                    }
                  };
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInnerClassWhereTopLevelExtends() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "ToExtend.java",
            """
            class ToExtend {
              String s1;
            }
            """)
        .addSourceLines(
            "Outer.java",
            """
            class Outer extends ToExtend {
              private String s2;

              class Inner {
                void doIt() {
                  // BUG: Diagnostic contains: [s2, s1]
                  String.format(s2 + s1);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsMixedStaticAndNonStaticNestedClasses() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Outer.java",
            """
            class Outer {
              String s1;

              static class Inner {
                String s2;

                class EvenMoreInner {
                  String s3;

                  void doIt() {
                    // BUG: Diagnostic contains: [s3, s2]
                    String.format(s3 + s2);
                  }
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsAnonymousClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public String s1;

              private void doIt(final String s2) {
                String s3 = "";
                String s4 = "";
                new Thread(
                        new Runnable() {
                          @Override
                          public void run() {
                            String s5 = "";
                            s5 = "foo";
                            // BUG: Diagnostic contains: [s5, s3, s2, s1]
                            String.format(s5 + s3 + s2 + s1);
                          }
                        })
                    .start();
                s4 = "foo";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsLambda() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public String s1;

              private void doIt(final String s2) {
                String s3 = "";
                String s4 = "";
                // BUG: Diagnostic contains: [s3, s2, s1]
                new Thread(() -> String.format(s3 + s2 + s1)).start();
                s4 = "foo";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsLambdaWithParams() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            import java.util.function.Function;

            class Test {
              public String s1;

              private void doIt(final String s2) {
                String s3 = "";
                String s4 = "";
                // BUG: Diagnostic contains: [name, s3, s2, s1]
                Function<String, String> f = (String name) -> String.format(name + s3 + s2 + s1);
                s4 = "foo";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsExceptionParameter() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public String s1;

              private void doIt(final String s2) {
                String s3 = "";
                try {
                  s3 = s1;
                } catch (Exception e) {
                  // BUG: Diagnostic contains: [e, s3, s2, s1]
                  String.format(e + s3 + s2 + s1);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsLocalClass() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String s1 = "";
                final String s2 = "";
                String s3 = "";
                class Local {
                  public void doIt() {
                    // BUG: Diagnostic contains: [s1, s2]
                    String.format(s2 + s1);
                  }
                }
                s3 = "foo";
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticInitializer() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public static String s1;
              public String s2;

              static {
                // BUG: Diagnostic contains: [s1]
                String.format(s1);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticVariableInitializer() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public static String s1;
              public String s2;
              // BUG: Diagnostic contains: [s1]
              public static String s3 = String.format(s1);
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsExplicitConstructorInvocation() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private String s1;
              private static String s2;

              public Test(String s1) {
                this.s1 = s1;
              }

              public Test() {
                // BUG: Diagnostic contains: [s2]
                this(String.format(s2));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsExplicitConstructorInvocation2() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Super.java",
            """
            class Super {
              protected String s1;
              protected static String s2;

              public Super(String s1) {
                this.s1 = s1;
              }
            }
            """)
        .addSourceLines(
            "Sub.java",
            """
            class Sub extends Super {
              public Sub(String s3) {
                // BUG: Diagnostic contains: [s3, s2]
                super(String.format(s3 + s2));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInterface() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "MyInterface.java",
            """
            interface MyInterface {
              String EMPTY_STRING = "";
            }
            """)
        .addSourceLines(
            "MyImpl.java",
            """
            class MyImpl implements MyInterface {
              void doIt() {
                // BUG: Diagnostic contains: [EMPTY_STRING]
                String.format(EMPTY_STRING);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsEnumConstant() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "MyEnum.java",
            """
            enum MyEnum {
              FOO,
              BAR,
              BAZ;

              static class Nested {
                void doIt() {
                  // BUG: Diagnostic contains: [FOO, BAR, BAZ]
                  String.format(FOO.toString() + BAR.toString() + BAZ.toString());
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsForLoop() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void doIt() {
                for (int i = 0; i < 10; i++) {
                  // BUG: Diagnostic contains: [i]
                  String.format(Integer.toString(i));
                }
                for (int j : new int[] {0, 1, 2}) {
                  // BUG: Diagnostic contains: [j]
                  String.format(Integer.toString(j));
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsTryWithResources() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            import java.io.*;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.*;

            class Test {
              void doIt() {
                try (BufferedReader reader = Files.newBufferedReader(Paths.get("foo"));
                    InputStream is =
                        new ByteArrayInputStream(
                        // BUG: Diagnostic contains: [reader]
                            String.format(reader.readLine()).getBytes(StandardCharsets.UTF_8))) {
                  // BUG: Diagnostic contains: [reader, is]
                  String.format(reader.readLine() + is.toString());
                } catch (IOException e) {
                  // BUG: Diagnostic contains: [e]
                  String.format(e.toString());
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsStaticImport() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            import static java.nio.charset.StandardCharsets.UTF_8;

            class Test {
              void doIt() {
                // BUG: Diagnostic contains: [UTF_8]
                String.format(UTF_8.toString());
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdentsInheritedStaticImport() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              String EMPTY = "";
            }
            """)
        .addSourceLines(
            "pkg/Super.java",
            """
            package pkg;

            public class Super {
              public static final String FOO = "foo";
            }
            """)
        .addSourceLines(
            "pkg/Impl.java",
            """
            package pkg;

            public class Impl extends Super implements MyInterface {}
            """)
        .addSourceLines(
            "Test.java",
            """
            import static pkg.Impl.EMPTY;
            import static pkg.Impl.FOO;

            public class Test {
              void doIt() {
                // BUG: Diagnostic contains: [EMPTY, FOO]
                String.format(EMPTY + FOO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariables() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static void test(Object o) {
                if (o instanceof MyInterface mi && o instanceof MyInterface mi2) {
                  // BUG: Diagnostic contains: [mi, mi2, o]
                  String.format("");
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariablesNeitherVisible() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static void test(Object o) {
                if (o instanceof MyInterface mi || o instanceof MyInterface mi2) {
                  // BUG: Diagnostic contains: [o]
                  String.format("");
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariablesNegated() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static void test(Object o) {
                if (!(o instanceof MyInterface mi)) {
                  return;
                }
                // BUG: Diagnostic contains: [mi, o]
                String.format("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariablesNegatedWithExplicitElse() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static void test(Object o) {
                if (!(o instanceof MyInterface mi)) {
                  // BUG: Diagnostic contains: [o]
                  String.format("");
                } else {
                  // BUG: Diagnostic contains: [mi, o]
                  String.format("");
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariablesNegatedButMayFallThrough() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static void test(Object o) {
                if (!(o instanceof MyInterface mi)) {}
                // BUG: Diagnostic contains: [o]
                String.format("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariableWithTernary() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static boolean test(Object o) {
                return o instanceof MyInterface mi
                    ?
                    // BUG: Diagnostic contains: [mi, o]
                    String.format("").isEmpty()
                    :
                    // BUG: Diagnostic contains: [o]
                    String.format("").isEmpty();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariableWithComplexConditions() {
    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "pkg/MyInterface.java",
            """
            package pkg;

            public interface MyInterface {
              static boolean test(Object o) {
                // BUG: Diagnostic contains: [s, o]
                return o instanceof String s && String.format(s).isEmpty();
              }

              static boolean test2(Object o) {
                // BUG: Diagnostic contains: [o]
                return o instanceof String s || String.format("").isEmpty();
              }

              static boolean test3(Object o) {
                // BUG: Diagnostic contains: [s, o]
                return !(o instanceof String s) || String.format(s).isEmpty();
              }

              static boolean test4(Object o) {
                // BUG: Diagnostic contains: [s, o]
                return o instanceof String s && true && String.format(s).isEmpty();
              }

              static boolean test5(Object o) {
                // BUG: Diagnostic contains: [s, o]
                return !(o instanceof String s && true) || (true && String.format(s).isEmpty());
              }

              static boolean test6(Object o) {
                // BUG: Diagnostic contains: [o]
                return !(o instanceof String s && true) && (true && String.format("").isEmpty());
              }

              static boolean test7(Object o) {
                // BUG: Diagnostic contains: [o]
                return o instanceof String s && true || true && String.format("").isEmpty();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findAllIdents_bindingVariableInRecord() {
    assume().that(Runtime.version().feature()).isAtLeast(21);

    CompilationTestHelper.newInstance(PrintIdents.class, getClass())
        .addSourceLines(
            "Point.java",
            """
            record Point(int x, int y) {
              static boolean test(Object o) {
                // BUG: Diagnostic contains: [x, y, o]
                return o instanceof Point(int x, int y) && String.format("").isEmpty();
              }
            }
            """)
        .doTest();
  }

  /** A {@link BugChecker} that prints all identifiers in scope at a method declaration. */
  @BugPattern(
      severity = SeverityLevel.ERROR,
      summary = "Prints all identifiers in scope at a method declaration.")
  public static class PrintIdentsAtMethodDeclaration extends BugChecker
      implements MethodTreeMatcher {
    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
      return buildDescription(tree)
          .setMessage(FindIdentifiers.findAllIdents(state).toString())
          .build();
    }
  }

  @Test
  public void findAllIdentsAtDoItMethod() {
    CompilationTestHelper.newInstance(PrintIdentsAtMethodDeclaration.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private String s1;
              private String s2;

              // BUG: Diagnostic contains: [s1, s2]
              private void doIt() {}
            }
            """)
        .doTest();
  }

  /**
   * A {@link BugChecker} that prints all unused variables in scope at a call to String.format().
   */
  @BugPattern(
      severity = SeverityLevel.ERROR,
      summary = "Prints all unused variabled in scope at a call to String.format()")
  public static class PrintUnusedVariables extends BugChecker
      implements MethodInvocationTreeMatcher {
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
      if (MethodMatchers.staticMethod()
          .onClass("java.lang.String")
          .named("format")
          .matches(tree, state)) {
        return buildDescription(tree)
            .setMessage(FindIdentifiers.findUnusedIdentifiers(state).toString())
            .build();
      }
      return Description.NO_MATCH;
    }
  }

  @Test
  public void returnsVariable_findUnusedVariables_whenVariableUnusedInBlock() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String a = null;
                // BUG: Diagnostic contains: [a]
                String.format(a);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void ignoresVariable_findUnusedVariables_whenVariableUsedAsArgumentInBlock() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void test(String arg) {}

              private void doIt() {
                String a = null;
                test(a);
                // BUG: Diagnostic contains: []
                String.format(null);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void ignoresVariable_findUnusedVariables_whenVariableUsedInExpressionInBlock() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void test(String arg) {}

              private void doIt() {
                String a = null;
                if (a == null) {}
                // BUG: Diagnostic contains: []
                String.format(null);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnsVariable_findUnusedVariables_whenVariableUsedLaterInBlock() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void test(String arg) {}

              private void doIt() {
                String a = null;
                // BUG: Diagnostic contains: [a]
                String.format(null);
                test(a);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnsVariable_findUnusedVariables_whenVariableUnusedButSameNameAsField() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              String a = null;

              void test(String arg) {}

              private void doIt() {
                test(a);
                String a = null;
                // BUG: Diagnostic contains: [a]
                String.format(null);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnsVariable_findUnusedVariables_whenVariableDefinedInEnhancedFor() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                for (String a : new String[] {}) {
                  // BUG: Diagnostic contains: [a]
                  String.format(a);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnsVariable_findUnusedVariables_whenLaterUseInAssignment() {
    CompilationTestHelper.newInstance(PrintUnusedVariables.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                String a = null;
                // BUG: Diagnostic contains: [a]
                String b = String.format(a);
              }
            }
            """)
        .doTest();
  }

  /** A {@link BugChecker} that prints all fields in receiver class on method invocations. */
  @BugPattern(
      severity = SeverityLevel.ERROR,
      summary = "Prints all fields in receivers of method invocations")
  public static class PrintFields extends BugChecker implements MethodInvocationTreeMatcher {
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
      MethodSymbol symbol = ASTHelpers.getSymbol(tree);
      Type receiverType = symbol.owner.asType();
      ImmutableList<VarSymbol> fields = FindIdentifiers.findAllFields(receiverType, state);
      return buildDescription(tree).setMessage(fields.toString()).build();
    }
  }

  @Test
  public void findsStaticAndInstanceFields_findAllFields() {
    CompilationTestHelper.newInstance(PrintFields.class, getClass())
        .addSourceLines(
            "Reference.java",
            """
            class Reference {
              static String staticField;
              String instanceField;

              static void test() {}
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                // BUG: Diagnostic contains: [staticField, instanceField]
                Reference.test();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void findsInheritedStaticAndInstanceFields_findAllFields() {
    CompilationTestHelper.newInstance(PrintFields.class, getClass())
        .addSourceLines(
            "Super.java",
            """
            class Super {
              static String staticField;
              String instanceField;
            }
            """)
        .addSourceLines(
            "Reference.java",
            """
            class Reference extends Super {
              static void test() {}
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              private void doIt() {
                // BUG: Diagnostic contains: [staticField, instanceField]
                Reference.test();
              }
            }
            """)
        .doTest();
  }

  /** A {@link BugChecker} that prints whether {@code A} is visible on each member select. */
  @BugPattern(severity = SeverityLevel.ERROR, summary = "A is visible")
  public static class IsAVisible extends BugChecker implements MemberSelectTreeMatcher {
    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
      return FindIdentifiers.findIdent("A", state, KindSelector.TYP) == null
          ? Description.NO_MATCH
          : describeMatch(tree);
    }
  }

  @Test
  public void aNotVisibleOutsideClass() {
    CompilationTestHelper.newInstance(IsAVisible.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test extends java.lang.Object {
              // BUG: Diagnostic contains:
              Test.A foo() {
                return null;
              }

              class A {}
            }
            """)
        .doTest();
  }

  @Test
  public void aVisibleOutsideClass() {
    CompilationTestHelper.newInstance(IsAVisible.class, getClass())
        .addSourceLines(
            "A.java",
            """
            package pkg;

            class A {}
            """)
        .addSourceLines(
            "Test.java",
            """
            package pkg;

            // BUG: Diagnostic contains:
            class Test extends java.lang.Object {
              // BUG: Diagnostic contains:
              pkg.A foo() {
                return null;
              }
            }
            """)
        .doTest();
  }

  /** A {@link BugChecker} that prints whether {@code A} is visible on each class tree. */
  @BugPattern(severity = SeverityLevel.ERROR, summary = "A is visible on ClassTree")
  public static class IsAVisibleOnClassTree extends BugChecker implements ClassTreeMatcher {
    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
      return FindIdentifiers.findIdent("A", state, KindSelector.TYP) == null
          ? Description.NO_MATCH
          : describeMatch(tree);
    }
  }

  @Test
  public void aVisibleOnClassTree() {
    CompilationTestHelper.newInstance(IsAVisibleOnClassTree.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            // BUG: Diagnostic contains:
            class Test {
              // BUG: Diagnostic contains:
              class A {}
            }
            """)
        .doTest();
  }

  @Test
  public void aNotVisibleOnClassTree() {
    CompilationTestHelper.newInstance(IsAVisibleOnClassTree.class, getClass())
        .addSourceLines(
            "Test.java",
            """
            class Test {
              // BUG: Diagnostic contains:
              class Sub {
                // BUG: Diagnostic contains:
                class A {}
              }
            }
            """)
        .doTest();
  }
}
