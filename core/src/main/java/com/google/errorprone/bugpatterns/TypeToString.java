/*
 * Copyright 2020 The Error Prone Authors.
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

import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.toType;
import static com.google.errorprone.predicates.TypePredicates.isDescendantOf;
import static com.google.errorprone.util.ASTHelpers.isBugCheckerCode;

import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.predicates.TypePredicate;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Flags {@code javax.lang.model.type.TypeMirror#toString} usage in {@link BugChecker}s.
 *
 * @author bhagwani@google.com (Sumit Bhagwani)
 */
@BugPattern(
    summary =
        "TypeMirror#toString shouldn't be used for comparison as it is expensive and fragile.",
    severity = SUGGESTION)
public class TypeToString extends AbstractToString {

  private static final TypePredicate IS_TYPE = isDescendantOf("javax.lang.model.type.TypeMirror");

  private static final Matcher<Tree> STRING_EQUALS =
      toType(
          MemberSelectTree.class,
          instanceMethod().onExactClass("java.lang.String").named("equals"));

  private static boolean typeToStringInBugChecker(Type type, VisitorState state) {
    if (!isBugCheckerCode(state)) {
      return false;
    }
    Tree parentTree = state.getPath().getParentPath().getLeaf();
    return IS_TYPE.apply(type, state) && STRING_EQUALS.matches(parentTree, state);
  }

  @Inject
  TypeToString(ErrorProneFlags flags) {
    super(flags);
  }

  @Override
  protected TypePredicate typePredicate() {
    return TypeToString::typeToStringInBugChecker;
  }

  @Override
  protected Optional<String> descriptionMessageForDefaultMatch(Type type, VisitorState state) {
    return Optional.of("TypeMirror#toString shouldn't be used as it is expensive and fragile.");
  }

  @Override
  protected Optional<Fix> implicitToStringFix(ExpressionTree tree, VisitorState state) {
    return Optional.empty();
  }

  @Override
  protected Optional<Fix> toStringFix(Tree parent, ExpressionTree tree, VisitorState state) {
    return Optional.empty();
  }
}
