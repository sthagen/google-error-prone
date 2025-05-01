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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.isSameType;

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.threadsafety.ConstantExpressions;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary = "Unsynchronized method overrides a synchronized method.",
    severity = WARNING,
    tags = StandardTags.FRAGILE_CODE)
public class UnsynchronizedOverridesSynchronized extends BugChecker implements MethodTreeMatcher {
  private final ConstantExpressions constantExpressions;

  @Inject
  UnsynchronizedOverridesSynchronized(ConstantExpressions constantExpressions) {
    this.constantExpressions = constantExpressions;
  }

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);

    if (isSynchronized(methodSymbol)) {
      return NO_MATCH;
    }
    for (MethodSymbol s : ASTHelpers.findSuperMethods(methodSymbol, state.getTypes())) {
      if (isSynchronized(s)) {
        // Input streams are typically not used across threads, so this case isn't
        // worth enforcing.
        if (isSameType(s.owner.type, JAVA_IO_INPUTSTREAM.get(state), state)) {
          continue;
        }
        if (ignore(methodTree, state)) {
          return NO_MATCH;
        }
        return buildDescription(methodTree)
            .addFix(
                SuggestedFixes.addModifiers(methodTree, state, Modifier.SYNCHRONIZED)
                    .orElse(SuggestedFix.emptyFix()))
            .setMessage(
                String.format(
                    "Unsynchronized method %s overrides synchronized method in %s",
                    methodSymbol.getSimpleName(), s.enclClass().getSimpleName()))
            .build();
      }
    }
    return NO_MATCH;
  }

  private static boolean isSynchronized(MethodSymbol sym) {
    return sym.getModifiers().contains(Modifier.SYNCHRONIZED);
  }

  /**
   * Don't flag methods that are empty, trivially delegate to a super-implementation, or return a
   * constant.
   */
  private boolean ignore(MethodTree method, VisitorState state) {
    return firstNonNull(
        new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitBlock(BlockTree tree, Void unused) {
            return switch (tree.getStatements().size()) {
              case 0 -> true;
              case 1 -> scan(getOnlyElement(tree.getStatements()), null);
              default -> false;
            };
          }

          @Override
          public Boolean visitReturn(ReturnTree tree, Void unused) {
            ExpressionTree expression = tree.getExpression();
            if (expression == null
                || constantExpressions.constantExpression(expression, state).isPresent()) {
              return true;
            }
            return scan(tree.getExpression(), null);
          }

          @Override
          public Boolean visitExpressionStatement(ExpressionStatementTree tree, Void unused) {
            return scan(tree.getExpression(), null);
          }

          @Override
          public Boolean visitTypeCast(TypeCastTree tree, Void unused) {
            return scan(tree.getExpression(), null);
          }

          @Override
          public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
            ExpressionTree receiver = ASTHelpers.getReceiver(node);
            return receiver instanceof IdentifierTree identifierTree
                && identifierTree.getName().contentEquals("super")
                && overrides(ASTHelpers.getSymbol(method), ASTHelpers.getSymbol(node));
          }

          private boolean overrides(MethodSymbol sym, MethodSymbol other) {
            return !sym.isStatic()
                && !other.isStatic()
                && (((sym.flags() | other.flags()) & Flags.SYNTHETIC) == 0)
                && sym.name.contentEquals(other.name)
                && sym.overrides(
                    other, enclosingClass(sym), state.getTypes(), /* checkResult= */ false);
          }
        }.scan(method.getBody(), null),
        false);
  }

  private static final Supplier<Type> JAVA_IO_INPUTSTREAM =
      VisitorState.memoize(state -> state.getTypeFromString("java.io.InputStream"));
}
