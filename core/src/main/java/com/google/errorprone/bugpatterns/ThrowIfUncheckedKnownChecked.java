/*
 * Copyright 2016 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.fixes.SuggestedFix.delete;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.argument;
import static com.google.errorprone.matchers.Matchers.argumentCount;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import javax.lang.model.type.UnionType;

/** A BugPattern; see the summary. */
@BugPattern(summary = "throwIfUnchecked(knownCheckedException) is a no-op.", severity = ERROR)
public class ThrowIfUncheckedKnownChecked extends BugChecker
    implements MethodInvocationTreeMatcher {

  private static final Matcher<MethodInvocationTree> IS_THROW_IF_UNCHECKED =
      allOf(
          anyOf(
              staticMethod().onClass("com.google.common.base.Throwables").named("throwIfUnchecked"),
              staticMethod()
                  .onClass("com.google.common.base.Throwables")
                  .named("propagateIfPossible")),
          argumentCount(1));

  private static final Matcher<ExpressionTree> IS_KNOWN_CHECKED_EXCEPTION =
      new Matcher<ExpressionTree>() {
        @Override
        public boolean matches(ExpressionTree tree, VisitorState state) {
          Type type = ASTHelpers.getType(tree);
          if (type.isUnion()) {
            return ((UnionType) type)
                .getAlternatives().stream().allMatch(t -> isKnownCheckedException(state, (Type) t));
          } else {
            return isKnownCheckedException(state, type);
          }
        }

        boolean isKnownCheckedException(VisitorState state, Type type) {
          Types types = state.getTypes();
          Symtab symtab = state.getSymtab();
          // Check erasure for generics.
          // TODO(cpovirk): Is that necessary here or in ThrowIfUncheckedKnownUnchecked?
          type = types.erasure(type);
          return
          // Has to be some Exception: A variable of type Throwable might be an Error.
          types.isSubtype(type, symtab.exceptionType)
              // Has to be some subtype: A variable of type Exception might be a RuntimeException.
              && !types.isSameType(type, symtab.exceptionType)
              // Can't be of type RuntimeException.
              && !types.isSubtype(type, symtab.runtimeExceptionType);
        }
      };

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (IS_THROW_IF_UNCHECKED.matches(tree, state)
        && argument(0, IS_KNOWN_CHECKED_EXCEPTION).matches(tree, state)) {
      return describeMatch(tree, delete(state.getPath().getParentPath().getLeaf()));
    }
    return NO_MATCH;
  }
}
