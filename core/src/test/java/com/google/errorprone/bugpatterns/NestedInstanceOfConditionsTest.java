/*
 * Copyright 2017 The Error Prone Authors.
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
 * @author mariasam@google.com (Maria Sam)
 * @author sulku@google.com (Marsela Sulku)
 */
@RunWith(JUnit4.class)
public class NestedInstanceOfConditionsTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(NestedInstanceOfConditions.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "NestedInstanceOfConditionsPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * @author mariasam@google.com (Maria Sam)
             * @author sulku@google.com (Marsela Sulku)
             */
            public class NestedInstanceOfConditionsPositiveCases {

              public static void nestedInstanceOfPost() {
                Object foo = new ClassA();
                Object bar = new ClassB();

                // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                if (foo instanceof ClassA) {
                  if (foo instanceof ClassB) {
                    System.out.println("test");
                  }
                }

                // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                if (foo instanceof ClassA) {
                  System.out.println("test");
                  if (foo instanceof ClassB) {
                    System.out.println("test");
                  }
                  System.out.println("test");
                }

                // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                if (foo instanceof ClassA) {
                  // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                  if (foo instanceof ClassA) {
                    if (foo instanceof ClassB) {
                      System.out.println("test");
                    }
                  }
                }

                // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                if (foo instanceof ClassA) {
                  // BUG: Diagnostic contains: Nested instanceOf conditions of disjoint types
                  if (foo instanceof ClassB) {
                    if (foo instanceof ClassC) {
                      System.out.println("test");
                    }
                  }
                }

                // BUG: Diagnostic contains: Nested instanceOf conditions
                if (foo instanceof ClassA) {
                  if (bar instanceof ClassB) {
                    if (foo instanceof ClassC) {
                      System.out.println("test");
                    }
                  }
                }

                if (foo instanceof ClassA) {
                  System.out.println("yay");
                  // BUG: Diagnostic contains: Nested instanceOf conditions
                } else if (foo instanceof ClassB) {
                  if (foo instanceof ClassC) {
                    System.out.println("uh oh");
                  }
                }
              }

              static class ClassA {}

              static class ClassB {}

              static class ClassC {}
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "NestedInstanceOfConditionsNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mariasam@google.com (Maria Sam)
 * @author sulku@google.com (Marsela Sulku)
 */
public class NestedInstanceOfConditionsNegativeCases {
  public static void nestedInstanceOfPositiveCases() {
    Object objectA = new Object();
    Object objectB = new Object();

    // different objects
    if (objectA instanceof SuperClass) {
      if (objectB instanceof DisjointClass) {
        System.out.println("yay");
      }
    }

    // nested if checks to see if subtype of first
    if (objectA instanceof SuperClass) {
      if (objectA instanceof SubClass) {
        System.out.println("yay");
      }
    }

    if (objectA instanceof SuperClass) {
      if (objectA instanceof SubClass) {
        if (objectB instanceof DisjointClass) {
          System.out.println("yay");
        }
      }
    }

    if (objectA instanceof SuperClass) {
      if (objectB instanceof DisjointClass) {
        if (objectA instanceof SubClass) {
          System.out.println("yay");
        }
      }
    }

    if (objectA instanceof SuperClass) {
      System.out.println("yay");
    } else if (objectA instanceof DisjointClass) {
      System.out.println("boo");
    } else if (objectA instanceof String) {
      System.out.println("aww");
    }

    if (objectA instanceof SuperClass) {
      objectA = "yay";
      if (objectA instanceof String) {
        System.out.println();
      }
    }

    if (objectA instanceof SuperClass) {
      if (objectA instanceof String) {
        objectA = "yay";
      }
    }

    List<Object> ls = new ArrayList<Object>();
    ls.add("hi");

    // even though this could potentially be an error, ls.get(0) can be altered in many ways in
    // between the two instanceof statements, therefore we do not match this case
    if (ls.get(0) instanceof String) {
      if (ls.get(0) instanceof SuperClass) {
        System.out.println("lol");
      }
    }
  }

  /** test class */
  public static class SuperClass {}
  ;

  /** test class */
  public static class SubClass extends SuperClass {}
  ;

  /** test class */
  public static class DisjointClass {}
  ;
}\
""")
        .doTest();
  }

  @Test
  public void patternMatchingInstanceof() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            public class Test {
              record Struct(Object a) {}

              public void test(Object x, Object y) {
                if (x instanceof Struct(Integer a1)) {
                  if (y instanceof Struct(Integer a2)) {}
                }
              }
            }
            """)
        .doTest();
  }
}
