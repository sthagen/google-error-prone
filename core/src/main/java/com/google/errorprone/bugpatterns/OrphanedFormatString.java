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

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.toType;
import static com.google.errorprone.matchers.method.MethodMatchers.constructor;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.findMatchingMethods;
import static com.google.errorprone.util.ASTHelpers.getReceiverType;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.AnnotationNames.FORMAT_METHOD_ANNOTATION;
import static com.google.errorprone.util.AnnotationNames.FORMAT_STRING_ANNOTATION;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.LiteralTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.predicates.TypePredicates;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.MissingFormatArgumentException;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary = "String literal contains format specifiers, but is not passed to a format method",
    severity = WARNING)
public class OrphanedFormatString extends BugChecker implements LiteralTreeMatcher {

  /** Method calls that developers commonly incorrectly assume to accept format arguments. */
  private static final Matcher<Tree> LIKELY_MISTAKE_METHOD_CALL =
      toType(
          ExpressionTree.class,
          anyOf(
              constructor().forClass("java.lang.StringBuilder"),
              allOf(
                  constructor()
                      .forClass(TypePredicates.isDescendantOf(s -> s.getSymtab().throwableType)),
                  (tree, state) -> {
                    Symbol sym = getSymbol(tree);
                    return sym instanceof MethodSymbol methodSymbol && !methodSymbol.isVarArgs();
                  }),
              instanceMethod()
                  .onDescendantOfAny("java.io.PrintStream", "java.io.PrintWriter")
                  .namedAnyOf("print", "println"),
              instanceMethod()
                  .onExactClass("java.lang.StringBuilder")
                  .named("append")
                  .withParameters("java.lang.CharSequence", "int", "int"),
              staticMethod()
                  .onClass("com.google.common.truth.Truth")
                  .named("assertWithMessage")
                  .withParameters("java.lang.String"),
              instanceMethod()
                  .onDescendantOf("com.google.common.flogger.LoggingApi")
                  .named("log")
                  .withParameters("java.lang.String"),
              (t, s) ->
                  t instanceof MethodInvocationTree
                      && !findMatchingMethods(
                              getSymbol(t).name,
                              ms ->
                                  hasAnnotation(ms, FORMAT_METHOD_ANNOTATION, s)
                                      || ms.getParameters().stream()
                                          .anyMatch(
                                              vs -> hasAnnotation(vs, FORMAT_STRING_ANNOTATION, s)),
                              getReceiverType(t),
                              s.getTypes())
                          .isEmpty()));

  @Override
  public Description matchLiteral(LiteralTree tree, VisitorState state) {
    Object value = tree.getValue();
    if (!(value instanceof String string)) {
      return NO_MATCH;
    }
    if (!missingFormatArgs(string)) {
      return NO_MATCH;
    }
    Tree methodInvocation = state.getPath().getParentPath().getLeaf();
    if (!LIKELY_MISTAKE_METHOD_CALL.matches(methodInvocation, state)) {
      return NO_MATCH;
    }

    // If someone has added new API methods to a subtype of the commonly-misused classes, we can
    // check to see if they made it @FormatMethod and the format-string slots in correctly.
    if (methodInvocation instanceof MethodInvocationTree methodInvocationTree
        && literalIsFormatMethodArg(tree, methodInvocationTree, state)) {
      return NO_MATCH;
    }

    return describeMatch(tree);
  }

  private static boolean literalIsFormatMethodArg(
      LiteralTree tree, MethodInvocationTree methodInvocationTree, VisitorState state) {
    MethodSymbol symbol = getSymbol(methodInvocationTree);
    if (hasAnnotation(symbol, FORMAT_METHOD_ANNOTATION, state)) {
      int indexOfParam = findIndexOfFormatStringParameter(state, symbol);
      if (indexOfParam != -1) {
        List<? extends ExpressionTree> args = methodInvocationTree.getArguments();
        // This *shouldn't* be a problem, since this means that the format string is in a varargs
        // position in a @FormatMethod declaration and the invocation contained no varargs
        // arguments, but it doesn't hurt to check.
        return args.size() > indexOfParam && args.get(indexOfParam) == tree;
      }
    }
    return false;
  }

  private static int findIndexOfFormatStringParameter(VisitorState state, MethodSymbol symbol) {
    // Find a parameter with @FormatString, if none, use the first String parameter
    int indexOfFirstString = -1;
    List<VarSymbol> params = symbol.params();
    for (int i = 0; i < params.size(); i++) {
      VarSymbol varSymbol = params.get(i);
      if (hasAnnotation(varSymbol, FORMAT_STRING_ANNOTATION, state)) {
        return i;
      }
      if (indexOfFirstString == -1
          && ASTHelpers.isSameType(varSymbol.type, state.getSymtab().stringType, state)) {
        indexOfFirstString = i;
      }
    }

    return indexOfFirstString;
  }

  /** Returns true for strings that contain format specifiers. */
  @SuppressWarnings("StringFormatWithoutAnyParams")
  private static boolean missingFormatArgs(String value) {
    try {
      String unused = String.format(value);
    } catch (MissingFormatArgumentException e) {
      return true;
    } catch (IllegalFormatException ignored) {
      // we don't care about other errors (it isn't supposed to be a format string)
    }
    return false;
  }
}
