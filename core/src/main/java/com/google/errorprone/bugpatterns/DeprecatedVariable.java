/*
 * Copyright 2021 The Error Prone Authors.
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

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getAnnotationWithSimpleName;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.util.Optional;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary = "Applying the @Deprecated annotation to local variables or parameters has no effect",
    severity = WARNING)
public class DeprecatedVariable extends BugChecker implements VariableTreeMatcher {
  @Override
  public Description matchVariable(VariableTree tree, VisitorState state) {
    Symbol.VarSymbol sym = ASTHelpers.getSymbol(tree);
    if (!hasAnnotation(sym, Deprecated.class.getName(), state)) {
      return NO_MATCH;
    }
    switch (sym.getKind()) {
      case LOCAL_VARIABLE, PARAMETER, BINDING_VARIABLE -> {}
      default -> {
        return NO_MATCH;
      }
    }
    Description.Builder description = buildDescription(tree);
    Optional.ofNullable(
            getAnnotationWithSimpleName(tree.getModifiers().getAnnotations(), "Deprecated"))
        .map(SuggestedFix::delete)
        .ifPresent(description::addFix);
    return description.build();
  }
}
