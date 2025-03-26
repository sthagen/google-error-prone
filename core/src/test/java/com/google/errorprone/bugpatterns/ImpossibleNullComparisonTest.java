/*
 * Copyright 2013 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.errorprone.bugpatterns;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author flx@google.com (Felix Berger)
 */
@RunWith(JUnit4.class)
@Ignore("b/130670448")
public final class ImpossibleNullComparisonTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ImpossibleNullComparison.class, getClass());

  private final BugCheckerRefactoringTestHelper refactoringHelper =
      BugCheckerRefactoringTestHelper.newInstance(ImpossibleNullComparison.class, getClass());

  @Test
  public void scalarCases() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                // BUG: Diagnostic contains: message.hasMessage()
                if (message.getMessage() != null) {}
                // BUG: Diagnostic contains: !message.hasMessage()
                if (message.getMessage() == null) {}
                // BUG: Diagnostic contains: message.hasMessage()
                if (null != message.getMessage()) {}
                // BUG: Diagnostic contains: message.getMessage().hasField()
                if (message.getMessage().getField() != null) {}
              }
            }
            """)
        .doTest();
  }

  @Test
  public void listCases() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                TestFieldProtoMessage field = message.getMessage();
                // BUG: Diagnostic contains: !message.getMultiFieldList().isEmpty()
                if (message.getMultiFieldList() != null) {}
                // BUG: Diagnostic contains: message.getMultiFieldList().isEmpty()
                if (null == message.getMultiFieldList()) {}
                // BUG: Diagnostic contains: message.getMultiFieldCount() > 1
                if (message.getMultiField(1) != null) {}
                // BUG: Diagnostic contains: message.getMultiFieldCount() <= 1
                if (message.getMultiField(1) == null) {}
              }
            }
            """)
        .doTest();
  }

  @Test
  public void intermediateVariable() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import java.util.List;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                TestFieldProtoMessage field = message.getMessage();
                List<TestFieldProtoMessage> fields = message.getMultiFieldList();
                // BUG: Diagnostic contains: message.hasMessage()
                if (field != null) {}
                // BUG: Diagnostic contains: !message.getMultiFieldList().isEmpty()
                if (fields != null) {}
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCases() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            class Test {
              public void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                Object object = new Object();
                if (message.getMessage() != object) {}
                if (object != message.getMessage()) {}
                if (message.getMessage().getField() != object) {}
                if (message.getMultiFieldList() != object) {}
                if (object == message.getMultiFieldList()) {}
              }
            }
            """)
        .doTest();
  }

  @Test
  public void proto3() {
    compilationHelper
        .addSourceLines(
            "TestProto3.java",
            """
            import com.google.errorprone.bugpatterns.proto.Proto3Test.TestProto3Message;

            public class TestProto3 {
              public boolean doIt(TestProto3Message proto3Message) {
                // BUG: Diagnostic matches: NO_FIX
                return proto3Message.getMyString() == null;
              }
            }
            """)
        .expectErrorMessage("NO_FIX", input -> !input.contains("hasMyString()"))
        .doTest();
  }

  @Test
  public void messageOrBuilderGetField() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.Descriptors.FieldDescriptor;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            public class Test {
              public boolean doIt(TestProtoMessage mob, FieldDescriptor f) {
                // BUG: Diagnostic contains:
                return mob.getField(f) == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void messageOrBuilderGetFieldCast() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.Descriptors.FieldDescriptor;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            public class Test {
              public boolean doIt(TestProtoMessage mob, FieldDescriptor f) {
                String s = ((String) mob.getField(f));
                // BUG: Diagnostic contains:
                return s == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void extendableMessageGetExtension1param() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static org.junit.Assert.assertNotNull;
            import com.google.protobuf.ExtensionLite;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            public class Test {
              public void test(TestProtoMessage e, ExtensionLite extensionLite) {
                // BUG: Diagnostic contains:
                boolean a = e.getExtension(extensionLite) == null;
                // BUG: Diagnostic contains:
                assertNotNull(e.getExtension(extensionLite));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void messageOrBuilderGetRepeatedField() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.Descriptors.FieldDescriptor;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            public class Test {
              public void doIt(TestProtoMessage mob, FieldDescriptor f) {
                // BUG: Diagnostic contains:
                boolean a = mob.getRepeatedField(f, 0) == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void extendableMessageGetExtension2param() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.ExtensionLite;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            public class Test {
              public void test(TestProtoMessage e, ExtensionLite extensionLite) {
                // BUG: Diagnostic contains:
                boolean a = e.getExtension(extensionLite, 0) == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void repeated() {
    compilationHelper
        .addSourceLines(
            "Test.java",
"""
import com.google.protobuf.ExtensionLite;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
import java.util.List;

