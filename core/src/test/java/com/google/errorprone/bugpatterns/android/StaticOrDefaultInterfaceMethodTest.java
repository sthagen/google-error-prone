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
public final class StaticOrDefaultInterfaceMethodTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(StaticOrDefaultInterfaceMethod.class, getClass())
          .setArgs(ImmutableList.of("-XDandroidCompatible=true"));

  @Test
  public void positiveCaseDefault() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains: StaticOrDefaultInterfaceMethod
              default void test() {
                System.out.println();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseStatic() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains: StaticOrDefaultInterfaceMethod
              static void test() {
                System.out.println();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseNoBody() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              void test();
            }
            """)
        .doTest();
  }

  @Test
  public void negativeCaseClass() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              static void test() {
                System.out.println();
              }
            }
            """)
        .doTest();
  }
}
