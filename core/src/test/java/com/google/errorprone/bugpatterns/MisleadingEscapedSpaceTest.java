/*
 * Copyright 2024 The Error Prone Authors.
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

@RunWith(JUnit4.class)
public final class MisleadingEscapedSpaceTest {
  private final CompilationTestHelper testHelper =
      CompilationTestHelper.newInstance(MisleadingEscapedSpace.class, getClass());

  @Test
  public void misleadingEscape() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              // BUG: Diagnostic contains:
              private static final String FOO = " \\s ";
            }\
            """)
        .doTest();
  }

  @Test
  public void literalBackslashS() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO = " \\\\s ";
            }\
            """)
        .doTest();
  }

  @Test
  public void asSingleCharacter_misleading() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              // BUG: Diagnostic contains:
              private static final char x = '\\s';
            }\
            """)
        .doTest();
  }

  @Test
  public void withinTextBlock_notAtEndOfLine_misleading() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO =
                  // BUG: Diagnostic contains:
                  \"""
              foo   \\s  bar
              \""";
              private static final String BAZ =
                  // BUG: Diagnostic contains:
                  \"""
              foo   \\s
              bar  \\s baz
              \""";
            }
            """)
        .doTest();
  }

  @Test
  public void atEndOfLine_notMisleading() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO =
                  \"""
              foo   \\s
              bar     \\s
              \""";
            }
            """)
        .doTest();
  }

  @Test
  public void multipleAtEndOfLine_notMisleading() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO =
                  \"""
              foo   \\s\\s\\s\\s
              \""";
            }
            """)
        .doTest();
  }

  @Test
  public void withinCommentInBrokenUpString_noFinding() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO = "foo" + /* \\s */ " bar";
            }\
            """)
        .doTest();
  }

  @Test
  public void atEndOfString_noFinding() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              private static final String FOO =
                  \"""
              foo
              bar\\s\""";
            }
            """)
        .doTest();
  }

  @Test
  public void escapedSpaceAtEndOfString() {
    testHelper
        .addSourceLines(
            "Test.class",
            """
            class Test {
              // BUG: Diagnostic contains:
              private static final String FOO = "foo\\s";
            }\
            """)
        .doTest();
  }
}
