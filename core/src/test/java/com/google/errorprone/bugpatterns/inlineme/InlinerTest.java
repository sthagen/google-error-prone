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

package com.google.errorprone.bugpatterns.inlineme;

import static com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH;
import static com.google.errorprone.bugpatterns.inlineme.Inliner.PREFIX_FLAG;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.scanner.ScannerSupplier;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link Inliner}. */
@RunWith(JUnit4.class)
public class InlinerTest {
  /*
    We expect that all @InlineMe annotations we try to use as inlineable targets are valid,
    so we run both checkers here. If the Validator trips on a method, we'll suggest some
    replacement which should trip up the checker.
  */
  private final BugCheckerRefactoringTestHelper refactoringTestHelper =
      BugCheckerRefactoringTestHelper.newInstance(
          ScannerSupplier.fromBugCheckerClasses(Inliner.class, Validator.class), getClass());

  @Test
  public void instanceMethod_withThisLiteral() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.foo2(value)")
              public void foo1(String value) {
                foo2(value);
              }

              public void foo2(String value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.foo1("frobber!");
                client.foo1("don't change this!");
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.foo2("frobber!");
                client.foo2("don't change this!");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void nestedQuotes() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.after(foo)")
              public String before(String foo) {
                return after(foo);
              }

              public String after(String foo) {
                return "frobber";
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String result = client.before("\\"");
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String result = client.after("\\"");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void method_withParamSwap() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.after(paramB, paramA)")
              public void before(String paramA, String paramB) {
                after(paramB, paramA);
              }

              public void after(String paramB, String paramA) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import java.time.Duration;

            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String a = "a";
                String b = "b";
                client.before(a, b);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import java.time.Duration;

            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String a = "a";
                String b = "b";
                client.after(b, a);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void method_withReturnStatement() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.after()")
              public String before() {
                return after();
              }

              public String after() {
                return "frobber";
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String result = client.before();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                String result = client.after();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void staticMethod_explicitTypeParam() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.foo;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(
                  replacement = "Client.after()",
                  imports = {"com.google.foo.Client"})
              public static <T> T before() {
                return after();
              }

              public static <T> T after() {
                return (T) null;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            package com.google.foo;

            public final class Caller {
              public void doTest() {
                String str = Client.<String>before();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            "package com.google.foo;",
            "public final class Caller {",
            "  public void doTest() {",
            // TODO(b/166285406): Client.<String>after();
            "    String str = Client.after();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethod_withConflictingImport() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              private Duration deadline = Duration.ofSeconds(5);

              @Deprecated
              @InlineMe(
                  replacement = "this.setDeadline(Duration.ofMillis(millis))",
                  imports = {"java.time.Duration"})
              public void setDeadline(long millis) {
                setDeadline(Duration.ofMillis(millis));
              }

              public void setDeadline(Duration deadline) {
                this.deadline = deadline;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import org.joda.time.Duration;

            public final class Caller {
              public void doTest() {
                Duration jodaDuration = Duration.millis(42);
                Client client = new Client();
                client.setDeadline(42);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import org.joda.time.Duration;

            public final class Caller {
              public void doTest() {
                Duration jodaDuration = Duration.millis(42);
                Client client = new Client();
                client.setDeadline(java.time.Duration.ofMillis(42));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void instanceMethod_withPartiallyQualifiedInnerType() {
    refactoringTestHelper
        .addInputLines(
            "A.java",
            """
            package com.google;

            public class A {
              public static class Inner {
                public static void foo() {}
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            """
            import com.google.A;
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "A.Inner.foo()", imports = "com.google.A")
              public void something() {
                A.Inner.foo();
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.something();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import com.google.A;

            public final class Caller {
              public void doTest() {
                Client client = new Client();
                A.Inner.foo();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void instanceMethod_withConflictingMethodNameAndParameterName() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              private long deadline = 5000;

              @Deprecated
              @InlineMe(replacement = "this.millis(millis)")
              public void setDeadline(long millis) {
                millis(millis);
              }

              public void millis(long millis) {
                this.deadline = millis;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.setDeadline(42);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.millis(42);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void staticMethod_withStaticImport_withImport() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.test;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(
                  replacement = "Client.after(value)",
                  imports = {"com.google.test.Client"})
              public static void before(int value) {
                after(value);
              }

              public static void after(int value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import static com.google.test.Client.before;

            public final class Caller {
              public void doTest() {
                before(42);
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            """
            import static com.google.test.Client.before;
            import com.google.test.Client;

            public final class Caller {
              public void doTest() {
                Client.after(42);
              }
            }
            """)
        .doTest();
  }

  // With the new suggester implementation, we always import the surrounding class, so the suggested
  // replacement here isn't considered valid.
  @Ignore("b/176439392")
  @Test
  public void staticMethod_withStaticImport_withStaticImportReplacement() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.test;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(
                  replacement = "after(value)",
                  staticImports = {"com.google.test.Client.after"})
              public static void before(int value) {
                after(value);
              }

              public static void after(int value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import static com.google.test.Client.before;

            public final class Caller {
              public void doTest() {
                before(42);
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            """
            import static com.google.test.Client.after;
            import static com.google.test.Client.before;

            public final class Caller {
              public void doTest() {
                after(42);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void instanceMethodCalledBySubtype() {
    refactoringTestHelper
        .addInputLines(
            "Parent.java",
            """
            package com.google.test;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public class Parent {
              @Deprecated
              @InlineMe(
                  replacement = "this.after(Duration.ofMillis(value))",
                  imports = {"java.time.Duration"})
              protected final void before(int value) {
                after(Duration.ofMillis(value));
              }

              protected void after(Duration value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Child.java",
            """
            package com.google.test;

            public final class Child extends Parent {
              public void doTest() {
                before(42);
              }
            }
            """)
        .addOutputLines(
            "Child.java",
            """
            package com.google.test;

            import java.time.Duration;

            public final class Child extends Parent {
              public void doTest() {
                after(Duration.ofMillis(42));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void constructorCalledBySubtype() {
    refactoringTestHelper
        .addInputLines(
            "Parent.java",
            """
            package com.google.test;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public class Parent {
              @Deprecated
              @InlineMe(
                  replacement = "this(Duration.ofMillis(value))",
                  imports = {"java.time.Duration"})
              protected Parent(int value) {
                this(Duration.ofMillis(value));
              }

              protected Parent(Duration value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Child.java",
            """
            package com.google.test;

            public final class Child extends Parent {
              public Child() {
                super(42);
              }
            }
            """)
        .addOutputLines(
            "Child.java",
            """
            package com.google.test;

            import java.time.Duration;

            public final class Child extends Parent {
              public Child() {
                super(Duration.ofMillis(42));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void fluentMethodChain() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.baz()")
              public Client foo() {
                return baz();
              }

              @Deprecated
              @InlineMe(replacement = "this.baz()")
              public Client bar() {
                return baz();
              }

              public Client baz() {
                return this;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client().foo().bar();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client().baz().baz();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void inliningWithField() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              @Deprecated
              @InlineMe(
                  replacement = "this.setTimeout(Duration.ZERO)",
                  imports = {"java.time.Duration"})
              public void clearTimeout() {
                setTimeout(Duration.ZERO);
              }

              public void setTimeout(Duration timeout) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                new Client().clearTimeout();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import java.time.Duration;

            public final class Caller {
              public void doTest() {
                new Client().setTimeout(Duration.ZERO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnThis() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this")
              public Client noOp() {
                return this;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client = client.noOp();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client = client;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnThis_preChained() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this")
              public Client noOp() {
                return this;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client().noOp();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnThis_postChained() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this")
              public Client noOp() {
                return this;
              }

              public void bar() {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                new Client().noOp().bar();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                new Client().bar();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void returnThis_alone() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this")
              public Client noOp() {
                return this;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.noOp();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void inlineUnvalidatedInline() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.errorprone.annotations.InlineMeValidationDisabled;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMeValidationDisabled(\"Migrating to factory method\")",
            "  @InlineMe(replacement = \"Client.create()\", imports = \"foo.Client\")",
            "  public Client() {}",
            "  ",
            // The Inliner wants to inline the body of this factory method to the factory method :)
            "  @SuppressWarnings(\"InlineMeInliner\")",
            "  public static Client create() { return new Client(); }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import foo.Client;

            public final class Caller {
              public void doTest() {
                Client client = new Client();
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import foo.Client;

            public final class Caller {
              public void doTest() {
                Client client = Client.create();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void inlineUnvalidatedInlineMessage() {
    CompilationTestHelper.newInstance(Inliner.class, getClass())
        .addSourceLines(
            "Client.java",
            "package foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.errorprone.annotations.InlineMeValidationDisabled;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMeValidationDisabled(\"Migrating to factory method\")",
            "  @InlineMe(replacement = \"Client.create()\", imports = \"foo.Client\")",
            "  public Client() {}",
            "  ",
            // The Inliner wants to inline the body of this factory method to the factory method :)
            "  @SuppressWarnings(\"InlineMeInliner\")",
            "  public static Client create() { return new Client(); }",
            "}")
        .addSourceLines(
            "Caller.java",
            """
import foo.Client;

public final class Caller {
  public void doTest() {
    // BUG: Diagnostic contains: NOTE: this is an unvalidated inlining! Reasoning: Migrating to
    // factory method
    Client client = new Client();
  }
}
""")
        .doTest();
  }

  @Test
  public void varargs() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.after(inputs)")
              public void before(int... inputs) {
                after(inputs);
              }

              public void after(int... inputs) {}

              @Deprecated
              @InlineMe(replacement = "this.after(inputs)")
              public void extraBefore(int first, int... inputs) {
                after(inputs);
              }

              @Deprecated
              @InlineMe(replacement = "this.after(first)")
              public void ignoreVarargs(int first, int... inputs) {
                after(first);
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.before(1);
                client.before();
                client.before(1, 2, 3);
                client.extraBefore(42, 1);
                client.extraBefore(42);
                client.extraBefore(42, 1, 2, 3);
                client.ignoreVarargs(42, 1);
                client.ignoreVarargs(42);
                client.ignoreVarargs(42, 1, 2, 3);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.after(1);
                client.after();
                client.after(1, 2, 3);
                client.after(1);
                client.after();
                client.after(1, 2, 3);
                client.after(42);
                client.after(42);
                client.after(42);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void varargsWithPrecedingElements() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.after(first, inputs)")
              public void before(int first, int... inputs) {
                after(first, inputs);
              }

              public void after(int first, int... inputs) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.before(1);
                client.before(1, 2, 3);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.after(1);
                client.after(1, 2, 3);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void replaceWithJustParameter() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "x")
              public final int identity(int x) {
                return x;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                int x = client.identity(42);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                int x = 42;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void orderOfOperations() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "x * y")
              public int multiply(int x, int y) {
                return x * y;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                int x = client.multiply(5, 10);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5, 10);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void orderOfOperationsWithParamAddition() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "x * y")
              public int multiply(int x, int y) {
                return x * y;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                int x = client.multiply(5 + 3, 10);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5 + 3, 10);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void orderOfOperationsWithTrailingOperand() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "x * y")
              public int multiply(int x, int y) {
                return x * y;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                int x = client.multiply(5 + 3, 10) * 5;
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5 + 3, 10) * 5;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void booleanParameterWithInlineComment() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(replacement = "this.after(/* isAdmin = */ isAdmin)")
              @Deprecated
              public void before(boolean isAdmin) {
                after(/* isAdmin= */ isAdmin);
              }

              public void after(boolean isAdmin) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.before(false);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(b/189535612): this is a bug!
            "    client.after(/* false = */ false);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void trailingSemicolon() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(replacement = "this.after(/* foo= */ isAdmin);;;;")
              @Deprecated
              public boolean before(boolean isAdmin) {
                return after(/* foo= */ isAdmin);
              }

              public boolean after(boolean isAdmin) {
                return isAdmin;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                boolean x = (client.before(false) || true);
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                boolean x = (client.after(/* false= */ false) || true);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void customInlineMe() {
    refactoringTestHelper
        .addInputLines(
            "InlineMe.java",
            """
            package bespoke;

            public @interface InlineMe {
              String replacement();

              String[] imports() default {};

              String[] staticImports() default {};
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            """
            import bespoke.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.foo2(value)")
              public void foo1(String value) {
                foo2(value);
              }

              public void foo2(String value) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.foo1("frobber!");
                client.foo1("don't change this!");
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                client.foo2("frobber!");
                client.foo2("don't change this!");
              }
            }
            """)
        .doTest();
  }

  // b/268215956
  @Test
  public void varArgs() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.foo;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(
                  replacement = "Client.execute2(format, args)",
                  imports = {"com.google.foo.Client"})
              public static void execute1(String format, Object... args) {
                execute2(format, args);
              }

              public static void execute2(String format, Object... args) {
                // do nothing
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import com.google.foo.Client;

            public final class Caller {
              public void doTest() {
                Client.execute1("hi %s");
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            """
            import com.google.foo.Client;

            public final class Caller {
              public void doTest() {
                Client.execute2("hi %s");
              }
            }
            """)
        .doTest();
  }

  // b/308614050
  @Test
  public void paramCast() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.foo;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(
                  replacement = "Client.after(value.doubleValue())",
                  imports = {"com.google.foo.Client"})
              public static void before(Long value) {
                after(value.doubleValue());
              }

              public static void after(double value) {
                // do nothing
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import com.google.foo.Client;

            public final class Caller {
              public void doTest() {
                Object value = 42L;
                Client.before((Long) value);
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            "import com.google.foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Object value = 42L;",
            "    Client.after(((Long) value).doubleValue());",
            "  }",
            "}")
        .allowBreakingChanges()
        .doTest();
  }

  // b/308614050
  @Test
  public void replacementWhichRequiresParens() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.foo;
            import com.google.errorprone.annotations.InlineMe;
            public final class Client {
              @InlineMe(replacement = "x * 2")
              public static int timesTwo(int x) {
                return x * 2;
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import com.google.foo.Client;
            public final class Caller {
              public void doTest() {
                long four = Client.timesTwo(1 + 1);
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            """
            import com.google.foo.Client;
            public final class Caller {
              public void doTest() {
                long four = (1 + 1) * 2;
              }
            }
            """)
        .doTest();
  }

  // b/365094947

  // b/375421323
  @Test
  public void inlinerReplacesParameterValueInPackageName() {
    refactoringTestHelper
        .addInputLines(
            "Bar.java",
            """
            package foo;

            public class Bar {
              public static void baz(String s) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;
            import foo.Bar;

            public class Client {
              @InlineMe(replacement = "Bar.baz(foo)", imports = "foo.Bar")
              public static void inlinedMethod(String foo) {
                Bar.baz(foo);
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            class Caller {
              class Bar {}

              void doTest() {
                Client.inlinedMethod("abc");
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            class Caller {
              class Bar {}

              void doTest() {
                "abc".Bar.baz("abc");
              }
            }
            """)
        .allowBreakingChanges()
        .doTest();
  }

  @Test
  public void methodReference() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package p;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @Deprecated
              @InlineMe(replacement = "this.instanceAfter()")
              public void instanceBefore() {
                instanceAfter();
              }

              public void instanceAfter() {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            import java.util.function.Consumer;
            import p.Client;

            public final class Caller {
              public void doTest() {
                Client client = new Client();
                Consumer<Client> c;
                Runnable r;
                r = client::instanceBefore;
                c = Client::instanceBefore;
              }
            }
            """)
        .addOutputLines(
            "out/Caller.java",
            """
            import java.util.function.Consumer;
            import p.Client;
            public final class Caller {
              public void doTest() {
                Client client = new Client();
                Consumer<Client> c;
                Runnable r;
                r = client::instanceAfter;
                c = Client::instanceAfter;

              }
            }
            """)
        .doTest(TEXT_MATCH);
  }

  // b/399499673
  @Test
  public void variableNamesInSubstitutionCollidesWithParameterName() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.foo;

            import com.google.common.collect.ImmutableList;
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(
                  replacement = "new Client(a, b)",
                  imports = {"com.google.foo.Client"})
              @Deprecated
              public static Client create(String a, ImmutableList<String> b) {
                return new Client(a, b);
              }

              public Client(String a, ImmutableList<String> b) {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            """
            package com.google.foo;

            import com.google.common.collect.ImmutableList;

            public final class Caller {
              public void doTest() {
                ImmutableList<String> b = ImmutableList.of("foo", "bar");
                Client client = Client.create(b.get(0), b.size() == 1 ? ImmutableList.of() : b);
              }
            }
            """)
        .addOutputLines(
            "Caller.java",
            """
            package com.google.foo;

            import com.google.common.collect.ImmutableList;

            public final class Caller {
              public void doTest() {
                ImmutableList<String> b = ImmutableList.of("foo", "bar");
                Client client =
                    new Client(b.get(0), b.size() == 1 ? ImmutableList.of() : b);
              }
            }
            """)
        .doTest();
  }

  private BugCheckerRefactoringTestHelper bugCheckerWithPrefixFlag(String prefix) {
    return BugCheckerRefactoringTestHelper.newInstance(Inliner.class, getClass())
        .setArgs("-XepOpt:" + PREFIX_FLAG + "=" + prefix);
  }

  private BugCheckerRefactoringTestHelper bugCheckerWithCheckFixCompiles() {
    return BugCheckerRefactoringTestHelper.newInstance(Inliner.class, getClass())
        .setArgs("-XepOpt:InlineMe:CheckFixCompiles=true");
  }

  // b/308614050
  @Test
  public void binaryTree_immediatelyInvoked_requiresParens() {
    refactoringTestHelper
        .addInputLines(
            "Strings.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Strings {
              @InlineMe(replacement = "string.repeat(count)")
              public static String repeat(String string, int count) {
                return string.repeat(count);
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              void test() {
                String s = Strings.repeat("a" + "b", 10);
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              void test() {
                String s = ("a" + "b").repeat(10);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void variousInlinings_doesNotAddParensWithinMethodCall() {
    refactoringTestHelper
        .addInputLines(
            "Strings.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Strings {
              @InlineMe(replacement = "String.format(\\"%s%s%s\\", x, y, z)")
              public static String f(String x, String y, String z) {
                return String.format("%s%s%s", x, y, z);
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              void test() {
                String s = Strings.f("a" + "b", "c" + "d", "e" + "f");
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            class Test {
              void test() {
                String s = String.format("%s%s%s", "a" + "b", "c" + "d", "e" + "f");
              }
            }
            """)
        .doTest();
  }

  // b/400398218
  @Test
  public void inlinedCodeRequiresParens() {
    refactoringTestHelper
        .addInputLines(
            "I.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class I {
              @InlineMe(replacement = "foo + \\"b\\"")
              public static String ab(String foo) {
                return foo + "b";
              }
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            class Test {
              void test(String x) {
                String abn = I.ab(x).repeat(10);
              }
            }
            """)
        // Broken! The inlined code requires parens.
        .addOutputLines(
            "Test.java",
            """
            class Test {
              void test(String x) {
                String abn = x + "b".repeat(10);
              }
            }
            """)
        .doTest();
  }
}
