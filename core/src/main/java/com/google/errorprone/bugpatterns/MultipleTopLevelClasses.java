/*
 * Copyright 2015 The Error Prone Authors.
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

import static com.google.errorprone.BugPattern.LinkType.CUSTOM;
import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;
import static com.google.errorprone.matchers.Description.NO_MATCH;

import com.google.common.base.Joiner;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.List;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    altNames = {"TopLevel"},
    summary = "Source files should not contain multiple top-level class declarations",
    severity = SUGGESTION,
    documentSuppression = false,
    linkType = CUSTOM,
    tags = StandardTags.STYLE,
    link = "https://google.github.io/styleguide/javaguide.html#s3.4.1-one-top-level-class")
public final class MultipleTopLevelClasses extends BugChecker
    implements CompilationUnitTreeMatcher {

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    List<String> names = new ArrayList<>();
    for (Tree member : tree.getTypeDecls()) {
      if (member instanceof ClassTree classMember) {
        if (isSuppressed(classMember, state)) {
          // If any top-level classes have @SuppressWarnings("TopLevel"), ignore
          // this compilation unit. We can't rely on the normal suppression
          // mechanism because the only enclosing element is the package declaration,
          // and @SuppressWarnings can't be applied to packages.
          return NO_MATCH;
        }
        names.add(classMember.getSimpleName().toString());
      }
    }
    if (names.size() <= 1) {
      return NO_MATCH;
    }
    String message =
        String.format(
            "Expected at most one top-level class declaration, instead found: %s",
            Joiner.on(", ").join(names));
    for (Tree typeDecl : tree.getTypeDecls()) {
      state.reportMatch(buildDescription(typeDecl).setMessage(message).build());
    }
    return NO_MATCH;
  }
}
