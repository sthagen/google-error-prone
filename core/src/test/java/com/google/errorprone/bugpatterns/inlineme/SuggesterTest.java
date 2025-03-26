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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link Suggester}. */
@RunWith(JUnit4.class)
public class SuggesterTest {
  private final BugCheckerRefactoringTestHelper refactoringTestHelper =
      BugCheckerRefactoringTestHelper.newInstance(Suggester.class, getClass());

  @Test
  public void buildAnnotation_withImports() {
    assertThat(
            InlineMeData.buildAnnotation(
                "REPLACEMENT",
                ImmutableSet.of("java.time.Duration", "java.time.Instant"),
                ImmutableSet.of()))
        .isEqualTo(
            "@InlineMe(replacement = \"REPLACEMENT\", "
                + "imports = {\"java.time.Duration\", \"java.time.Instant\"})\n");
  }

  @Test
  public void buildAnnotation_withSingleImport() {
    assertThat(
            InlineMeData.buildAnnotation(
                "REPLACEMENT", ImmutableSet.of("java.time.Duration"), ImmutableSet.of()))
        .isEqualTo(
            "@InlineMe(replacement = \"REPLACEMENT\", " + "imports = \"java.time.Duration\")\n");
  }

  @Test
  public void instanceMethodNewImport() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              private Duration deadline = Duration.ofSeconds(5);

              @Deprecated
              public void setDeadline(long millis) {
                setDeadline(Duration.ofMillis(millis));
              }

              public void setDeadline(Duration deadline) {
                this.deadline = deadline;
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              private Duration deadline = Duration.ofSeconds(5);

              @InlineMe(
                  replacement = "this.setDeadline(Duration.ofMillis(millis))",
                  imports = "java.time.Duration")
              @Deprecated
              public void setDeadline(long millis) {
                setDeadline(Duration.ofMillis(millis));
              }

              public void setDeadline(Duration deadline) {
                this.deadline = deadline;
              }
            }

            """)
        .doTest();
  }

  @Test
  public void staticMethodInNewClass() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              @Deprecated
              public Duration fromMillis(long millis) {
                return Duration.ofMillis(millis);
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              @InlineMe(replacement = "Duration.ofMillis(millis)", imports = "java.time.Duration")
              @Deprecated
              public Duration fromMillis(long millis) {
                return Duration.ofMillis(millis);
              }
            }

            """)
        .doTest();
  }

  @Test
  public void unqualifiedStaticFieldReference() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              public static final String STR = "kurt";

              @Deprecated
              public int stringLength() {
                return STR.length();
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            "package com.google.frobber;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  public static final String STR = \"kurt\";",
            // TODO(b/234643232): this is a bug; it should be "Client.STR.length()" plus an import
            "  @InlineMe(replacement = \"STR.length()\")",
            "  @Deprecated",
            "  public int stringLength() {",
            "    return STR.length();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void qualifiedStaticFieldReference() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              public static final String STR = "kurt";

              @Deprecated
              public int stringLength() {
                return Client.STR.length();
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              public static final String STR = "kurt";

              @InlineMe(replacement = "Client.STR.length()", imports = "com.google.frobber.Client")
              @Deprecated
              public int stringLength() {
                return Client.STR.length();
              }
            }

            """)
        .doTest();
  }

  @Test
  public void protectedConstructor() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              protected Client() {}
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void returnField() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              @Deprecated
              public Duration getZero() {
                return Duration.ZERO;
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client {
              @InlineMe(replacement = "Duration.ZERO", imports = "java.time.Duration")
              @Deprecated
              public Duration getZero() {
                return Duration.ZERO;
              }
            }

            """)
        .doTest();
  }

  @Test
  public void implementationSplitOverMultipleLines() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;
            import java.time.Instant;

            public final class Client {
              @Deprecated
              public Duration getElapsed() {
                return Duration.between(Instant.ofEpochMilli(42), Instant.now());
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;
            import java.time.Instant;

            public final class Client {
              @InlineMe(
                  replacement = "Duration.between(Instant.ofEpochMilli(42), Instant.now())",
                  imports = {"java.time.Duration", "java.time.Instant"})
              @Deprecated
              public Duration getElapsed() {
                return Duration.between(Instant.ofEpochMilli(42), Instant.now());
              }
            }

            """)
        .doTest();
  }

  @Test
  public void anonymousClass() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              public Object getUselessObject() {
                return new Object() {
                  @Override
                  public int hashCode() {
                    return 42;
                  }
                };
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void methodReference() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;
            import java.util.Optional;

            public final class Client {
              @Deprecated
              public Optional<Duration> silly(Optional<Long> input) {
                return input.map(Duration::ofMillis);
              }
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;
import java.time.Duration;
import java.util.Optional;

public final class Client {
  @InlineMe(replacement = "input.map(Duration::ofMillis)", imports = "java.time.Duration")
  @Deprecated
  public Optional<Duration> silly(Optional<Long> input) {
    return input.map(Duration::ofMillis);
  }
}

""")
        .doTest();
  }

  @Test
  public void newClass() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import org.joda.time.Instant;

            public final class Client {
              @Deprecated
              public Instant silly() {
                return new Instant();
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import org.joda.time.Instant;

            public final class Client {
              @InlineMe(replacement = "new Instant()", imports = "org.joda.time.Instant")
              @Deprecated
              public Instant silly() {
                return new Instant();
              }
            }

            """)
        .doTest();
  }

  @Test
  public void newArray() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import org.joda.time.Instant;

            public final class Client {
              @Deprecated
              public Instant[] silly() {
                return new Instant[42];
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import org.joda.time.Instant;

            public final class Client {
              @InlineMe(replacement = "new Instant[42]", imports = "org.joda.time.Instant")
              @Deprecated
              public Instant[] silly() {
                return new Instant[42];
              }
            }

            """)
        .doTest();
  }

  @Test
  public void newNestedClass() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              public NestedClass silly() {
                return new NestedClass();
              }

              public static class NestedClass {}
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;

