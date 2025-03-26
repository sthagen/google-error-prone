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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@Ignore("b/130670719")
public final class RedundantSetterCallTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(RedundantSetterCall.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "ProtoRedundantSetPositiveCases.java",
"""
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

final class ProtoRedundantSetPositiveCases {
  private static final TestFieldProtoMessage foo = TestFieldProtoMessage.getDefaultInstance();
  private static final TestFieldProtoMessage bar = TestFieldProtoMessage.getDefaultInstance();

  private void singleField() {
    TestProtoMessage twice =
        TestProtoMessage.newBuilder()
            .setMessage(foo)
            .addMultiField(bar)
            // BUG: Diagnostic contains: setMessage
            .setMessage(foo)
            .addMultiField(bar)
            .build();
    TestProtoMessage nestedField =
        TestProtoMessage.newBuilder()
            .setMessage(
                // BUG: Diagnostic contains: setField
                TestFieldProtoMessage.newBuilder().setField(foo).setField(foo))
            .addMultiField(bar)
            .build();
  }

  private void repeatedField() {
    TestProtoMessage.Builder again =
        TestProtoMessage.newBuilder()
            .setMessage(foo)
            .setMessage(foo)
            // BUG: Diagnostic contains: setMessage
            .setMessage(foo)
            .setMultiField(0, bar)
            .setMultiField(1, foo)
            // BUG: Diagnostic contains: setMultiField
            .setMultiField(1, bar);
  }
}
""")
        .doTest();
  }

  @Test
  public void singleField() {
    compilationHelper
        .addSourceLines(
            "SingleField.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class ProtoRedundantSetNegativeCases {
              private void singleField() {
                TestProtoMessage.Builder builder =
                    TestProtoMessage.newBuilder()
                        .setMessage(TestFieldProtoMessage.getDefaultInstance())
                        .addMultiField(TestFieldProtoMessage.getDefaultInstance())
                        .addMultiField(TestFieldProtoMessage.getDefaultInstance());
              }
            }
            """)
        .doTest();
  }

  @Test
  public void repeatedField() {
    compilationHelper
        .addSourceLines(
            "RepeatedField.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class ProtoRedundantSetNegativeCases {
              private void repeatedField() {
                TestProtoMessage.Builder builder =
                    TestProtoMessage.newBuilder()
                        .setMessage(TestFieldProtoMessage.getDefaultInstance())
                        .setMultiField(0, TestFieldProtoMessage.getDefaultInstance())
                        .setMultiField(1, TestFieldProtoMessage.getDefaultInstance());
              }
            }
            """)
        .doTest();
  }

  @Test
  public void complexChaining() {
    compilationHelper
        .addSourceLines(
            "ComplexChaining.java",
            """
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class ProtoRedundantSetNegativeCases {
              private void intermediateBuild() {
                TestProtoMessage message =
                    TestProtoMessage.newBuilder()
                        .setMessage(TestFieldProtoMessage.getDefaultInstance())
                        .build()
                        .toBuilder()
                        .setMessage(TestFieldProtoMessage.getDefaultInstance())
                        .build();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void fixes() {
    BugCheckerRefactoringTestHelper.newInstance(RedundantSetterCall.class, getClass())
        .addInputLines(
            "ProtoRedundantSetPositiveCases.java",
"""
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

final class ProtoRedundantSetPositiveCases {
  private static final TestFieldProtoMessage foo = TestFieldProtoMessage.getDefaultInstance();
  private static final TestFieldProtoMessage bar = TestFieldProtoMessage.getDefaultInstance();

  private void singleField() {
    TestProtoMessage twice =
        TestProtoMessage.newBuilder()
            .setMessage(foo)
            .addMultiField(bar)
            .setMessage(foo)
            .addMultiField(bar)
            .build();
  }

  private void repeatedField() {
    TestProtoMessage.Builder again =
        TestProtoMessage.newBuilder()
            .setMessage(foo)
            .setMessage(foo)
            .setMessage(foo)
            .setMultiField(0, bar)
            .setMultiField(1, foo)
            .setMultiField(1, bar);
  }
}
""")
        .addOutputLines(
            "ProtoRedundantSetExpected.java",
"""
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

final class ProtoRedundantSetPositiveCases {
  private static final TestFieldProtoMessage foo = TestFieldProtoMessage.getDefaultInstance();
  private static final TestFieldProtoMessage bar = TestFieldProtoMessage.getDefaultInstance();

  private void singleField() {
    TestProtoMessage twice =
        TestProtoMessage.newBuilder().addMultiField(bar).setMessage(foo).addMultiField(bar).build();
  }

  private void repeatedField() {
    TestProtoMessage.Builder again =
        TestProtoMessage.newBuilder()
            .setMessage(foo)
            .setMultiField(0, bar)
            .setMultiField(1, foo)
            .setMultiField(1, bar);
  }
}
""")
        .doTest(TestMode.AST_MATCH);
  }

  @Test
  public void autovalue() {
    compilationHelper
        .addSourceLines(
            "Animal.java",
            """
            import com.google.auto.value.AutoValue;

            @AutoValue
            abstract class Animal {
              abstract String name();

              static Builder builder() {
                return null;
              }

              @AutoValue.Builder
              abstract static class Builder {
                abstract Builder setName(String name);

                public Builder nonAbstractMethod(String foo) {
                  return null;
                }

                abstract Animal build();
              }
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void test() {
                // BUG: Diagnostic contains:
                Animal.builder().setName("foo").setName("bar").build();
                Animal.builder().nonAbstractMethod("foo").nonAbstractMethod("bar").build();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void builderOfWildcardType_noFindings() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import com.google.protobuf.Descriptors.FieldDescriptor;
            import com.google.protobuf.GeneratedMessage.Builder;

            class Test {
              public void clear(final Builder<?> builder, FieldDescriptor fieldDescriptor) {
                builder.clearField(fieldDescriptor);
              }
            }
            """)
        .doTest();
  }
}
