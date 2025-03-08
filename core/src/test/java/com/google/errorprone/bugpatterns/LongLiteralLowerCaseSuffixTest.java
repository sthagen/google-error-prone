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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for {@link LongLiteralLowerCaseSuffix}.
 *
 * @author Simon Nickerson (sjnickerson@google.com)
 */
@RunWith(JUnit4.class)
public class LongLiteralLowerCaseSuffixTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(LongLiteralLowerCaseSuffix.class, getClass());

  @Test
  @SuppressWarnings("MisformattedTestData")
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "LongLiteralLowerCaseSuffixPositiveCase1.java",
            """
package com.google.errorprone.bugpatterns.testdata;

/** Positive cases for {@link LongLiteralLowerCaseSuffix}. */
public class LongLiteralLowerCaseSuffixPositiveCase1 {

  // This constant string includes non-ASCII characters to make sure that we're not confusing
  // bytes and chars:
  @SuppressWarnings("unused")
  private static final String TEST_STRING = "Îñţérñåţîöñåļîžåţîờñ";

  public void positiveLowerCase() {
    // BUG: Diagnostic contains: value = 123432L
    long value = 123432l;
  }

  public void zeroLowerCase() {
    // BUG: Diagnostic contains: value = 0L
    long value = 0l;
  }

  public void negativeLowerCase() {
    // BUG: Diagnostic contains: value = -123432L
    long value = -123432l;
  }

  public void negativeExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  123432L
    long value = -  123432l;
  }

  public void positiveHexLowerCase() {
    // BUG: Diagnostic contains: value = 0x8abcDEF0L
    long value = 0x8abcDEF0l;
    // BUG: Diagnostic contains: value = 0X80L
    value = 0X80l;
  }

  public void zeroHexLowerCase() {
    // BUG: Diagnostic contains: value = 0x0L
    long value = 0x0l;
    // BUG: Diagnostic contains: value = 0X0L
    value = 0X0l;
  }

  public void negativeHexLowerCase() {
    // BUG: Diagnostic contains: value = -0x8abcDEF0L
    long value = -0x8abcDEF0l;
    // BUG: Diagnostic contains: value = -0X80L
    value = -0X80l;
  }

  public void negativeHexExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  0x8abcDEF0L
    long value = -  0x8abcDEF0l;
  }

  public void positiveOctalLowerCase() {
    // BUG: Diagnostic contains: value = 06543L
    long value = 06543l;
  }

  public void zeroOctalLowerCase() {
    // BUG: Diagnostic contains: value = 00L
    long value = 00l;
  }

  public void negativeOctalLowerCase() {
    // BUG: Diagnostic contains: value = -06543L
    long value = -06543l;
  }

  public void negativeOctalExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  06543L
    long value = -  06543l;
  }
}
""")
        .doTest();
  }

  /** Test for Java 7 integer literals that include underscores. */
  @Test
  public void java7PositiveCase() {
    compilationHelper
        .addSourceLines(
            "LongLiteralLowerCaseSuffixPositiveCase2.java",
            """
package com.google.errorprone.bugpatterns.testdata;

/** Positive cases for {@link LongLiteralLowerCaseSuffix}. */
public class LongLiteralLowerCaseSuffixPositiveCase2 {

  // This constant string includes non-ASCII characters to make sure that we're not confusing
  // bytes and chars:
  @SuppressWarnings("unused")
  private static final String TEST_STRING = "Îñţérñåţîöñåļîžåţîờñ";

  public void underscoredLowerCase() {
    // BUG: Diagnostic contains: value = 0_1__2L
    long value = 0_1__2l;
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "LongLiteralLowerCaseSuffixNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * Negative cases for {@link LongLiteralLowerCaseSuffix}
             *
             * @author Simon Nickerson (sjnickerson@google.com)
             */
            public class LongLiteralLowerCaseSuffixNegativeCases {
              public void positiveUpperCase() {
                long value = 123432L;
              }

              public void zeroUpperCase() {
                long value = 0L;
              }

              public void negativeUpperCase() {
                long value = -3L;
              }

              public void notLong() {
                String value = "0l";
              }

              public void variableEndingInEllIsNotALongLiteral() {
                long ell = 0L;
                long value = ell;
              }

              public void positiveNoSuffix() {
                long value = 3;
              }

              public void negativeNoSuffix() {
                long value = -3;
              }

              public void positiveHexUpperCase() {
                long value = 0x80L;
              }

              public void zeroHexUpperCase() {
                long value = 0x0L;
              }

              public void negativeHexUpperCase() {
                long value = -0x80L;
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void disableable() {
    compilationHelper
        .setArgs(ImmutableList.of("-Xep:LongLiteralLowerCaseSuffix:OFF"))
        .expectNoDiagnostics()
        .addSourceLines(
            "LongLiteralLowerCaseSuffixPositiveCase1.java",
            """
package com.google.errorprone.bugpatterns.testdata;

/** Positive cases for {@link LongLiteralLowerCaseSuffix}. */
public class LongLiteralLowerCaseSuffixPositiveCase1 {

  // This constant string includes non-ASCII characters to make sure that we're not confusing
  // bytes and chars:
  @SuppressWarnings("unused")
  private static final String TEST_STRING = "Îñţérñåţîöñåļîžåţîờñ";

  public void positiveLowerCase() {
    // BUG: Diagnostic contains: value = 123432L
    long value = 123432l;
  }

  public void zeroLowerCase() {
    // BUG: Diagnostic contains: value = 0L
    long value = 0l;
  }

  public void negativeLowerCase() {
    // BUG: Diagnostic contains: value = -123432L
    long value = -123432l;
  }

  public void negativeExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  123432L
    long value = -123432l;
  }

  public void positiveHexLowerCase() {
    // BUG: Diagnostic contains: value = 0x8abcDEF0L
    long value = 0x8abcDEF0l;
    // BUG: Diagnostic contains: value = 0X80L
    value = 0X80l;
  }

  public void zeroHexLowerCase() {
    // BUG: Diagnostic contains: value = 0x0L
    long value = 0x0l;
    // BUG: Diagnostic contains: value = 0X0L
    value = 0X0l;
  }

  public void negativeHexLowerCase() {
    // BUG: Diagnostic contains: value = -0x8abcDEF0L
    long value = -0x8abcDEF0l;
    // BUG: Diagnostic contains: value = -0X80L
    value = -0X80l;
  }

  public void negativeHexExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  0x8abcDEF0L
    long value = -0x8abcDEF0l;
  }

  public void positiveOctalLowerCase() {
    // BUG: Diagnostic contains: value = 06543L
    long value = 06543l;
  }

  public void zeroOctalLowerCase() {
    // BUG: Diagnostic contains: value = 00L
    long value = 00l;
  }

  public void negativeOctalLowerCase() {
    // BUG: Diagnostic contains: value = -06543L
    long value = -06543l;
  }

  public void negativeOctalExtraSpacesLowerCase() {
    // BUG: Diagnostic contains: value = -  06543L
    long value = -06543l;
  }
}
""")
        .doTest();
  }
}
