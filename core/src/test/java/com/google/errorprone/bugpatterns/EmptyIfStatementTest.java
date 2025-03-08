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
 * @author alexeagle@google.com (Alex Eagle)
 */
@RunWith(JUnit4.class)
public class EmptyIfStatementTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(EmptyIfStatement.class, getClass());

  @Test
  @SuppressWarnings("MisformattedTestData") // intentional
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "EmptyIfStatementPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * Positive test cases for the empty if statement check.
             *
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class EmptyIfStatementPositiveCases {

              public static void positiveCase1() {
                int i = 10;
                // BUG: Diagnostic contains: if (i == 10) {
                if (i == 10); {
                  i++;
                }
              }

              public static void positiveCase2() {
                int i = 10;
                // BUG: Diagnostic contains: if (i == 10)
                if (i == 10);
                i++;
                System.out.println("foo");
              }

              public static void positiveCase3() {
                int i = 10;
                if (i == 10)
                  // BUG: Diagnostic contains: remove this line
                  ;
                i++;
                System.out.println("foo");
              }

              public static void positiveCase4() {
                int i = 10;
                // BUG: Diagnostic contains: remove this line
                if (i == 10)            ;
              }

              public static void positiveCase5() {
                int i = 10;
                if (i == 10)
                  // BUG: Diagnostic contains: remove this line
                  ;
                {
                  System.out.println("foo");
                }
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "EmptyIfStatementNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;
            /**
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class EmptyIfStatementNegativeCases {

              // just a normal use of if
              public static void negativeCase1() {
                int i = 10;
                if (i == 10) {
                  System.out.println("foo");
                }
                i++;
              }

              // empty then part but nonempty else
              public static void negativeCase2() {
                int i = 0;
                if (i == 10)
                  ;
                else System.out.println("not 10");
              }

              // multipart if with non-empty else
              public static void negativeCase3() {
                int i = 0;
                if (i == 10)
                  ;
                else if (i == 11)
                  ;
                else if (i == 12)
                  ;
                else System.out.println("not 10, 11, or 12");
              }
            }\
            """)
        .doTest();
  }
}
