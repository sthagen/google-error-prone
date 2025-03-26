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
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link UnsafeReflectiveConstructionCast} bug pattern.
 *
 * @author bhagwani@google.com (Sumit Bhagwani)
 */
@RunWith(JUnit4.class)
public class UnsafeReflectiveConstructionCastTest {
  private final BugCheckerRefactoringTestHelper testHelper =
      BugCheckerRefactoringTestHelper.newInstance(
          UnsafeReflectiveConstructionCast.class, getClass());
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(UnsafeReflectiveConstructionCast.class, getClass());

  @Test
  public void positiveCase() {
    testHelper
        .addInputLines(
            "in/Test.java",
"""
class Test {
  private String newInstanceOnGetDeclaredConstructorChained() throws Exception {
    return (String) Class.forName("java.lang.String").getDeclaredConstructor().newInstance();
  }
}
""")
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              private String newInstanceOnGetDeclaredConstructorChained() throws Exception {
                return Class.forName("java.lang.String")
                    .asSubclass(String.class)
                    .getDeclaredConstructor()
                    .newInstance();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseConstructor() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              private String newInstanceOnGetConstructorChained() throws Exception {
                return (String) Class.forName("java.lang.String").getConstructor().newInstance();
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
            """
            class Test {
              private String newInstanceOnGetConstructorChained() throws Exception {
                return Class.forName("java.lang.String")
                    .asSubclass(String.class)
                    .getConstructor()
                    .newInstance();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void positiveCaseWithErasure() {
    testHelper
        .addInputLines(
            "in/Test.java",
            """
            class Test {
              class Fn<T> {}
              ;

              private Fn<String> newInstanceOnGetDeclaredConstructorChained() throws Exception {
                return (Fn<String>) Class.forName("Fn").getDeclaredConstructor().newInstance();
              }
            }
            """)
        .addOutputLines(
            "out/Test.java",
"""
class Test {
  class Fn<T> {}
  ;

  private Fn<String> newInstanceOnGetDeclaredConstructorChained() throws Exception {
    return (Fn<String>)
        Class.forName("Fn").asSubclass(Fn.class).getDeclaredConstructor().newInstance();
  }
}
""")
        .doTest();
  }

  @Test
  public void negativeCaseWithIntersection() {
    compilationHelper
        .addSourceLines(
            "in/Test.java",
"""
import java.io.Serializable;

class Test {
  interface Fn {}
  ;

  private Fn newInstanceOnGetDeclaredConstructorChained() throws Exception {
    return (Serializable & Fn) Class.forName("Fn").getDeclaredConstructor().newInstance();
  }
}
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "UnsafeReflectiveConstructionCastNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

/**
 * Negative cases for {@link UnsafeReflectiveConstructionCast}.
 *
 * @author bhagwani@google.com (Sumit Bhagwani)
 */
public class UnsafeReflectiveConstructionCastNegativeCases {

  public String newInstanceDirectCall() throws Exception {
    return (String) Class.forName("java.lang.String").newInstance();
  }

  public String newInstanceDirectlyOnClassAndGetDeclaredConstructor() throws Exception {
    return (String) String.class.getDeclaredConstructor().newInstance();
  }

  public String newInstanceDirectlyOnClassAndNewInstance() throws Exception {
    return (String) String.class.newInstance();
  }

  public String invocationWithAsSubclass() throws Exception {
    return Class.forName("java.lang.String").asSubclass(String.class).newInstance();
  }

  public class Supplier<T> {
    public T get(String className) {
      try {
        return (T) Class.forName(className).getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}\
""")
        .doTest();
  }
}
