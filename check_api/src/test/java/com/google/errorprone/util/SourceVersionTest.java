/*
 * Copyright 2023 The Error Prone Authors.
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
package com.google.errorprone.util;

import static com.google.common.truth.Truth.assertThat;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceVersionTest {
  @Test
  public void supportsSwitchExpressions_notSupported() {
    Context context = contextWithSourceVersion("13");

    assertThat(SourceVersion.supportsSwitchExpressions(context)).isFalse();
  }

  @Test
  public void supportsSwitchExpressions_conditionallySupported() {
    Context context = contextWithSourceVersion("14");

    assertThat(SourceVersion.supportsSwitchExpressions(context))
        .isEqualTo(Runtime.version().feature() >= 14);
  }

  @Test
  public void supportsTextBlocks_notSupported() {
    Context context = contextWithSourceVersion("14");

    assertThat(SourceVersion.supportsTextBlocks(context)).isFalse();
  }

  @Test
  public void supportsTextBlocks_conditionallySupported() {
    Context context = contextWithSourceVersion("15");

    assertThat(SourceVersion.supportsTextBlocks(context))
        .isEqualTo(Runtime.version().feature() >= 15);
  }

  @Test
  public void supportsPatternMatchingSwitch_notSupported() {
    Context context = contextWithSourceVersion("17");

    assertThat(SourceVersion.supportsPatternMatchingSwitch(context)).isFalse();
  }

  @Test
  public void supportsPatternMatchingSwitch_supported() {
    Context context = contextWithSourceVersion("21");

    assertThat(SourceVersion.supportsPatternMatchingSwitch(context))
        .isEqualTo(Runtime.version().feature() >= 21);
  }

  private static Context contextWithSourceVersion(String versionString) {
    Context context = new Context();

    Options options = Options.instance(context);
    options.put(Option.SOURCE, versionString);

    return context;
  }
}
