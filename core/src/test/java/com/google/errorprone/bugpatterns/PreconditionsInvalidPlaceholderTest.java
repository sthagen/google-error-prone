/*
 * Copyright 2012 The Error Prone Authors.
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
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@RunWith(JUnit4.class)
public class PreconditionsInvalidPlaceholderTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(PreconditionsInvalidPlaceholder.class, getClass());

  @Test
  public void testPositiveCase1() {
    compilationHelper.addSourceFile("PreconditionsInvalidPlaceholderPositiveCase1.java").doTest();
  }

  @Test
  public void testNegativeCase1() {
    compilationHelper.addSourceFile("PreconditionsInvalidPlaceholderNegativeCase1.java").doTest();
  }
}
