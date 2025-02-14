/*
 * Copyright 2018 The Error Prone Authors.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.streamReceivers;

import com.google.common.base.Ascii;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Detects usages of Truth assertions with the expected and actual values reversed.
 *
 * @author ghm@google.com (Graeme Morgan)
 */
@BugPattern(
    summary =
        "The actual and expected values appear to be swapped, which results in poor assertion "
            + "failure messages. The actual value should come first.",
    severity = WARNING,
    tags = StandardTags.STYLE)
public final class TruthAssertExpected extends BugChecker implements MethodInvocationTreeMatcher {

  private static final String TRUTH = "com.google.common.truth.Truth";
  private static final String PROTOTRUTH = "com.google.common.truth.extensions.proto.ProtoTruth";

  private static final Matcher<ExpressionTree> TRUTH_ASSERTTHAT =
      staticMethod().onClassAny(TRUTH, PROTOTRUTH).named("assertThat");

  private static final Matcher<ExpressionTree> TRUTH_VERB =
      instanceMethod()
          .onDescendantOf("com.google.common.truth.StandardSubjectBuilder")
          .named("that");

  private static final Matcher<ExpressionTree> ASSERTION = anyOf(TRUTH_ASSERTTHAT, TRUTH_VERB);

  /**
   * A heuristic for whether a method may have been invoked with an expected value.
   *
   * <p>We match cases such as {@code assertThat(expectedFoo)}, {@code
   * assertThat(expectedBar.id())}, but not if the identifier extends {@link Throwable} (as this is
   * often named {@code expectedException} or similar).
   */
  private static boolean expectedValue(ExpressionTree tree, VisitorState state) {
    if (!(tree instanceof MethodInvocationTree methodTree)) {
      return false;
    }
    IdentifierTree identifier = getRootIdentifier(getOnlyElement(methodTree.getArguments()));
    if (identifier == null) {
      return false;
    }
    Type throwable = Suppliers.typeFromClass(Throwable.class).get(state);
    String identifierName = Ascii.toLowerCase(identifier.getName().toString());
    return identifierName.contains("expected")
        && !identifierName.contains("actual")
        && !ASTHelpers.isSubtype(ASTHelpers.getType(identifier), throwable, state);
  }

  private static final Matcher<ExpressionTree> ASSERT_ON_EXPECTED =
      allOf(ASSERTION, TruthAssertExpected::expectedValue);

  /** Truth assertion chains which are commutative. Many are not, such as {@code containsAny}. */
  private static final Matcher<ExpressionTree> REVERSIBLE_TERMINATORS =
      anyOf(
          instanceMethod()
              .onDescendantOf("com.google.common.truth.Subject")
              .namedAnyOf(
                  "isEqualTo",
                  "isNotEqualTo",
                  "isSameAs",
                  "isNotSameAs",
                  "containsExactlyElementsIn"),
          instanceMethod()
              .onDescendantOf("com.google.common.truth.MapSubject")
              .named("containsExactlyEntriesIn"),
          instanceMethod()
              .onDescendantOf("com.google.common.truth.FloatSubject.TolerantFloatComparison")
              .named("of"),
          instanceMethod()
              .onDescendantOf("com.google.common.truth.DoubleSubject.TolerantDoubleComparison")
              .named("of"));

  /**
   * Matches expressions that look like they should be considered constant, i.e. {@code
   * ImmutableList.of(1, 2)}, {@code Long.valueOf(10L)}.
   */
  private static boolean isConstantCreator(ExpressionTree tree) {
    List<? extends ExpressionTree> arguments =
        tree.accept(
            new SimpleTreeVisitor<List<? extends ExpressionTree>, Void>() {
              @Override
              public List<? extends ExpressionTree> visitNewClass(NewClassTree node, Void unused) {
                return node.getArguments();
              }

              @Override
              public @Nullable List<? extends ExpressionTree> visitMethodInvocation(
                  MethodInvocationTree node, Void unused) {
                MethodSymbol symbol = ASTHelpers.getSymbol(node);
                if (!symbol.isStatic()) {
                  return null;
                }
                return node.getArguments();
              }
            },
            null);
    return arguments != null
        && arguments.stream().allMatch(argument -> ASTHelpers.constValue(argument) != null);
  }

  /** Matcher for a reversible assertion that appears to be the wrong way around. */
  private static final Matcher<ExpressionTree> MATCH =
      allOf(REVERSIBLE_TERMINATORS, hasReceiverMatching(ASSERT_ON_EXPECTED));

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (!MATCH.matches(tree, state)) {
      return Description.NO_MATCH;
    }
    if (expectedValue(tree, state)) {
      return Description.NO_MATCH;
    }
    ExpressionTree assertion = findReceiverMatching(tree, state, ASSERT_ON_EXPECTED);
    if (!(assertion instanceof MethodInvocationTree methodInvocationTree)) {
      return Description.NO_MATCH;
    }
    ExpressionTree assertedArgument = getOnlyElement(methodInvocationTree.getArguments());
    ExpressionTree terminatingArgument = getOnlyElement(tree.getArguments());
    // To avoid some false positives, skip anything where the terminating value is already a
    // compile-time constant.
    if (ASTHelpers.constValue(terminatingArgument) != null
        || Matchers.staticFieldAccess().matches(terminatingArgument, state)
        || isConstantCreator(terminatingArgument)) {
      return Description.NO_MATCH;
    }
    SuggestedFix fix = SuggestedFix.swap(assertedArgument, terminatingArgument, state);
    if (SuggestedFixes.compilesWithFix(fix, state)) {
      return describeMatch(tree, fix);
    }
    return describeMatch(tree);
  }

  private static Matcher<ExpressionTree> hasReceiverMatching(Matcher<ExpressionTree> matcher) {
    return (tree, state) -> findReceiverMatching(tree, state, matcher) != null;
  }

  /**
   * Walks up the receivers for an expression chain until one matching {@code matcher} is found, and
   * returns the matched {@link ExpressionTree}, or {@code null} if none match.
   */
  private static @Nullable ExpressionTree findReceiverMatching(
      ExpressionTree tree, VisitorState state, Matcher<ExpressionTree> matcher) {
    return streamReceivers(tree).filter(t -> matcher.matches(t, state)).findFirst().orElse(null);
  }

  /**
   * Walks up the provided {@link ExpressionTree} to find the {@link IdentifierTree identifier} that
   * it stems from. Returns {@code null} if the tree doesn't terminate in an identifier.
   */
  private static @Nullable IdentifierTree getRootIdentifier(ExpressionTree tree) {
    if (tree == null) {
      return null;
    }
    return switch (tree.getKind()) {
      case IDENTIFIER -> (IdentifierTree) tree;
      case MEMBER_SELECT, METHOD_INVOCATION -> getRootIdentifier(ASTHelpers.getReceiver(tree));
      default -> null;
    };
  }
}
