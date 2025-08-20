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

package com.google.errorprone.bugpatterns.flogger;

import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.util.ASTHelpers.streamReceivers;

import com.google.common.collect.Lists;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import javax.lang.model.type.TypeKind;
import org.jspecify.annotations.Nullable;

/**
 * Detects Flogger log statements that pass Exceptions to the log method instead of using withCause.
 */
@BugPattern(
    summary = "Use withCause to associate Exceptions with log statements",
    severity = BugPattern.SeverityLevel.WARNING)
public class FloggerWithoutCause extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

  private static final Matcher<ExpressionTree> LOG_METHOD =
      instanceMethod().onDescendantOf("com.google.common.flogger.LoggingApi").named("log");

  private static final Matcher<ExpressionTree> WITH_CAUSE =
      instanceMethod().onDescendantOf("com.google.common.flogger.LoggingApi").named("withCause");

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (!LOG_METHOD.matches(tree, state)) {
      return Description.NO_MATCH;
    }

    Tree exception = getExceptionArg(tree, state);
    if (exception == null) {
      return Description.NO_MATCH;
    }

    if (streamReceivers(tree).anyMatch(receiver -> WITH_CAUSE.matches(receiver, state))) {
      return Description.NO_MATCH;
    }

    return describeMatch(
        tree,
        SuggestedFix.postfixWith(
            ASTHelpers.getReceiver(tree),
            String.format(".withCause(%s)", state.getSourceForNode(exception))));
  }

  private static @Nullable Tree getExceptionArg(MethodInvocationTree tree, VisitorState state) {
    for (Tree arg : Lists.reverse(tree.getArguments())) {
      try {
        Type argType = ASTHelpers.getType(arg);
        if (argType != null
            && argType.getKind() != TypeKind.NULL
            && ASTHelpers.isSubtype(argType, state.getSymtab().throwableType, state)) {
          return arg;
        }
      } catch (RuntimeException t) {
        // ignore completion failures
      }
    }
    return null;
  }
}
