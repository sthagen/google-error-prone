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
 * @author mdempsky@google.com (Matthew Dempsky)
 */
@RunWith(JUnit4.class)
public class InvalidPatternSyntaxTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(InvalidPatternSyntax.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "InvalidPatternSyntaxPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.regex.Pattern;

            /**
             * @author mdempsky@google.com (Matthew Dempsky)
             */
            public class InvalidPatternSyntaxPositiveCases {
              public static final String INVALID = "*";

              {
                // BUG: Diagnostic contains: Unclosed character class
                Pattern.matches("[^\\\\]", "");
                // BUG: Diagnostic contains: Unclosed character class
                Pattern.matches("[a-z", "");
                // BUG: Diagnostic contains: Illegal repetition
                Pattern.matches("{", "");

                // BUG: Diagnostic contains:
                Pattern.matches(INVALID, "");
                // BUG: Diagnostic contains:
                "".matches(INVALID);
                // BUG: Diagnostic contains:
                "".replaceAll(INVALID, "");
                // BUG: Diagnostic contains:
                "".replaceFirst(INVALID, "");
                // BUG: Diagnostic contains:
                "".split(INVALID);
                // BUG: Diagnostic contains:
                "".split(INVALID, 0);
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "InvalidPatternSyntaxNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.regex.Pattern;

            /**
             * @author mdempsky@google.com (Matthew Dempsky)
             */
            public class InvalidPatternSyntaxNegativeCases {
              public void foo(String x) {
                Pattern.compile("t");
                Pattern.compile("t", 0);
                Pattern.matches("t", "");
                "".matches("t");
                "".replaceAll("t", "");
                "".replaceFirst("t", "");
                "".split("t");
                "".split("t", 0);

                Pattern.compile(x);
                Pattern.compile(x, 0);
                Pattern.matches(x, "");
                "".matches(x);
                "".replaceAll(x, "");
                "".replaceFirst(x, "");
                "".split(x);
                "".split(x, 0);
              }
            }\
            """)
        .doTest();
  }
}
