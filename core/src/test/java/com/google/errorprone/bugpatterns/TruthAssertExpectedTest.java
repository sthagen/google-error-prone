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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TruthAssertExpected} bug pattern.
 *
 * @author ghm@google.com (Graeme Morgan)
 */
@RunWith(JUnit4.class)
public final class TruthAssertExpectedTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(TruthAssertExpected.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "TruthAssertExpectedPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;

/**
 * Positive test cases for TruthAssertExpected check.
 *
 * @author ghm@google.com (Graeme Morgan)
 */
final class TruthAssertExpectedPositiveCases {
  private static final ImmutableList<Object> EXPECTED_LIST = ImmutableList.of();
  private static final float EXPECTED_FLOAT = 1f;

  private float actualFloat() {
    return 3.14f;
  }

  private void simple() {
    Object expected = new Object();
    Object actual = new Object();
    Object foo = new Object();
    // BUG: Diagnostic contains: assertThat(foo).isEqualTo(expected)
    assertThat(expected).isEqualTo(foo);
    // BUG: Diagnostic contains: assertThat(foo).isNotEqualTo(expected)
    assertThat(expected).isNotEqualTo(foo);

    // BUG: Diagnostic contains: assertWithMessage("reversed!").that(actual).isEqualTo(expected)
    assertWithMessage("reversed!").that(expected).isEqualTo(actual);

    // BUG: Diagnostic contains: assertThat(actual.hashCode()).isEqualTo(expected.hashCode())
    assertThat(expected.hashCode()).isEqualTo(actual.hashCode());
  }

  private void tolerantFloats() {
    // BUG: Diagnostic contains: assertThat(actualFloat()).isWithin(1f).of(EXPECTED_FLOAT)
    assertThat(EXPECTED_FLOAT).isWithin(1f).of(actualFloat());
  }

  private void lists() {
    // BUG: Diagnostic contains:
    // assertThat(ImmutableList.of(this)).containsExactlyElementsIn(EXPECTED_LIST);
    assertThat(EXPECTED_LIST).containsExactlyElementsIn(ImmutableList.of(this));
    // BUG: Diagnostic contains:
    // assertThat(ImmutableList.of(this)).containsExactlyElementsIn(EXPECTED_LIST).inOrder();
    assertThat(EXPECTED_LIST).containsExactlyElementsIn(ImmutableList.of(this)).inOrder();
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "TruthAssertExpectedNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import static com.google.common.truth.Truth.assertThat;

            import com.google.common.collect.ImmutableList;

            /**
             * Negative test cases for TruthAssertExpected check.
             *
             * @author ghm@google.com (Graeme Morgan)
             */
            final class TruthAssertExpectedNegativeCases {
              private static final Object expected = new Object();
              private static final Object actual = new Object();
              private static final Object foo = new Object();
              private static final long CONSTANT = 1L;

              private enum Enum {
                A,
                B;
              }

              private void simple() {
                assertThat(foo).isEqualTo(expected);

                assertThat(expected.hashCode()).isEqualTo(expected.hashCode());
                assertThat(hashCode()).isEqualTo(foo);
              }

              private void actualAndExpectedTogether(int delay) {
                int actualDelayInExpectedUnits = 1;
                assertThat(actualDelayInExpectedUnits).isEqualTo(delay);
              }

              private void expectedExceptions() {
                Exception expectedException = new Exception("Oh no.");
                assertThat(expectedException).hasMessageThat().isEqualTo("Oh no.");
                assertThat(expectedException.getClass()).isEqualTo(hashCode());
              }

              private void staticFactoryMethod() {
                assertThat(expected).isEqualTo(Long.valueOf(10L));
                assertThat(expected).isEqualTo(ImmutableList.of(1));
              }

              private void constantValues() {
                assertThat(expected).isEqualTo(Enum.A);
                assertThat(expected).isEqualTo(10L);
                assertThat(expected).isEqualTo(CONSTANT);
              }
            }\
            """)
        .doTest();
  }
}
