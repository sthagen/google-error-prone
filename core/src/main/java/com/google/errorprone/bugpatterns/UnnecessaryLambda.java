/*
 * Copyright 2019 The Error Prone Authors.
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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.CharMatcher.inRange;
import static com.google.common.base.CharMatcher.is;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.fixes.SuggestedFixes.prettyType;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.enclosingPackage;
import static com.google.errorprone.util.ASTHelpers.getModifiers;
import static com.google.errorprone.util.ASTHelpers.getReceiver;
import static com.google.errorprone.util.ASTHelpers.getStartPosition;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isStatic;
import static com.sun.tools.javac.util.Position.NOPOS;
import static java.util.stream.Collectors.joining;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types.FunctionDescriptorLookupError;
import java.util.Objects;
import java.util.function.Predicate;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary =
        "Returning a lambda from a helper method or saving it in a constant is unnecessary; prefer"
            + " to implement the functional interface method directly and use a method reference"
            + " instead.",
    severity = WARNING)
public class UnnecessaryLambda extends BugChecker
    implements MethodTreeMatcher, VariableTreeMatcher {

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    if (!tree.getParameters().isEmpty() || !tree.getThrows().isEmpty()) {
      return NO_MATCH;
    }
    LambdaExpressionTree lambda = LAMBDA_VISITOR.visit(tree.getBody(), null);
    if (lambda == null) {
      return NO_MATCH;
    }
    MethodSymbol sym = getSymbol(tree);
    if (!ASTHelpers.canBeRemoved(sym, state) || ASTHelpers.shouldKeep(tree)) {
      return NO_MATCH;
    }
    SuggestedFix.Builder fix = SuggestedFix.builder();
    String name = tree.getName().toString();
    Tree type = tree.getReturnType();
    if (!canFix(type, sym, state)) {
      return NO_MATCH;
    }
    if (state.isAndroidCompatible()) {
      return NO_MATCH;
    }
    boolean[] usedInEnhancedForLoop = {false};
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {

        if (Objects.equals(getSymbol(node), sym)) {
          Tree parent = getCurrentPath().getParentPath().getLeaf();
          if (parent instanceof EnhancedForLoopTree enhancedForLoopTree
              && enhancedForLoopTree.getExpression().equals(node)) {
            usedInEnhancedForLoop[0] = true;
          } else {
            replaceUseWithMethodReference(fix, node, name, state.withPath(getCurrentPath()));
          }
        }
        return super.visitMethodInvocation(node, null);
      }
    }.scan(state.getPath().getCompilationUnit(), null);
    if (usedInEnhancedForLoop[0]) {
      return NO_MATCH;
    }
    lambdaToMethod(state, lambda, fix, name, type);
    return describeMatch(tree, fix.build());
  }

  private static final CharMatcher UPPER_CASE = inRange('A', 'Z').or(is('_')).or(inRange('0', '9'));

  @Override
  public Description matchVariable(VariableTree tree, VisitorState state) {
    if (tree.getInitializer() == null) {
      return NO_MATCH;
    }
    LambdaExpressionTree lambda = LAMBDA_VISITOR.visit(tree.getInitializer(), null);
    if (lambda == null) {
      return NO_MATCH;
    }
    Symbol sym = getSymbol(tree);
    if (sym.getKind() != ElementKind.FIELD
        || !sym.isPrivate()
        || !sym.getModifiers().contains(Modifier.FINAL)) {
      return NO_MATCH;
    }
    if (ASTHelpers.hasAnnotation(tree, "com.google.inject.testing.fieldbinder.Bind", state)) {
      return NO_MATCH;
    }
    Tree type = tree.getType();
    if (!canFix(type, sym, state)) {
      return NO_MATCH;
    }
    if (state.isAndroidCompatible()) {
      return NO_MATCH;
    }
    SuggestedFix.Builder fix = SuggestedFix.builder();
    String varName = tree.getName().toString();
    // NOTE: if https://github.com/google/guava/issues/2212 gets resolved, we could use it here.
    String methodName =
        (isStatic(sym) && UPPER_CASE.matchesAllOf(varName))
            ? UPPER_UNDERSCORE.converterTo(LOWER_CAMEL).convert(varName)
            : varName;

    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        if (Objects.equals(getSymbol(node), sym)) {
          replaceUseWithMethodReference(fix, node, methodName, state.withPath(getCurrentPath()));
        }
        return super.visitMemberSelect(node, null);
      }

      @Override
      public Void visitIdentifier(IdentifierTree node, Void unused) {
        if (Objects.equals(getSymbol(node), sym)) {
          replaceUseWithMethodReference(fix, node, methodName, state.withPath(getCurrentPath()));
        }
        return super.visitIdentifier(node, null);
      }
    }.scan(state.getPath().getCompilationUnit(), null);
    SuggestedFixes.removeModifiers(tree, state, Modifier.FINAL).ifPresent(fix::merge);
    lambdaToMethod(state, lambda, fix, methodName, type);
    return describeMatch(tree, fix.build());
  }

  // Allowlist of core packages to emit fixes for functional interfaces in. User-defined functional
  // interfaces are slightly more likely to have documentation value.
  private static final ImmutableSet<String> PACKAGES_TO_FIX =
      ImmutableSet.of(
          "com.google.common.base",
          "com.google.errorprone.matchers",
          "java.util.function",
          "java.lang");

  /**
   * Check if the only methods invoked on the functional interface type are the descriptor method,
   * e.g. don't rewrite uses of {@link Predicate} in compilation units that call other methods like
   * {#link Predicate#add}.
   */
  private boolean canFix(Tree type, Symbol sym, VisitorState state) {
    Symbol descriptor;
    try {
      descriptor = state.getTypes().findDescriptorSymbol(getType(type).asElement());
    } catch (FunctionDescriptorLookupError e) {
      return false;
    }
    if (!PACKAGES_TO_FIX.contains(enclosingPackage(descriptor).getQualifiedName().toString())) {
      return false;
    }
    class Scanner extends TreePathScanner<Void, Void> {

      boolean fixable = true;
      boolean inInitializer = false;

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        check(node);
        return super.visitMethodInvocation(node, null);
      }

      @Override
      public Void visitVariable(VariableTree node, Void unused) {
        boolean wasInInitializer = inInitializer;
        if (sym.equals(getSymbol(node))) {
          inInitializer = true;
        }
        super.visitVariable(node, null);
        inInitializer = wasInInitializer;
        return null;
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        if (inInitializer && sym.equals(getSymbol(node))) {
          // We're not smart enough to rewrite a recursive lambda.
          fixable = false;
        }
        return super.visitMemberSelect(node, null);
      }

      private void check(MethodInvocationTree node) {
        ExpressionTree lhs = node.getMethodSelect();
        if (!(lhs instanceof MemberSelectTree memberSelectTree)) {
          return;
        }
        ExpressionTree receiver = memberSelectTree.getExpression();
        if (!Objects.equals(sym, getSymbol(receiver))) {
          return;
        }
        Symbol symbol = getSymbol(lhs);
        if (Objects.equals(descriptor, symbol)) {
          return;
        }
        fixable = false;
      }
    }
    Scanner scanner = new Scanner();
    scanner.scan(state.getPath().getCompilationUnit(), null);
    return scanner.fixable;
  }

  private void lambdaToMethod(
      VisitorState state,
      LambdaExpressionTree lambda,
      SuggestedFix.Builder fix,
      String name,
      Tree type) {
    Type fi = state.getTypes().findDescriptorType(getType(type));
    Tree tree = state.getPath().getLeaf();
    ModifiersTree modifiers = getModifiers(tree);
    int endPosition = state.getEndPosition(tree);
    StringBuilder replacement = new StringBuilder();
    replacement.append(String.format(" %s %s(", prettyType(state, fix, fi.getReturnType()), name));
    replacement.append(
        Streams.zip(
                fi.getParameterTypes().stream(),
                lambda.getParameters().stream(),
                (t, p) -> String.format("%s %s", prettyType(state, fix, t), p.getName()))
            .collect(joining(", ")));
    replacement.append(")");
    if (lambda.getBody() instanceof BlockTree) {
      replacement.append(state.getSourceForNode(lambda.getBody()));
    } else {
      replacement.append("{");
      if (!fi.getReturnType().hasTag(TypeTag.VOID)) {
        replacement.append("return ");
      }
      replacement.append(state.getSourceForNode(lambda.getBody()));
      replacement.append(";");
      replacement.append("}");
    }
    int modifiedEndPos = state.getEndPosition(modifiers);
    fix.replace(
        modifiedEndPos == NOPOS ? getStartPosition(tree) : modifiedEndPos + 1,
        endPosition,
        replacement.toString());
  }

  private static void replaceUseWithMethodReference(
      SuggestedFix.Builder fix, ExpressionTree node, String newName, VisitorState state) {
    Tree parent = state.getPath().getParentPath().getLeaf();
    if (parent instanceof MemberSelectTree memberSelectTree
        && memberSelectTree.getExpression().equals(node)) {
      Tree receiver = node instanceof IdentifierTree ? null : getReceiver(node);
      fix.replace(
          receiver != null ? state.getEndPosition(receiver) : getStartPosition(node),
          state.getEndPosition(parent),
          (receiver != null ? "." : "") + newName);
    } else {
      Symbol sym = getSymbol(node);
      String receiverCode;
      if (node instanceof MethodInvocationTree && getReceiver(node) != null) {
        receiverCode = state.getSourceForNode(getReceiver(node));
      } else {
        receiverCode = isStatic(sym) ? enclosingClass(sym).getSimpleName().toString() : "this";
      }
      fix.replace(node, String.format("%s::%s", receiverCode, newName));
    }
  }

  private static final SimpleTreeVisitor<LambdaExpressionTree, Void> LAMBDA_VISITOR =
      new SimpleTreeVisitor<LambdaExpressionTree, Void>() {
        @Override
        public LambdaExpressionTree visitLambdaExpression(LambdaExpressionTree node, Void unused) {
          return node;
        }

        @Override
        public @Nullable LambdaExpressionTree visitBlock(BlockTree node, Void unused) {
          // when processing a method body, only consider methods with a single `return` statement
          // that returns a method
          return node.getStatements().size() == 1
              ? getOnlyElement(node.getStatements()).accept(this, null)
              : null;
        }

        @Override
        public @Nullable LambdaExpressionTree visitReturn(ReturnTree node, Void unused) {
          return node.getExpression() != null ? node.getExpression().accept(this, null) : null;
        }

        @Override
        public LambdaExpressionTree visitTypeCast(TypeCastTree node, Void unused) {
          return node.getExpression().accept(this, null);
        }

        @Override
        public LambdaExpressionTree visitParenthesized(ParenthesizedTree node, Void unused) {
          return node.getExpression().accept(this, null);
        }
      };
}
