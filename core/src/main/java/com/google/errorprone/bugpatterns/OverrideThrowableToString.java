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

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;

/**
 * Warns against overriding toString() in a Throwable class and suggests getMessage()
 *
 * @author mariasam@google.com (Maria Sam)
 */
@BugPattern(
    summary =
        "To return a custom message with a Throwable class, one should "
            + "override getMessage() instead of toString().",
    severity = WARNING)
public final class OverrideThrowableToString extends BugChecker implements MethodTreeMatcher {

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    if (!Matchers.toStringMethodDeclaration().matches(methodTree, state)) {
      return NO_MATCH;
    }
    ClassTree classTree = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
    if (!isSubtype(getType(classTree), state.getSymtab().throwableType, state)) {
      return NO_MATCH;
    }
    if (classTree.getMembers().stream()
        .filter(m -> m instanceof MethodTree)
        .map(m -> (MethodTree) m)
        .anyMatch(m -> m.getName().contentEquals("getMessage"))) {
      return NO_MATCH;
    }
    return describeMatch(methodTree, SuggestedFixes.renameMethod(methodTree, "getMessage", state));
  }
}
