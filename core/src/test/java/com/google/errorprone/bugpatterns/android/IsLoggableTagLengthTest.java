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

package com.google.errorprone.bugpatterns.android;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author epmjohnston@google.com (Emily P.M. Johnston)
 */
@RunWith(JUnit4.class)
public final class IsLoggableTagLengthTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(IsLoggableTagLength.class, getClass())
          .addSourceLines(
              "Log.java",
              """
              package android.util;

              public class Log {
                public static boolean isLoggable(String tag, int level) {
                  return false;
                }

                public static final int INFO = 0;
              }\
              """)
          .setArgs(ImmutableList.of("-XDandroidCompatible=true"));

  @Test
  public void negativeCaseLiteral() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              public void log() {
                Log.isLoggable("SHORT_ENOUGH", Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseLiteral() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              public void log() {
                // BUG: Diagnostic contains: IsLoggableTagLength
                Log.isLoggable("THIS_TAG_NAME_IS_WAY_TOO_LONG", Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseLiteralUnicode() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              public void log() {
                Log.isLoggable("🚀🚀🚀🚀", Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseLiteralUnicode() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              public void log() {
                // BUG: Diagnostic contains: IsLoggableTagLength
                Log.isLoggable("☔☔☔☔☔☔☔☔☔☔☔☔", Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseFinalField() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              static final String TAG = "SHORT_ENOUGH";

              public void log() {
                Log.isLoggable(TAG, Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseFinalField() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              static final String TAG = "THIS_TAG_NAME_IS_WAY_TOO_LONG";

              public void log() {
                // BUG: Diagnostic contains: IsLoggableTagLength
                Log.isLoggable(TAG, Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseClassName() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              public void log() {
                Log.isLoggable(Test.class.getSimpleName(), Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseClassName() {
    compilationHelper
        .addSourceLines(
            "ThisClassNameIsWayTooLong.java",
            """
            import android.util.Log;

            class ThisClassNameIsWayTooLong {
              public void log() {
                // BUG: Diagnostic contains: IsLoggableTagLength
                Log.isLoggable(ThisClassNameIsWayTooLong.class.getSimpleName(), Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseFinalFieldClassName() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              static final String TAG = Test.class.getSimpleName();

              public void log() {
                Log.isLoggable(TAG, Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseFinalFieldClassName() {
    compilationHelper
        .addSourceLines(
            "ThisClassNameIsWayTooLong.java",
            """
            import android.util.Log;

            class ThisClassNameIsWayTooLong {
              static final String TAG = ThisClassNameIsWayTooLong.class.getSimpleName();

              public void log() {
                // BUG: Diagnostic contains: IsLoggableTagLength
                Log.isLoggable(TAG, Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseNonFinalFieldUninitialized() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import android.util.Log;

            class Test {
              String unknownValue;

              public void log() {
                Log.isLoggable(unknownValue, Log.INFO);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseNonFinalFieldClassNameTooLong() {
    compilationHelper
        .addSourceLines(
            "ThisClassNameIsWayTooLong.java",
            """
            import android.util.Log;

            class ThisClassNameIsWayTooLong {
              String TAG = ThisClassNameIsWayTooLong.class.getSimpleName();

              public void log() {
                Log.isLoggable(TAG, Log.INFO);
              }
            }
            """)
        .doTest();
  }
}
