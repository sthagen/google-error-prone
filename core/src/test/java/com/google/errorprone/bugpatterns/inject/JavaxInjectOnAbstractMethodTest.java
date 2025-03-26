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

package com.google.errorprone.bugpatterns.inject;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
@RunWith(JUnit4.class)
public class JavaxInjectOnAbstractMethodTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(JavaxInjectOnAbstractMethod.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "JavaxInjectOnAbstractMethodPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.inject.testdata;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
public class JavaxInjectOnAbstractMethodPositiveCases {

  /** Abstract class has an injectable(javax.inject.Inject) abstract method. */
  public abstract class TestClass1 {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    abstract void abstractMethod();
  }

  /**
   * Abstract class has an injectable(javax.inject.Inject) abstract method and an unrelated concrete
   * method.
   */
  public abstract class TestClass2 {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    abstract void abstractMethod();

    public void foo() {}
  }

  /**
   * Abstract class has an injectable(javax.inject.Inject) abstract method and an unrelated abstract
   * method.
   */
  public abstract class TestClass3 {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    abstract void abstractMethod1();

    abstract void abstractMethod2();
  }

  /** Interface with Inject method, should also fail. */
  public interface TestInterface {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    void abstractMethod();
  }

  /** Concrete Implementer of interface. */
  public class Implementing implements TestInterface {
    // No error here
    public void abstractMethod() {}
  }

  /** Abstract implementer of interface. */
  public abstract class AbstractImplementing implements TestInterface {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    public abstract void abstractMethod();
  }

  interface HasDefault {
    // BUG: Diagnostic contains: remove
    @javax.inject.Inject
    default void foo() {}
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "JavaxInjectOnAbstractMethodNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.inject.testdata;

import javax.inject.Inject;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
public class JavaxInjectOnAbstractMethodNegativeCases {

  /** Concrete class has no methods or annotations. */
  public class TestClass1 {}

  /** Abstract class has a single abstract method with no annotation. */
  public abstract class TestClass2 {
    abstract void abstractMethod();
  }

  /** Concrete class has an injectable method. */
  public class TestClass3 {
    @Inject
    public void foo() {}
  }

  /** Abstract class has an injectable concrete method. */
  public abstract class TestClass4 {
    abstract void abstractMethod();

    @Inject
    public void concreteMethod() {}
  }

  /**
   * Abstract class has an com.google.inject.Inject abstract method (This is allowed; Injecting
   * abstract methods is only forbidden with javax.inject.Inject).
   */
  public abstract class TestClass5 {
    @com.google.inject.Inject
    abstract void abstractMethod();
  }

  /** Abstract class has an injectable(javax.inject.Inject) abstract method. Error is suppressed. */
  public abstract class TestClass6 {
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    @javax.inject.Inject
    abstract void abstractMethod();
  }
}\
""")
        .doTest();
  }
}
