/* Copyright 2016 The Error Prone Authors.
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
 * @author sulku@google.com (Marsela Sulku)
 * @author mariasam@google.com (Maria Sam)
 */
@RunWith(JUnit4.class)
public class ComparableAndComparatorTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ComparableAndComparator.class, getClass());

  @Test
  public void positive() {
    compilationHelper
        .addSourceLines(
            "ComparableAndComparatorPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.Comparator;

            /**
             * @author sulku@google.com (Marsela Sulku)
             * @author mariasam@google.com (Maria Sam)
             */
            public class ComparableAndComparatorPositiveCases {

              /** implements both interfaces */
              // BUG: Diagnostic contains: Class should not implement both
              public static class BadClass implements Comparable<BadClass>, Comparator<BadClass> {
                @Override
                public int compareTo(BadClass comparableNode) {
                  return 0;
                }

                @Override
                public int compare(BadClass a, BadClass b) {
                  return 0;
                }
              }

              /** Superclass test class */
              public static class SuperClass implements Comparator<SuperClass> {
                @Override
                public int compare(SuperClass o1, SuperClass o2) {
                  return 0;
                }
              }

              /** SubClass test class */
              // BUG: Diagnostic contains: Class should not implement both
              public static class SubClass extends SuperClass implements Comparable<SubClass> {
                @Override
                public int compareTo(SubClass o) {
                  return 0;
                }
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negative() {
    compilationHelper
        .addSourceLines(
            "ComparableAndComparatorNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.Comparator;

/** Created by mariasam on 6/5/17. */
public class ComparableAndComparatorNegativeCases {

  /** Class that implements comparable, but also defines a comparator */
  public static class ComparableAndComparatorNested
      implements Comparable<ComparableAndComparatorNested> {

    /** Comparator */
    private static final Comparator<ComparableAndComparatorNested> myComparator =
        new Comparator<ComparableAndComparatorNested>() {

          @Override
          public int compare(ComparableAndComparatorNested o1, ComparableAndComparatorNested o2) {
            return 0;
          }
        };

    @Override
    public int compareTo(ComparableAndComparatorNested o) {
      return 0;
    }
  }

  /** class that only implements comparable */
  public static class OnlyComparable implements Comparable<OnlyComparable> {

    @Override
    public int compareTo(OnlyComparable o) {
      return 0;
    }
  }

  /** class that only implements comparator */
  public static class OnlyComparator implements Comparator<OnlyComparator> {
    @Override
    public int compare(OnlyComparator o1, OnlyComparator o2) {
      return 0;
    }
  }

  /** This test case is here to increase readability */
  // BUG: Diagnostic contains: Class should not implement both
  public static class BadClass implements Comparable<BadClass>, Comparator<BadClass> {
    @Override
    public int compareTo(BadClass comparableNode) {
      return 0;
    }

    @Override
    public int compare(BadClass a, BadClass b) {
      return 0;
    }
  }

  /** Subclass should not cause error */
  public static class BadClassSubclass extends BadClass {
    public int sampleMethod() {
      return 0;
    }
  }

  /** Enums implementing comparator are ok */
  enum TestEnum implements Comparator<Integer> {
    MONDAY,
    TUESDAY,
    WEDNESDAY;

    @Override
    public int compare(Integer one, Integer two) {
      return 0;
    }
  }
}\
""")
        .doTest();
  }
}
