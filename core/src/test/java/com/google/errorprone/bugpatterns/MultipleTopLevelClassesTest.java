/*
 * Copyright 2015 The Error Prone Authors.
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

import static com.google.common.truth.TruthJUnit.assume;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link MultipleTopLevelClasses}Test */
@RunWith(JUnit4.class)
public class MultipleTopLevelClassesTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(MultipleTopLevelClasses.class, getClass());

  @Test
  public void twoClasses() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            package a;

            // BUG: Diagnostic contains: one top-level class declaration, instead found: One, Two
            class One {}

            // BUG: Diagnostic contains:
            class Two {}
            """)
        .doTest();
  }

  @Test
  public void packageInfo() {
    compilationHelper
        .addSourceLines(
            "a/package-info.java",
            """
            /** Documentation for our package */
            package a;
            """)
        .doTest();
  }

  @Test
  public void defaultPackage() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            // BUG: Diagnostic contains:
            class A {}

            // BUG: Diagnostic contains:
            class B {}
            """)
        .doTest();
  }

  @Test
  public void suppression() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            package a;

            class One {}

            @SuppressWarnings("TopLevel")
            class Other {}
            """)
        .doTest();
  }

  @Test
  public void emptyDeclaration() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            package a;

            class Test {}
            ;
            """)
        .doTest();
  }

  @Test
  public void semiInImportList() {
    assume().that(Runtime.version().feature()).isLessThan(21);
    compilationHelper
        .addSourceLines(
            "a/A.java",
            "package a;",
            "// BUG: Diagnostic contains:",
            "// one top-level class declaration, instead found: Test, Extra",
            "import java.util.List;;",
            "// BUG: Diagnostic contains:",
            "import java.util.ArrayList;",
            "// BUG: Diagnostic contains:",
            "class Test {",
            "  List<String> xs = new ArrayList<>();",
            "}",
            "// BUG: Diagnostic contains:",
            "class Extra {}")
        .doTest();
  }

  @Test
  public void twoRecords() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            package a;

            // BUG: Diagnostic contains: one top-level class declaration, instead found: One, Two
            record One() {}

            // BUG: Diagnostic contains:
            record Two() {}
            """)
        .doTest();
  }
}
