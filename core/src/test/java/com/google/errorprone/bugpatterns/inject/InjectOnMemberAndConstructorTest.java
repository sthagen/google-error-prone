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

package com.google.errorprone.bugpatterns.inject;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author bhagwani@google.com (Sumit Bhagwani)
 */
@RunWith(JUnit4.class)
public class InjectOnMemberAndConstructorTest {

  private final BugCheckerRefactoringTestHelper testHelper =
      BugCheckerRefactoringTestHelper.newInstance(InjectOnMemberAndConstructor.class, getClass());
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(InjectOnMemberAndConstructor.class, getClass());

  @Test
  public void positiveCase() {
    testHelper
        .addInputLines(
            "in/InjectOnMemberAndConstructorPositiveCases.java",
"""
import javax.inject.Inject;

public class InjectOnMemberAndConstructorPositiveCases {
  @Inject private final String stringFieldWithInject;
  @Inject private final Long longFieldWithInject;
  private final String stringFieldWithoutInject;

  @Inject
  public InjectOnMemberAndConstructorPositiveCases(
      String stringFieldWithInject, String stringFieldWithoutInject, Long longFieldWithInject) {
    this.stringFieldWithInject = stringFieldWithInject;
    this.stringFieldWithoutInject = stringFieldWithoutInject;
    this.longFieldWithInject = longFieldWithInject;
  }
}
""")
        .addOutputLines(
            "out/InjectOnMemberAndConstructorPositiveCases.java",
"""
import javax.inject.Inject;

public class InjectOnMemberAndConstructorPositiveCases {
  private final String stringFieldWithInject;
  private final Long longFieldWithInject;
  private final String stringFieldWithoutInject;

  @Inject
  public InjectOnMemberAndConstructorPositiveCases(
      String stringFieldWithInject, String stringFieldWithoutInject, Long longFieldWithInject) {
    this.stringFieldWithInject = stringFieldWithInject;
    this.stringFieldWithoutInject = stringFieldWithoutInject;
    this.longFieldWithInject = longFieldWithInject;
  }
}
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "InjectOnMemberAndConstructorNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.inject.testdata;

            import javax.inject.Inject;

            /**
             * Negative test cases for {@link InjectOnMemberAndConstructor} check.
             *
             * @author bhagwani@google.com (Sumit Bhagwani)
             */
            public class InjectOnMemberAndConstructorNegativeCases {

              public class InjectOnConstructorOnly {
                private final String stringFieldWithoutInject;

                @Inject
                public InjectOnConstructorOnly(String stringFieldWithoutInject) {
                  this.stringFieldWithoutInject = stringFieldWithoutInject;
                }
              }

              public class InjectOnFieldOnly {
                @Inject private String stringFieldWithInject;
              }

              public class MixedInject {
                @Inject private String stringFieldWithInject;

                @Inject
                public MixedInject() {}
              }
            }\
            """)
        .doTest();
  }
}
