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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getStartPosition;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.bugpatterns.argumentselectiondefects.NamedParameterComment;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.Comments;
import com.google.errorprone.util.ErrorProneComment;
import com.google.errorprone.util.ErrorProneComment.ErrorProneCommentStyle;
import com.google.errorprone.util.ErrorProneToken;
import com.google.errorprone.util.ErrorProneTokens;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.Position;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import javax.inject.Inject;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary =
        "Detects `/* name= */`-style comments on actual parameters where the name doesn't match the"
            + " formal parameter",
    severity = WARNING)
public class ParameterName extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher {

  private final ImmutableList<String> exemptPackages;

  @Inject
  ParameterName(ErrorProneFlags errorProneFlags) {
    this.exemptPackages =
        errorProneFlags.getListOrEmpty("ParameterName:exemptPackagePrefixes").stream()
            // add a trailing '.' so that e.g. com.foo matches as a prefix of com.foo.bar, but not
            // com.foobar
            .map(p -> p.endsWith(".") ? p : p + ".")
            .collect(toImmutableList());
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    checkArguments(tree, tree.getArguments(), state.getEndPosition(tree.getMethodSelect()), state);
    return NO_MATCH;
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {
    checkArguments(tree, tree.getArguments(), state.getEndPosition(tree.getIdentifier()), state);
    return NO_MATCH;
  }

  private void checkArguments(
      Tree tree,
      List<? extends ExpressionTree> arguments,
      int argListStartPosition,
      VisitorState state) {
    if (arguments.isEmpty()) {
      return;
    }
    MethodSymbol sym = (MethodSymbol) ASTHelpers.getSymbol(tree);
    if (NamedParameterComment.containsSyntheticParameterName(sym)) {
      return;
    }
    int start = argListStartPosition;
    if (start == Position.NOPOS) {
      // best effort work-around for https://github.com/google/error-prone/issues/780
      return;
    }
    String enclosingClass = ASTHelpers.enclosingClass(sym).toString();
    if (exemptPackages.stream().anyMatch(enclosingClass::startsWith)) {
      return;
    }
    Iterator<? extends ExpressionTree> argumentIterator = arguments.iterator();
    // For each parameter/argument pair, we tokenize the characters between the end of the
    // previous argument (or the start of the argument list, in the case of the first argument)
    // and the start of the current argument. The `start` variable is advanced each time, stepping
    // over each argument when we finish processing it.
    for (VarSymbol param : sym.getParameters()) {
      if (!argumentIterator.hasNext()) {
        return; // A vararg parameter has zero corresponding arguments passed
      }
      ExpressionTree argument = argumentIterator.next();
      Optional<Range<Integer>> positions = positions(argument, state);
      if (positions.isEmpty()) {
        return;
      }
      start =
          processArgument(
              positions.get(), start, state, tok -> checkArgument(param, argument, tok, state));
    }

    // handle any varargs arguments after the first
    while (argumentIterator.hasNext()) {
      ExpressionTree argument = argumentIterator.next();
      Optional<Range<Integer>> positions = positions(argument, state);
      if (positions.isEmpty()) {
        return;
      }
      start =
          processArgument(positions.get(), start, state, tok -> checkComment(argument, tok, state));
    }
  }

  /** Returns the source span for a tree, or empty if the position information is not available. */
  Optional<Range<Integer>> positions(Tree tree, VisitorState state) {
    int endPosition = state.getEndPosition(tree);
    if (endPosition == Position.NOPOS) {
      return Optional.empty();
    }
    return Optional.of(Range.closedOpen(getStartPosition(tree), endPosition));
  }

  private static int processArgument(
      Range<Integer> positions,
      int offset,
      VisitorState state,
      Consumer<ErrorProneToken> consumer) {
    String source = state.getSourceCode().subSequence(offset, positions.upperEndpoint()).toString();
    Deque<ErrorProneToken> tokens =
        new ArrayDeque<>(ErrorProneTokens.getTokens(source, offset, state.context));
    if (advanceTokens(tokens, positions)) {
      consumer.accept(tokens.removeFirst());
    }
    return positions.upperEndpoint();
  }

  private static boolean advanceTokens(Deque<ErrorProneToken> tokens, Range<Integer> actual) {
    while (!tokens.isEmpty() && tokens.getFirst().pos() < actual.lowerEndpoint()) {
      tokens.removeFirst();
    }
    if (tokens.isEmpty()) {
      return false;
    }
    if (!actual.contains(tokens.getFirst().pos())) {
      return false;
    }
    return true;
  }

  private record FixInfo(
      boolean isFormatCorrect, boolean isNameCorrect, ErrorProneComment comment, String name) {
    static FixInfo create(
        boolean isFormatCorrect, boolean isNameCorrect, ErrorProneComment comment, String name) {
      return new FixInfo(isFormatCorrect, isNameCorrect, comment, name);
    }
  }

  private void checkArgument(
      VarSymbol formal, ExpressionTree actual, ErrorProneToken token, VisitorState state) {
    List<FixInfo> matches = new ArrayList<>();
    for (ErrorProneComment comment : token.comments()) {
      if (comment.getStyle().equals(ErrorProneCommentStyle.LINE)) {
        // These are usually not intended as a parameter comment, and we don't want to flag if they
        // happen to match the parameter comment format.
        continue;
      }
      Matcher m =
          NamedParameterComment.PARAMETER_COMMENT_PATTERN.matcher(
              Comments.getTextFromComment(comment));
      if (!m.matches()) {
        continue;
      }

      boolean isFormatCorrect = isVarargs(formal) ^ Strings.isNullOrEmpty(m.group(2));
      String name = m.group(1);
      boolean isNameCorrect = formal.getSimpleName().contentEquals(name);

      // If there are multiple parameter name comments, bail if any one of them is an exact match.
      if (isNameCorrect && isFormatCorrect) {
        matches.clear();
        break;
      }

      matches.add(FixInfo.create(isFormatCorrect, isNameCorrect, comment, name));
    }

    String fixTemplate = isVarargs(formal) ? "/* %s...= */" : "/* %s= */";
    for (FixInfo match : matches) {
      SuggestedFix rewriteCommentFix =
          rewriteComment(match.comment(), String.format(fixTemplate, formal.getSimpleName()));
      SuggestedFix rewriteToRegularCommentFix =
          rewriteComment(match.comment(), String.format("/* %s */", match.name()));

      Description description;
      if (match.isFormatCorrect() && !match.isNameCorrect()) {
        description =
            buildDescription(actual)
                .setMessage(
                    String.format(
                        "`%s` does not match formal parameter name `%s`; either fix the name or"
                            + " use a regular comment",
                        match.comment().getText(), formal.getSimpleName()))
                .addFix(rewriteCommentFix)
                .addFix(rewriteToRegularCommentFix)
                .build();
      } else if (!match.isFormatCorrect() && match.isNameCorrect()) {
        description =
            buildDescription(actual)
                .setMessage(
                    String.format(
                        "parameter name comment `%s` uses incorrect format",
                        match.comment().getText()))
                .addFix(rewriteCommentFix)
                .build();
      } else if (!match.isFormatCorrect() && !match.isNameCorrect()) {
        description =
            buildDescription(actual)
                .setMessage(
                    String.format(
                        "`%s` does not match formal parameter name `%s` and uses incorrect "
                            + "format; either fix the format or use a regular comment",
                        match.comment().getText(), formal.getSimpleName()))
                .addFix(rewriteCommentFix)
                .addFix(rewriteToRegularCommentFix)
                .build();
      } else {
        throw new AssertionError(
            "Unexpected match with both isNameCorrect and isFormatCorrect true: " + match);
      }
      state.reportMatch(description);
    }
  }

  private static SuggestedFix rewriteComment(ErrorProneComment comment, String format) {
    int replacementStartPos = comment.getSourcePos(0);
    int replacementEndPos = comment.getSourcePos(comment.getText().length() - 1) + 1;
    return SuggestedFix.replace(replacementStartPos, replacementEndPos, format);
  }

  // complains on parameter name comments on varargs past the first one
  private void checkComment(ExpressionTree arg, ErrorProneToken token, VisitorState state) {
    for (ErrorProneComment comment : token.comments()) {
      Matcher m =
          NamedParameterComment.PARAMETER_COMMENT_PATTERN.matcher(
              Comments.getTextFromComment(comment));
      if (m.matches()) {
        SuggestedFix rewriteCommentFix =
            rewriteComment(
                comment, String.format("/* %s%s */", m.group(1), firstNonNull(m.group(2), "")));
        state.reportMatch(
            buildDescription(arg)
                .addFix(rewriteCommentFix)
                .setMessage("parameter name comment only allowed on first varargs argument")
                .build());
      }
    }
  }

  private static boolean isVarargs(VarSymbol sym) {
    Preconditions.checkArgument(
        sym.owner instanceof MethodSymbol, "sym must be a parameter to a method");
    MethodSymbol method = (MethodSymbol) sym.owner;
    return method.isVarArgs() && (method.getParameters().last() == sym);
  }
}
