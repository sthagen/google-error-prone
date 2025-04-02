/*
 * Copyright 2022 The Error Prone Authors.
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

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static java.lang.annotation.ElementType.TYPE_PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

/** A bugpattern; see the summary. */
@BugPattern(
    summary =
        "Type-use nullability annotations should annotate the inner class, not the outer class"
            + " (e.g., write `A.@Nullable B` instead of `@Nullable A.B`).",
    severity = ERROR)
public final class NullableOnContainingClass extends BugChecker
    implements MemberSelectTreeMatcher, MethodTreeMatcher, VariableTreeMatcher {
  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    if (!(tree.getExpression() instanceof AnnotatedTypeTree annotatedTypeTree)) {
      return NO_MATCH;
    }
    return handle(annotatedTypeTree.getAnnotations(), tree, state);
  }

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    return handle(tree.getModifiers().getAnnotations(), tree.getReturnType(), state);
  }

  @Override
  public Description matchVariable(VariableTree tree, VisitorState state) {
    return handle(tree.getModifiers().getAnnotations(), tree.getType(), state);
  }

  private Description handle(
      List<? extends AnnotationTree> annotations, Tree type, VisitorState state) {
    if (!(type instanceof MemberSelectTree memberSelectTree)) {
      return NO_MATCH;
    }
    int endOfOuterType = state.getEndPosition(memberSelectTree.getExpression());

    for (AnnotationTree annotation : annotations) {
      if (!isOnlyTypeAnnotation(getSymbol(annotation))) {
        continue;
      }
      if (NULLABLE_ANNOTATION_NAMES.contains(getType(annotation).tsym.getSimpleName().toString())) {
        if (state.getEndPosition(annotation) < endOfOuterType) {
          return describeMatch(
              annotation,
              SuggestedFix.builder()
                  .delete(annotation)
                  .replace(
                      endOfOuterType + 1,
                      endOfOuterType + 1,
                      state.getSourceForNode(annotation) + " ")
                  .build());
        }
      }
    }
    return NO_MATCH;
  }

  /**
   * True if this annotation is only {@link TYPE_USE}, or only {@link TYPE_USE} and {@link
   * TYPE_PARAMETER}.
   */
  private static boolean isOnlyTypeAnnotation(Symbol anno) {
    Target target = anno.getAnnotation(Target.class);
    ImmutableSet<ElementType> elementTypes =
        target == null ? ImmutableSet.of() : ImmutableSet.copyOf(target.value());
    return elementTypes.contains(TYPE_USE) && TYPE_USE_OR_TYPE_PARAMETER.containsAll(elementTypes);
  }

  private static final ImmutableSet<String> NULLABLE_ANNOTATION_NAMES =
      ImmutableSet.of("Nullable", "NonNull", "NullableType");
  private static final ImmutableSet<ElementType> TYPE_USE_OR_TYPE_PARAMETER =
      ImmutableSet.of(TYPE_USE, TYPE_PARAMETER);
}
