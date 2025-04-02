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

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.BinaryTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @author Sumit Bhagwani (bhagwani@google.com)
 */
@BugPattern(
    summary = "Non-trivial compile time constant boolean expressions shouldn't be used.",
    severity = WARNING)
public class ComplexBooleanConstant extends BugChecker implements BinaryTreeMatcher {

  @Override
  public Description matchBinary(BinaryTree tree, VisitorState state) {
    Boolean constValue = booleanValue(tree);
    if (constValue == null) {
      return Description.NO_MATCH;
    }
    return buildDescription(tree)
        .addFix(SuggestedFix.replace(tree, constValue.toString()))
        .setMessage(
            String.format(
                "This expression always evaluates to `%s`, prefer a boolean literal for clarity.",
                constValue))
        .build();
  }

  @Nullable Boolean booleanValue(BinaryTree tree) {
    if (tree.getLeftOperand() instanceof JCLiteral && tree.getRightOperand() instanceof JCLiteral) {
      return ASTHelpers.constValue(tree, Boolean.class);
    }
    // Handle `&& false` and `|| true` even if the other operand is a non-trivial constant
    // (including constant fields).
    SimpleTreeVisitor<Boolean, Void> boolValue =
        new SimpleTreeVisitor<Boolean, Void>() {
          @Override
          public @Nullable Boolean visitLiteral(LiteralTree node, Void unused) {
            if (node.getValue() instanceof Boolean b) {
              return b;
            }
            return null;
          }

          @Override
          public @Nullable Boolean visitUnary(UnaryTree node, Void unused) {
            Boolean r = node.getExpression().accept(this, null);
            if (r == null) {
              return null;
            }
            return switch (node.getKind()) {
              case LOGICAL_COMPLEMENT -> !r;
              default -> null;
            };
          }
        };
    Boolean lhs = tree.getLeftOperand().accept(boolValue, null);
    Boolean rhs = tree.getRightOperand().accept(boolValue, null);
    switch (tree.getKind()) {
      case CONDITIONAL_AND, AND -> {
        if (lhs != null && rhs != null) {
          return lhs && rhs;
        }
        if (Objects.equals(lhs, Boolean.FALSE) || Objects.equals(rhs, Boolean.FALSE)) {
          return false;
        }
      }
      case CONDITIONAL_OR, OR -> {
        if (lhs != null && rhs != null) {
          return lhs || rhs;
        }
        if (Objects.equals(lhs, Boolean.TRUE) || Objects.equals(rhs, Boolean.TRUE)) {
          return true;
        }
      }
      default -> {}
    }
    return null;
  }
}
