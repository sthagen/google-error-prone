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
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@RunWith(JUnit4.class)
public class AssistedInjectScopingTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(AssistedInjectScoping.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "AssistedInjectScopingPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.inject.guice.testdata;

            import com.google.inject.Inject;
            import com.google.inject.Singleton;
            import com.google.inject.assistedinject.Assisted;
            import com.google.inject.assistedinject.AssistedInject;
            import com.google.inject.servlet.RequestScoped;

            /**
             * @author eaftan@google.com (Eddie Aftandilian)
             */
            public class AssistedInjectScopingPositiveCases {

              // BUG: Diagnostic contains: remove this line
              @Singleton
              public class TestClass {
                @Inject
                public TestClass(String unassisted, @Assisted String assisted) {}
              }

              // BUG: Diagnostic contains: remove this line
              @RequestScoped
              public class TestClass2 {
                @Inject
                public TestClass2(String unassisted, @Assisted String assisted) {}
              }

              // BUG: Diagnostic contains: remove this line
              @Singleton
              public class TestClass3 {
                @AssistedInject
                public TestClass3(String param) {}
              }

              /** Multiple constructors, but only one with @Inject, and that one matches. */
              // BUG: Diagnostic contains: remove this line
              @Singleton
              public class TestClass4 {
                @Inject
                public TestClass4(String unassisted, @Assisted String assisted) {}

                public TestClass4(String unassisted, int i) {}

                public TestClass4(int i, String unassisted) {}
              }

              /** Multiple constructors, none with @Inject, one matches. */
              // BUG: Diagnostic contains: remove this line
              @Singleton
              public class TestClass5 {
                public TestClass5(String unassisted1, String unassisted2) {}

                public TestClass5(String unassisted, int i) {}

                @AssistedInject
                public TestClass5(int i, String unassisted) {}
              }

              /** JSR330 annotations. */
              // BUG: Diagnostic contains: remove this line
              @javax.inject.Singleton
              public class TestClass6 {
                @javax.inject.Inject
                public TestClass6(String unassisted, @Assisted String assisted) {}
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "AssistedInjectScopingNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.inject.guice.testdata;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * @author eaftan@google.com (Eddie Aftandilian)
 */
public class AssistedInjectScopingNegativeCases {

  /** Class is not assisted and has no scoping annotation. */
  public class TestClass1 {
    public TestClass1(String unassisted1, String unassisted2) {}
  }

  /** Class is not assisted and has no scoping annotation, but has an unrelated annotation. */
  @SuppressWarnings("foo")
  public class TestClass2 {
    public TestClass2(String unassisted, @Assisted String assisted) {}
  }

  /** Class is not assisted but has scoping annotation. */
  @Singleton
  public class TestClass3 {
    public TestClass3(String unassisted1, String unassisted2) {}
  }

  /** Class is assisted via @Assisted param but has no scoping annotation. */
  public class TestClass4 {
    @Inject
    public TestClass4(@Assisted String assisted) {}
  }

  /** Class is assisted via @AssistedInject constructor but has no scoping annotation. */
  public class TestClass5 {
    @AssistedInject
    public TestClass5(String unassisted) {}
  }

  /** Class is not assisted -- constructor with @Assisted param does not have @Inject. */
  @Singleton
  public class TestClass6 {
    public TestClass6(@Assisted String assisted) {}
  }

  /** Multiple constructors but not assisted. */
  @Singleton
  public class TestClass7 {
    public TestClass7(String unassisted1, String unassisted2) {}

    public TestClass7(String unassisted, int i) {}

    public TestClass7(int i, String unassisted) {}
  }

  /** Multiple constructors, one with @Inject, non-@Inject ones match. */
  @Singleton
  public class TestClass8 {
    @Inject
    public TestClass8(String unassisted1, String unassisted2) {}

    @AssistedInject
    public TestClass8(String param, int i) {}

    @AssistedInject
    public TestClass8(int i, String param) {}
  }

  /** Multiple constructors, one with @Inject, non-@Inject ones match. */
  @Singleton
  public class TestClass9 {
    @Inject
    public TestClass9(String unassisted1, String unassisted2) {}

    @AssistedInject
    public TestClass9(String param, int i) {}

    @AssistedInject
    public TestClass9(int i, String param) {}
  }

  @Singleton
  public class TestClass10 {
    public TestClass10(@Assisted String assisted, String unassisted) {}

    public TestClass10(@Assisted String assisted, int i) {}

    public TestClass10(int i, @Assisted String assisted) {}
  }
}\
""")
        .doTest();
  }
}
