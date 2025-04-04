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
 * @author jsjeon@google.com (Jinseong Jeon)
 */
@RunWith(JUnit4.class)
public class IterableAndIteratorTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(IterableAndIterator.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "IterableAndIteratorPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.Iterator;

/**
 * @author jsjeon@google.com (Jinseong Jeon)
 */
public class IterableAndIteratorPositiveCases {

  /** Test Node */
  public static class MyNode {
    String tag;
    MyNode next;
  }

  /** Test List that implements both Iterator and Iterable */
  // BUG: Diagnostic contains: both
  public static class MyBadList implements Iterator<MyNode>, Iterable<MyNode> {
    private MyNode head;

    public MyBadList() {
      head = null;
    }

    @Override
    public boolean hasNext() {
      return head != null;
    }

    @Override
    public MyNode next() {
      if (hasNext()) {
        MyNode ret = head;
        head = head.next;
        return ret;
      }
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove is not supported");
    }

    public void add(MyNode node) {
      if (!hasNext()) {
        head.next = node;
      }
      head = node;
    }

    @Override
    public Iterator<MyNode> iterator() {
      return this;
    }
  }

  /** Test List that extends the above bad implementation Diagnostic should bypass this */
  public static class MyBadListInherited extends MyBadList {
    public MyBadListInherited() {}
  }

  /** Test List that implements only Iterator */
  public static class MyGoodList implements Iterator<MyNode> {
    private MyNode head;

    public MyGoodList() {
      head = null;
    }

    @Override
    public boolean hasNext() {
      return head != null;
    }

    @Override
    public MyNode next() {
      if (hasNext()) {
        MyNode ret = head;
        head = head.next;
        return ret;
      }
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove is not supported");
    }

    public void add(MyNode node) {
      if (!hasNext()) {
        head.next = node;
      }
      head = node;
    }
  }

  /** Test List that implicitly implements both interfaces */
  // BUG: Diagnostic contains: both
  public static class MyImplicitlyBadList extends MyGoodList implements Iterable<MyNode> {

    public MyImplicitlyBadList() {}

    @Override
    public Iterator<MyNode> iterator() {
      return this;
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
            "IterableAndIteratorNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.Iterator;

            /**
             * @author jsjeon@google.com (Jinseong Jeon)
             */
            public class IterableAndIteratorNegativeCases {

              /** Test Node */
              public static class MyNode {
                String tag;
                MyNode next;
              }

              /** Test List that implements only Iterator */
              public static class MyList1 implements Iterator<MyNode> {
                private MyNode head;

                public MyList1() {
                  head = null;
                }

                @Override
                public boolean hasNext() {
                  return head != null;
                }

                @Override
                public MyNode next() {
                  if (hasNext()) {
                    MyNode ret = head;
                    head = head.next;
                    return ret;
                  }
                  return null;
                }

                @Override
                public void remove() {
                  throw new UnsupportedOperationException("remove is not supported");
                }

                public void add(MyNode node) {
                  if (!hasNext()) {
                    head.next = node;
                  }
                  head = node;
                }
              }

              /** Test List that implements only Iterable */
              public static class MyList2 implements Iterable<MyNode> {

                @Override
                public Iterator<MyNode> iterator() {
                  MyList1 l = new MyList1();
                  // code to populate the list goes here
                  return l;
                }
              }
            }\
            """)
        .doTest();
  }
}
