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
 * @author mariasam@google.com (Maria Sam) on 6/27/17.
 */
@RunWith(JUnit4.class)
public class CollectionToArraySafeParameterTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(CollectionToArraySafeParameter.class, getClass());

  @Test
  public void positiveCases() {
    compilationHelper
        .addSourceLines(
            "CollectionToArraySafeParameterPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.HashSet;
            import java.util.Set;

            /**
             * @author mariasam@google.com (Maria Sam) on 6/27/17.
             */
            public class CollectionToArraySafeParameterPositiveCases<T> {

              private static void basicCase() {
                Collection<String> collection = new ArrayList<String>();
                // BUG: Diagnostic contains: array parameter
                Integer[] intArray = collection.toArray(new Integer[collection.size()]);

                Integer[] arrayOfInts = new Integer[10];
                // BUG: Diagnostic contains: array parameter
                Integer[] wrongArray = collection.toArray(arrayOfInts);

                Set<Integer> integerSet = new HashSet<Integer>();
                // BUG: Diagnostic contains: array parameter
                Long[] longArray = integerSet.toArray(new Long[10]);

                Set<Long> longSet = new HashSet<Long>();
                // BUG: Diagnostic contains: array parameter
                Integer[] integerArray = longSet.toArray(new Integer[10]);
              }

              void test(Foo<Integer> foo) {
                // BUG: Diagnostic contains: array parameter
                String[] things = foo.toArray(new String[] {});
              }

              void test(FooBar<Integer> foo) {
                // BUG: Diagnostic contains: array parameter
                Integer[] things = foo.toArray(new Integer[] {});
              }

              class FooBar<T> extends HashSet<String> {}

              class Foo<T> extends HashSet<T> {}
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCases() {
    compilationHelper
        .addSourceLines(
            "CollectionToArraySafeParameterNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author mariasam@google.com (Maria Sam) on 6/27/17.
 */
public class CollectionToArraySafeParameterNegativeCases {

  private void basicCase() {
    Collection<String> collection = new ArrayList<String>();
    Collection<Integer> collInts = new ArrayList<Integer>();

    Object[] intArrayActualNoParam = collInts.toArray();
    Integer[] intArrayActual = collInts.toArray(new Integer[collection.size()]);

    Collection<Object> collectionObjects = new ArrayList<>();
    Integer[] intArrayObjects = collectionObjects.toArray(new Integer[collectionObjects.size()]);

    Integer[] arrayOfInts = new Integer[10];
    Integer[] otherArray = collInts.toArray(arrayOfInts);

    Collection<Collection<Integer>> collectionOfCollection = new ArrayList<Collection<Integer>>();
    Collection<Integer>[] collectionOfCollectionArray =
        collectionOfCollection.toArray(new ArrayList[10]);

    SomeObject someObject = new SomeObject();
    Integer[] someObjectArray = someObject.toArray(new Integer[1]);

    // test to make sure that when the collection has no explicit type there is no error thrown
    // when "toArray" is called.
    Collection someObjects = new ArrayList();
    Object[] intArray = someObjects.toArray(new Integer[1]);
  }

  class FooBar<T> extends HashSet<T> {}

  void testFooBar(FooBar<Integer> fooBar) {
    Integer[] things = fooBar.toArray(new Integer[] {});
  }

  class Foo<T> extends HashSet<String> {}

  void test(Foo<Integer> foo) {
    String[] things = foo.toArray(new String[] {});
  }

  class SomeObject {
    Integer[] toArray(Integer[] someArray) {
      return new Integer[10];
    }
  }
}\
""")
        .doTest();
  }

  // regression test for b/67022899
  @Test
  public void b67022899() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.util.Collection;

            class Test {
              <C extends Collection<Integer>> void f(C cx) {
                cx.toArray(new Integer[0]);
              }
            }
            """)
        .doTest();
  }
}
