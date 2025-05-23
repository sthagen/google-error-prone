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

package com.google.errorprone.bugpatterns.argumentselectiondefects;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import org.jspecify.annotations.Nullable;

/**
 * A heuristic for checking if a formal parameter matches a predefined set of words which have been
 * identified as ones which don't have a reliable similarity score. Typically these are words which
 * are used in many different contexts e.g. {@code key}, {@code value}, {@code str0}.
 *
 * @author andrewrice@google.com (Andrew Rice)
 */
final class LowInformationNameHeuristic implements Heuristic {

  private static final ImmutableSet<String> DEFAULT_FORMAL_PARAMETER_EXCLUSION_REGEXS =
      ImmutableSet.of(
          "[a-z][a-z]?[0-9]*", "arg[0-9]", "value", "key", "label", "param[0-9]", "str[0-9]");

  private final ImmutableSet<String> overloadedNamesRegexs;

  LowInformationNameHeuristic(ImmutableSet<String> overloadedNamesRegexs) {
    this.overloadedNamesRegexs = overloadedNamesRegexs;
  }

  LowInformationNameHeuristic() {
    this(DEFAULT_FORMAL_PARAMETER_EXCLUSION_REGEXS);
  }

  /**
   * Return true if this parameter does not match any of the regular expressions in the list of
   * overloaded words.
   */
  @Override
  public boolean isAcceptableChange(
      Changes changes, Tree node, MethodSymbol symbol, VisitorState state) {
    return changes.changedPairs().stream().allMatch(p -> findMatch(p.formal()) == null);
  }

  /**
   * Return the first regular expression from the list of overloaded words which matches the
   * parameter name.
   */
  protected @Nullable String findMatch(Parameter parameter) {
    return overloadedNamesRegexs.stream()
        .filter(r -> parameter.name().matches(r))
        .findFirst()
        .orElse(null);
  }
}
