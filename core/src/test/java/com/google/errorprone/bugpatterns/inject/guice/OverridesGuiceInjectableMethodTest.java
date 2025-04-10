/*
 * Copyright 2013 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.inject.guice;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
@RunWith(JUnit4.class)
public class OverridesGuiceInjectableMethodTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(OverridesGuiceInjectableMethod.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "OverridesGuiceInjectableMethodPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.inject.guice.testdata;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
public class OverridesGuiceInjectableMethodPositiveCases {

  /** Class with a method foo() annotated with @com.google.inject.Inject. */
  public class TestClass1 {
    @com.google.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() that is not annotated with @Inject, but overrides a method that is
   * annotated with @com.google.inject.Inject
   */
  public class TestClass2 extends TestClass1 {
    // BUG: Diagnostic contains: @Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() that is not annotated with @Inject, but overrides a method that in
   * turn is overrides a method that is annotated with @com.google.inject.Inject
   */
  public class TestClass3 extends TestClass2 {
    // BUG: Diagnostic contains: @Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() that is not annotated with @javax.inject.Inject and overrides a
   * method that is annotated with @com.google.inject.Inject. This class does not contain an error,
   * but it is extended in the next test class.
   */
  public class TestClass4 extends TestClass1 {
    @javax.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() that is not annotated with @Inject and overrides a method that is is
   * annotated with @javax.inject.Inject. This super method in turn overrides a method that is
   * annoatated with @com.google.inject.Inject.
   */
  public class TestClass5 extends TestClass4 {
    // BUG: Diagnostic contains: @Inject
    public void foo() {}
  }

  /** Class that extends a class with an injected method, but doesn't override it. */
  public class TestClass6 extends TestClass1 {}

  /**
   * Class that extends a class with an injected method, but it was declared in a supertype that
   * isn't a direct supertype.
   */
  public class TestClass7 extends TestClass1 {
    // BUG: Diagnostic contains: @Inject
    public void foo() {}
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "OverridesGuiceInjectableMethodNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.inject.guice.testdata;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
public class OverridesGuiceInjectableMethodNegativeCases {

  /** Class with a method foo() annotated with @com.google.inject.Inject. */
  public class TestClass1 {
    @com.google.inject.Inject
    public void foo() {}
  }

  /** Class with a method foo() annotated with @javax.inject.Inject. */
  public class TestClass2 {
    @javax.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() annotated with @javax.inject.Inject that overrides a method annotated
   * with @com.google.inject.Inject.
   */
  public class TestClass3 extends TestClass1 {
    @javax.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() annotated with @com.google.inject.Inject that overrides a method
   * annotated with @javax.inject.Inject.
   */
  public class TestClass4 extends TestClass2 {
    @com.google.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() annotated with @javax.inject.Inject that overrides a method annotated
   * with @com.google.inject.Inject
   */
  public class TestClass5 extends TestClass1 {
    @javax.inject.Inject
    public void foo() {}
  }

  /**
   * Class with a method foo() that is not annotated with @Inject, but overrides a method that is
   * annotated with @com.google.inject.Inject. Warning is suppressed.
   */
  public class TestClass6 extends TestClass1 {
    @SuppressWarnings("OverridesGuiceInjectableMethod")
    @Override
    public void foo() {}
  }

  /** Class that extends a class with an injected method, but doesn't override it. */
  public class TestClass7 extends TestClass1 {}
}\
""")
        .doTest();
  }
}