public class Test {
  public void test(
      ExtensionLite<TestProtoMessage, List<TestFieldProtoMessage>> e, TestProtoMessage message) {
    // BUG: Diagnostic contains:
    boolean y = message.getExtension(e) == null;
  }
}
""")
        .doTest();
  }

  @Test
  public void repeated2() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.Extension.MessageType;
            import com.google.protobuf.ExtensionLite;
            import com.google.protobuf.GeneratedMessage;
            import com.sun.tools.javac.code.Type;
            import com.google.common.collect.ImmutableList;
            import java.util.List;

            public class Test {
              public static <
                      MessageType extends GeneratedMessage.ExtendableMessage<MessageType>,
                      Type extends GeneratedMessage>
                  List<Type> getRepeatedExtensionObjects(
                      GeneratedMessage.ExtendableMessage<MessageType> mob,
                      ExtensionLite<MessageType, List<Type>> extension) {
                ImmutableList.Builder extensionList = ImmutableList.builder();
                int extensionCount = mob.getExtensionCount(extension);
                for (int extensionIndex = 0; extensionIndex < extensionCount; ++extensionIndex) {
                  // BUG: Diagnostic contains:
                  boolean y = mob.getExtension(extension) == null;
                  extensionList.add(mob.getExtension(extension));
                }
                return extensionList.build();
              }
            }
            """)
        .addModules("jdk.compiler/com.sun.tools.javac.code")
        .doTest();
  }

  @Test
  public void preconditions() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.base.Preconditions.checkNotNull;
            import static com.google.common.base.Verify.verifyNotNull;
            import static java.util.Objects.requireNonNull;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import java.util.List;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                TestFieldProtoMessage field = message.getMessage();
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(field);
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMessage());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                verifyNotNull(message.getMessage());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMultiFieldList());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMessage(), new Object());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMultiFieldList(), new Object());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMessage(), "%s", new Object());
                // BUG: Diagnostic contains: This value cannot be null
                // remove this line
                checkNotNull(message.getMultiFieldList(), "%s", new Object());
                // BUG: Diagnostic contains: fieldMessage = message.getMessage();
                TestFieldProtoMessage fieldMessage = checkNotNull(message.getMessage());
                // BUG: Diagnostic contains: fieldMessage2 = message.getMessage()
                TestFieldProtoMessage fieldMessage2 = checkNotNull(message.getMessage(), "M");
                // BUG: Diagnostic contains: message.getMessage().toString();
                checkNotNull(message.getMessage()).toString();
                // BUG: Diagnostic contains: message.getMessage().toString();
                checkNotNull(message.getMessage(), "Message").toString();
                // BUG: Diagnostic contains: TestFieldProtoMessage fieldCopy = field;
                TestFieldProtoMessage fieldCopy = requireNonNull(field);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void assertions() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import static org.junit.Assert.assertNotNull;
            import com.google.common.truth.extensions.proto.ProtoTruth;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import java.util.List;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                TestFieldProtoMessage field = message.getMessage();
                // BUG: Diagnostic contains: assertTrue("Message", message.hasMessage());
                assertNotNull("Message", message.getMessage());
                // BUG: Diagnostic contains: assertThat(message.hasMessage()).isTrue()
                assertThat(message.getMessage()).isNotNull();
                // BUG: Diagnostic contains: assertThat(message.hasMessage()).isTrue()
                ProtoTruth.assertThat(message.getMessage()).isNotNull();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void assertions_negative() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import static org.junit.Assert.assertNotNull;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import java.util.List;

            class Test {
              void test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                TestFieldProtoMessage field = message.getMessage();
                assertNotNull("Message", message.getMessage());
                assertThat(message.getMessage()).isNotNull();
              }
            }
            """)
        .setArgs(ImmutableList.of("-XepOpt:ProtoFieldNullComparison:MatchTestAssertions=false"))
        .doTest();
  }

  @Test
  public void optional() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import java.util.Optional;

            class Test {
              Optional<?> test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                // BUG: Diagnostic contains: Optional.of(message.getMessage())
                return Optional.ofNullable(message.getMessage());
              }
            }
            """)
        .doTest();
  }

  @Test
  public void guavaOptional() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;
            import java.util.Optional;

            class Test {
              Optional<?> test() {
                TestProtoMessage message = TestProtoMessage.newBuilder().build();
                // BUG: Diagnostic contains: Optional.of(message.getMessage())
                return Optional.ofNullable(message.getMessage());
              }
            }
            """)
        .doTest();
  }

  @Test
  public void optionalGet() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.Optional;

            public class Test {
              public boolean o(Optional<String> o) {
                // BUG: Diagnostic contains: o.isEmpty()
                return o.get() == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void optionalGetSwitched() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import java.util.Optional;

            public class Test {
              public boolean o(Optional<String> o) {
                return switch (o.get()) {
                  case null -> true;
                  case "" -> false;
                  default -> false;
                };
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.Optional;

            public class Test {
              public boolean o(Optional<String> o) {
                return switch (o.get()) {
                  case "" -> false;
                  default -> false;
                };
              }
            }
            """)
        .doTest();
  }

  @Test
  public void optionalGetSwitched_noNullCheck() {
    refactoringHelper
        .addInputLines(
            "Test.java",
            """
            import java.util.Optional;

            public class Test {
              public boolean o(Optional<String> o) {
                return switch (o.get()) {
                  case "" -> false;
                  default -> false;
                };
              }
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void guavaOptionalGet() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.base.Optional;

            public class Test {
              public boolean o(Optional<String> o) {
                // BUG: Diagnostic contains: !o.isPresent()
                return o.get() == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void multimapGet() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.collect.Multimap;

            public class Test {
              public boolean o(Multimap<String, String> m) {
                // BUG: Diagnostic contains: !m.containsKey("foo")
                return m.get("foo") == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void listMultimapGet() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.collect.ListMultimap;

            public class Test {
              public boolean o(ListMultimap<String, String> m) {
                // BUG: Diagnostic contains: !m.containsKey("foo")
                return m.get("foo") == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void tables() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.common.collect.Table;

            public class Test {
              public void o(Table<String, String, String> t) {
                // BUG: Diagnostic contains: !t.containsRow("foo")
                boolean b1 = t.row("foo") == null;
                // BUG: Diagnostic contains: !t.containsColumn("foo")
                boolean b2 = t.column("foo") == null;
              }
            }
            """)
        .doTest();
  }

  @Test
  public void primitives() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void o(int i, Integer boxed) {
                // BUG: Diagnostic contains:
                assertThat(i).isNotNull();
                assertThat(boxed).isNotNull();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void valueOf() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import java.util.concurrent.TimeUnit;

            public class Test {
              public void o(String s) {
                // BUG: Diagnostic contains:
                assertThat(TimeUnit.valueOf(s)).isNotNull();
                // BUG: Diagnostic contains:
                assertThat(Integer.valueOf(s)).isNotNull();
              }
            }
            """)
        .doTest();
  }
}
