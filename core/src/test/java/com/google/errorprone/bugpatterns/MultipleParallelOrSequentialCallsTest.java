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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link MultipleParallelOrSequentialCalls}.
 *
 * @author mariasam@google.com (Maria Sam) on 7/6/17.
 */
@RunWith(JUnit4.class)
public class MultipleParallelOrSequentialCallsTest {

  private final CompilationTestHelper compilationTestHelper =
      CompilationTestHelper.newInstance(MultipleParallelOrSequentialCalls.class, getClass());

  @Test
  public void positiveCases() {
    compilationTestHelper
        .addSourceLines(
            "MultipleParallelOrSequentialCallsPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author @mariasam (Maria Sam) on 7/6/17.
 */
class MultipleParallelOrSequentialCallsPositiveCases {

  public void basicCaseParallel(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel().parallel();
  }

  public void basicCaseParallelNotFirst(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m);'?
    list.stream().map(m -> m).parallel().parallel();
  }

  public void basicCollection(Collection<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel().parallel();
  }

  public void parallelStream(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream().parallel().parallel();
  }

  public void basicCaseParallelThisInMethodArg(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'this.hello(list.stream().parallel());'?
    this.hello(list.stream().parallel().parallel());
  }

  public void onlyOneError(List<String> list) {
    this.hello(
        // BUG: Diagnostic contains: Multiple calls
        list.stream().parallel().parallel());
  }

  public void mapMethod(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'hello(list.stream().parallel().map(m ->
    // this.hello(null)));'?
    hello(list.stream().map(m -> this.hello(null)).parallel().parallel());
  }

  public void betweenMethods(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m.toString());'?
    list.stream().parallel().map(m -> m.toString()).parallel();
  }

  public void basicCaseParallelNotLast(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m ->
    // m.toString()).findFirst();'?
    list.stream().parallel().map(m -> m.toString()).parallel().findFirst();
  }

  public void basicCaseSequential(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().map(m -> m.toString());'?
    list.stream().sequential().map(m -> m.toString()).sequential();
  }

  public void bothSequentialAndParallel(List<String> list) {
    // this case is unlikely (wrong, even) but just checking that this works
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().parallel();'?
    list.stream().sequential().parallel().sequential();
  }

  public void bothSequentialAndParallelMultiple(List<String> list) {
    // this is even more messed up, this test is here to make sure the checker doesn't throw an
    // exception
    // BUG: Diagnostic contains: Multiple calls
    list.stream().sequential().parallel().sequential().parallel();
  }

  public void parallelMultipleLines(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel()
    list.stream().parallel().map(m -> m.toString()).parallel();
  }

  public void multipleParallelCalls(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream().sequential();
  }

  public String hello(Stream st) {
    return "";
  }

  public void streamWithinAStream(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream()
        .flatMap(childDir -> list2.stream())
        .parallel()
        .flatMap(a -> list2.stream())
        .parallel();
  }

  public void streamWithinAStreamImmediatelyAfterOtherParallel(
      List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream().parallel().map(m -> list2.stream().parallel()).parallel();
  }

  public void parallelAndNestedStreams(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.parallelStream()
        .flatMap(childDir -> list2.stream())
        .parallel()
        .filter(m -> (new TestClass("test")).testClass())
        .map(
            a -> {
              if (a == null) {
                return a;
              }
              return null;
            })
        .filter(a -> a != null)
        .flatMap(a -> list2.stream())
        .parallel();
  }

  private class TestClass {
    public TestClass(String con) {}

    private boolean testClass() {
      return true;
    }
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCases() {
    compilationTestHelper
        .addSourceLines(
            "MultipleParallelOrSequentialCallsNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.List;

            /** Created by mariasam on 7/6/17. */
            public class MultipleParallelOrSequentialCallsNegativeCases {

              public void basicCase(List<String> list) {
                list.stream().parallel();
              }

              public void basicCaseSequential(List<String> list) {
                list.stream().sequential();
              }

              public void basicCaseNotLast(List<String> list) {
                list.stream().parallel().findFirst();
              }

              public void middleParallel(List<String> list) {
                list.stream().map(m -> m).parallel().filter(m -> m.isEmpty());
              }

              public void otherMethod() {
                SomeObject someObject = new SomeObject();
                someObject.parallel().parallel();
              }

              public void otherMethodNotParallel(List<String> list) {
                list.stream().filter(m -> m.isEmpty()).findFirst();
              }

              public void streamWithinAStreamImmediatelyAfter(List<String> list) {
                list.stream().map(m -> list.stream().parallel()).parallel();
              }

              public void streamWithinAStreamImmediatelyAfterOtherParallelBothFirstAndWithin(
                  List<String> list) {
                list.stream().parallel().map(m -> list.stream().parallel());
              }

              public void streamWithinAStreamImmediatelyAfterOtherParallelBoth(List<String> list) {
                list.stream().sequential().map(m -> list.stream().parallel()).parallel();
              }

              class SomeObject {
                public SomeObject parallel() {
                  return null;
                }
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void fixes() {
    BugCheckerRefactoringTestHelper.newInstance(MultipleParallelOrSequentialCalls.class, getClass())
        .addInputLines(
            "MultipleParallelOrSequentialCallsPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author @mariasam (Maria Sam) on 7/6/17.
 */
class MultipleParallelOrSequentialCallsPositiveCases {

  public void basicCaseParallel(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel().parallel();
  }

  public void basicCaseParallelNotFirst(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m);'?
    list.stream().map(m -> m).parallel().parallel();
  }

  public void basicCollection(Collection<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel().parallel();
  }

  public void parallelStream(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream().parallel().parallel();
  }

  public void basicCaseParallelThisInMethodArg(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'this.hello(list.stream().parallel());'?
    this.hello(list.stream().parallel().parallel());
  }

  public void onlyOneError(List<String> list) {
    this.hello(
        // BUG: Diagnostic contains: Multiple calls
        list.stream().parallel().parallel());
  }

  public void mapMethod(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'hello(list.stream().parallel().map(m ->
    // this.hello(null)));'?
    hello(list.stream().map(m -> this.hello(null)).parallel().parallel());
  }

  public void betweenMethods(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m.toString());'?
    list.stream().parallel().map(m -> m.toString()).parallel();
  }

  public void basicCaseParallelNotLast(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m ->
    // m.toString()).findFirst();'?
    list.stream().parallel().map(m -> m.toString()).parallel().findFirst();
  }

  public void basicCaseSequential(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().map(m -> m.toString());'?
    list.stream().sequential().map(m -> m.toString()).sequential();
  }

  public void bothSequentialAndParallel(List<String> list) {
    // this case is unlikely (wrong, even) but just checking that this works
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().parallel();'?
    list.stream().sequential().parallel().sequential();
  }

  public void bothSequentialAndParallelMultiple(List<String> list) {
    // this is even more messed up, this test is here to make sure the checker doesn't throw an
    // exception
    // BUG: Diagnostic contains: Multiple calls
    list.stream().sequential().parallel().sequential().parallel();
  }

  public void parallelMultipleLines(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel()
    list.stream().parallel().map(m -> m.toString()).parallel();
  }

  public void multipleParallelCalls(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream().sequential();
  }

  public String hello(Stream st) {
    return "";
  }

  public void streamWithinAStream(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream()
        .flatMap(childDir -> list2.stream())
        .parallel()
        .flatMap(a -> list2.stream())
        .parallel();
  }

  public void streamWithinAStreamImmediatelyAfterOtherParallel(
      List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream().parallel().map(m -> list2.stream().parallel()).parallel();
  }

  public void parallelAndNestedStreams(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.parallelStream()
        .flatMap(childDir -> list2.stream())
        .parallel()
        .filter(m -> (new TestClass("test")).testClass())
        .map(
            a -> {
              if (a == null) {
                return a;
              }
              return null;
            })
        .filter(a -> a != null)
        .flatMap(a -> list2.stream())
        .parallel();
  }

  private class TestClass {
    public TestClass(String con) {}

    private boolean testClass() {
      return true;
    }
  }
}\
""")
        .addOutputLines(
            "MultipleParallelOrSequentialCallsPositiveCases_expected.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author @mariasam (Maria Sam) on 7/6/17.
 */
class MultipleParallelOrSequentialCallsPositiveCases {

  public void basicCaseParallel(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel();
  }

  public void basicCaseParallelNotFirst(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m);'?
    list.stream().parallel().map(m -> m);
  }

  public void basicCollection(Collection<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel();'?
    list.stream().parallel();
  }

  public void parallelStream(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream();
  }

  public void basicCaseParallelThisInMethodArg(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'this.hello(list.stream().parallel());'?
    this.hello(list.stream().parallel());
  }

  public void onlyOneError(List<String> list) {
    this.hello(
        // BUG: Diagnostic contains: Multiple calls
        list.stream().parallel());
  }

  public void mapMethod(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'hello(list.stream().parallel().map(m ->
    // this.hello(null)));'?
    hello(list.stream().parallel().map(m -> this.hello(null)));
  }

  public void betweenMethods(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m -> m.toString());'?
    list.stream().parallel().map(m -> m.toString());
  }

  public void basicCaseParallelNotLast(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel().map(m ->
    // m.toString()).findFirst();'?
    list.stream().parallel().map(m -> m.toString()).findFirst();
  }

  public void basicCaseSequential(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().map(m -> m.toString());'?
    list.stream().sequential().map(m -> m.toString());
  }

  public void bothSequentialAndParallel(List<String> list) {
    // this case is unlikely (wrong, even) but just checking that this works
    // BUG: Diagnostic contains: Did you mean 'list.stream().sequential().parallel();'?
    list.stream().sequential().parallel();
  }

  public void bothSequentialAndParallelMultiple(List<String> list) {
    // this is even more messed up, this test is here to make sure the checker doesn't throw an
    // exception
    // BUG: Diagnostic contains: Multiple calls
    list.stream().sequential().parallel().parallel();
  }

  public void parallelMultipleLines(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.stream().parallel()
    list.stream().parallel().map(m -> m.toString());
  }

  public void multipleParallelCalls(List<String> list) {
    // BUG: Diagnostic contains: Did you mean 'list.parallelStream();'?
    list.parallelStream();
  }

  public String hello(Stream st) {
    return "";
  }

  public void streamWithinAStream(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream().parallel().flatMap(childDir -> list2.stream()).flatMap(a -> list2.stream());
  }

  public void streamWithinAStreamImmediatelyAfterOtherParallel(
      List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.stream().parallel().map(m -> list2.stream().parallel());
  }

  public void parallelAndNestedStreams(List<String> list, List<String> list2) {
    // BUG: Diagnostic contains: Did you mean
    list.parallelStream()
        .flatMap(childDir -> list2.stream())
        .filter(m -> (new TestClass("test")).testClass())
        .map(
            a -> {
              if (a == null) {
                return a;
              }
              return null;
            })
        .filter(a -> a != null)
        .flatMap(a -> list2.stream());
  }

  private class TestClass {
    public TestClass(String con) {}

    private boolean testClass() {
      return true;
    }
  }
}\
""")
        .doTest(TestMode.AST_MATCH);
  }
}