public final class Client {
  @InlineMe(replacement = "new NestedClass()", imports = "com.google.frobber.Client.NestedClass")
  @Deprecated
  public NestedClass silly() {
    return new NestedClass();
  }

  public static class NestedClass {}
}

""")
        .doTest();
  }

  @Test
  public void returnStringLiteral() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              public String getName() {
                return "kurt";
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(replacement = "\\"kurt\\"")
              @Deprecated
              public String getName() {
                return "kurt";
              }
            }

            """)
        .doTest();
  }

  @Test
  public void callMethodWithStringLiteral() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              public String getName() {
                return getName("kurt");
              }

              public String getName(String defaultValue) {
                return "test";
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(replacement = "this.getName(\\"kurt\\")")
              @Deprecated
              public String getName() {
                return getName("kurt");
              }

              public String getName(String defaultValue) {
                return "test";
              }
            }

            """)
        .doTest();
  }

  @Test
  public void returnPrivateVariable() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              private final Duration myDuration = Duration.ZERO;

              @Deprecated
              public Duration getMyDuration() {
                return myDuration;
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void returnPrivateVariable_qualifiedWithThis() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              private final Duration myDuration = Duration.ZERO;

              @Deprecated
              public Duration getMyDuration() {
                return this.myDuration;
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void settingPrivateVariable() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              private Duration duration = Duration.ZERO;

              @Deprecated
              public void setDuration(Duration duration) {
                this.duration = duration;
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void delegateToParentClass() {
    refactoringTestHelper
        .addInputLines(
            "Parent.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public class Parent {
              private Duration duration = Duration.ZERO;

              public final Duration after() {
                return duration;
              }
            }

            """)
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client extends Parent {
              private Duration duration = Duration.ZERO;

              @Deprecated
              public final Duration before() {
                return after();
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.time.Duration;

            public final class Client extends Parent {
              private Duration duration = Duration.ZERO;

              @InlineMe(replacement = "this.after()")
              @Deprecated
              public final Duration before() {
                return after();
              }
            }

            """)
        .doTest();
  }

  @Test
  public void withCast() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              @Deprecated
              public void setDuration(Object duration) {
                foo((Duration) duration);
              }

              public void foo(Duration duration) {}
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;
import java.time.Duration;

public final class Client {
  @InlineMe(replacement = "this.foo((Duration) duration)", imports = "java.time.Duration")
  @Deprecated
  public void setDuration(Object duration) {
    foo((Duration) duration);
  }

  public void foo(Duration duration) {}
}

""")
        .doTest();
  }

  @Test
  public void accessPrivateVariable() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              private final Duration myDuration = Duration.ZERO;

              @Deprecated
              public boolean silly() {
                return myDuration.isZero();
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void accessPrivateMethod() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public final class Client {
              @Deprecated
              public boolean silly() {
                return privateDelegate();
              }

              private boolean privateDelegate() {
                return false;
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void tryWithResources() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            import java.io.BufferedReader;
            import java.io.FileReader;
            import java.io.IOException;

            public class Client {
              @Deprecated
              public String readLine(String path) throws IOException {
                try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                  return br.readLine();
                }
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void ifStatement() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            public class Client {
              @Deprecated
              public void foo(String input) {
                if (input.equals("hi")) {
                  return;
                }
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void nestedBlock() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            public class Client {
              @Deprecated
              public String foo(String input) {
                {
                  return input.toLowerCase();
                }
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void ternaryOverMultipleLines() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
"""
package com.google.frobber;

import java.time.Duration;

public final class Client {
  @Deprecated
  public Duration getDeadline(Duration deadline) {
    return (deadline.compareTo(Duration.ZERO) > 0 ? Duration.ofSeconds(42) : Duration.ZERO);
  }
}

""")
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;
import java.time.Duration;

public final class Client {
  @InlineMe(
      replacement =
          "(deadline.compareTo(Duration.ZERO) > 0 ? Duration.ofSeconds(42) : Duration.ZERO)",
      imports = "java.time.Duration")
  @Deprecated
  public Duration getDeadline(Duration deadline) {
    return (deadline.compareTo(Duration.ZERO) > 0 ? Duration.ofSeconds(42) : Duration.ZERO);
  }
}

""")
        .doTest();
  }

  @Test
  public void staticCallingAnotherQualifiedStatic() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.time.Duration;

            public final class Client {
              @Deprecated
              public static Duration getDeadline() {
                return Client.getDeadline2();
              }

              public static Duration getDeadline2() {
                return Duration.ZERO;
              }
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;
import java.time.Duration;

public final class Client {
  @InlineMe(replacement = "Client.getDeadline2()", imports = "com.google.frobber.Client")
  @Deprecated
  public static Duration getDeadline() {
    return Client.getDeadline2();
  }

  public static Duration getDeadline2() {
    return Duration.ZERO;
  }
}

""")
        .doTest();
  }

  @Test
  public void staticReferenceToJavaLang() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import static java.lang.String.format;

            public final class Client {
              @Deprecated
              public static String myFormat(String template, String arg) {
                return format(template, arg);
              }
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import static java.lang.String.format;
import com.google.errorprone.annotations.InlineMe;

public final class Client {
  @InlineMe(replacement = "format(template, arg)", staticImports = "java.lang.String.format")
  @Deprecated
  public static String myFormat(String template, String arg) {
    return format(template, arg);
  }
}

""")
        .doTest();
  }

  @Test
  public void replacementContainsGenericInvocation() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.util.ArrayList;
            import java.util.List;

            public final class Client {
              @Deprecated
              public static List<Void> newArrayList() {
                return new ArrayList<Void>();
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;
            import java.util.ArrayList;
            import java.util.List;

            public final class Client {
              @InlineMe(replacement = "new ArrayList<Void>()", imports = "java.util.ArrayList")
              @Deprecated
              public static List<Void> newArrayList() {
                return new ArrayList<Void>();
              }
            }

            """)
        .doTest();
  }

  @Test
  public void suggestedFinalOnOtherwiseGoodMethod() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public class Client {
              @Deprecated
              public int method() {
                return 42;
              }
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;

            public class Client {
              @InlineMe(replacement = "42")
              @Deprecated
              public final int method() {
                return 42;
              }
            }

            """)
        .doTest();
  }

  @Test
  public void dontSuggestOnDefaultMethods() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public interface Client {
              @Deprecated
              public default int method() {
                return 42;
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  // Since constructors can't be "overridden" in the same way as other non-final methods, it's
  // OK to inline them even if there could be a subclass of the surrounding class.
  @Test
  public void deprecatedConstructorInNonFinalClass() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public class Client {
              @Deprecated
              public Client() {
                this(42);
              }

              public Client(int value) {}
            }

            """)
        .addOutputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.InlineMe;

            public class Client {
              @InlineMe(replacement = "this(42)")
              @Deprecated
              public Client() {
                this(42);
              }

              public Client(int value) {}
            }

            """)
        .doTest();
  }

  @Test
  public void publicStaticFactoryCallsPrivateConstructor() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            public class Client {
              @Deprecated
              public static Client create() {
                return new Client();
              }

              private Client() {}
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void deprecatedMethodWithDoNotCall() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import com.google.errorprone.annotations.DoNotCall;

            public class Client {
              @DoNotCall
              @Deprecated
              public void before() {
                after();
              }

              public void after() {}
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void implementationUsingPublicStaticField() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            package com.google.frobber;

            import java.util.function.Supplier;

            public class Client {
              public static final Supplier<Integer> MAGIC = () -> 42;

              @Deprecated
              public static int before() {
                return after(MAGIC.get());
              }

              public static int after(int value) {
                return value;
              }
            }

            """)
        .addOutputLines(
            "Client.java",
"""
package com.google.frobber;

import com.google.errorprone.annotations.InlineMe;
import java.util.function.Supplier;

public class Client {
  public static final Supplier<Integer> MAGIC = () -> 42;

  @InlineMe(replacement = "Client.after(MAGIC.get())", imports = "com.google.frobber.Client")
  @Deprecated
  public static int before() {
    return after(MAGIC.get());
  }

  public static int after(int value) {
    return value;
  }
}

""")
        .doTest();
  }

  @Test
  public void apisLikelyUsedReflectively() {
    refactoringTestHelper
        .addInputLines(
            "Test.java",
            """
            import com.google.errorprone.annotations.Keep;
            import com.google.inject.Provides;
            import java.time.Duration;
            import java.util.Optional;

            public class Test {
              @Deprecated
              @Provides
              public Optional<Duration> provides(Optional<Long> input) {
                return input.map(Duration::ofMillis);
              }

              @Deprecated
              @Keep
              public Optional<Duration> reflective(Optional<Long> input) {
                return input.map(Duration::ofMillis);
              }
            }

            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void importStatic_getsCorrectlySuggestedAsStaticImports() {
    refactoringTestHelper
        .addInputLines(
            "KeymasterEncrypter.java",
            """
            package com.google.security.keymaster;

            import static java.nio.charset.StandardCharsets.US_ASCII;
            import com.google.errorprone.annotations.InlineMe;

            public final class KeymasterEncrypter {
              @Deprecated
              public final byte[] encryptASCII(String plaintext) {
                return encrypt(plaintext.getBytes(US_ASCII));
              }

              public byte[] encrypt(byte[] plaintext) {
                return plaintext;
              }
            }

            """)
        .addOutputLines(
            "KeymasterEncrypter.java",
            """
            package com.google.security.keymaster;

            import static java.nio.charset.StandardCharsets.US_ASCII;
            import com.google.errorprone.annotations.InlineMe;

            public final class KeymasterEncrypter {
              @InlineMe(
                  replacement = "this.encrypt(plaintext.getBytes(US_ASCII))",
                  staticImports = "java.nio.charset.StandardCharsets.US_ASCII")
              @Deprecated
              public final byte[] encryptASCII(String plaintext) {
                return encrypt(plaintext.getBytes(US_ASCII));
              }

              public byte[] encrypt(byte[] plaintext) {
                return plaintext;
              }
            }

            """)
        .doTest();
  }

  @Test
  public void importStatic_getsIncorrectlySuggestedAsImportsInsteadOfStaticImports() {
    refactoringTestHelper
        .addInputLines(
            "KeymasterCrypter.java",
            """
            package com.google.security.keymaster;

            import static java.nio.charset.StandardCharsets.US_ASCII;
            import com.google.errorprone.annotations.InlineMe;

            public final class KeymasterCrypter {
              @Deprecated
              public final String decryptASCII(byte[] ciphertext) {
                return new String(decrypt(ciphertext), US_ASCII);
              }

              public byte[] decrypt(byte[] ciphertext) {
                return ciphertext;
              }
            }

            """)
        .addOutputLines(
            "KeymasterCrypter.java",
            "package com.google.security.keymaster;",
            "import static java.nio.charset.StandardCharsets.US_ASCII;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class KeymasterCrypter {",
            // TODO(b/242890437): This line is wrong:
            "  @InlineMe(replacement = \"new String(this.decrypt(ciphertext), US_ASCII)\", imports"
                + " = \"US_ASCII\")",
            // It should be this instead:
            // "  @InlineMe(",
            // "      replacement = \"new String(this.decrypt(ciphertext), US_ASCII)\",",
            // "      staticImports =\"java.nio.charset.StandardCharsets.US_ASCII\")",
            "  @Deprecated",
            "  public final String decryptASCII(byte[] ciphertext) {",
            "    return new String(decrypt(ciphertext), US_ASCII);",
            "  }",
            "  public byte[] decrypt(byte[] ciphertext) {",
            "    return ciphertext;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void noInlineMeSuggestionWhenParameterNamesAreArgN() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            public final class Client {
              @Deprecated
              public void before(int arg0, int arg1) {
                after(arg0, arg1);
              }

              public void after(int arg0, int arg1) {}
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void inlineMeSuggestionWhenParameterNamesAreNotArgN() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            """
            public final class Client {
              @Deprecated
              public void before(int int0, int int1) {
                after(int0, int1);
              }

              public void after(int int0, int int1) {}
            }
            """)
        .addOutputLines(
            "Client.java",
            """
            import com.google.errorprone.annotations.InlineMe;

            public final class Client {
              @InlineMe(replacement = "this.after(int0, int1)")
              @Deprecated
              public void before(int int0, int int1) {
                after(int0, int1);
              }

              public void after(int int0, int int1) {}
            }
            """)
        .doTest();
  }

  @Test
  public void deprecatedConstructorSuperCall() {
    refactoringTestHelper
        .addInputLines(
            "ExecutionError.java",
            """
            public final class ExecutionError extends Error {
              @Deprecated
              public ExecutionError(String message) {
                super(message);
              }
            }
            """)
        .expectUnchanged()
        .doTest();
  }
}
