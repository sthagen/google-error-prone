/*
 * Copyright 2024 The Error Prone Authors.
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
import static com.google.errorprone.util.SourceVersion.supportsTextBlocks;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.LiteralTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ErrorProneTokens;
import com.sun.source.tree.LiteralTree;
import com.sun.tools.javac.parser.Tokens.TokenKind;

/** See the summary. */
@BugPattern(
    summary =
        "Using \\s anywhere except at the end of a line in a text block is potentially misleading.",
    severity = ERROR)
public final class MisleadingEscapedSpace extends BugChecker implements LiteralTreeMatcher {
  @Override
  public Description matchLiteral(LiteralTree tree, VisitorState state) {
    if (!supportsTextBlocks(state.context)) {
      return NO_MATCH;
    }
    if (tree.getValue() instanceof Character) {
      if (tree.getValue().equals(' ') && state.getSourceForNode(tree).equals("'\\s'")) {
        return describeMatch(tree);
      }
    }
    if (tree.getValue() instanceof String value) {
      // Fast path out and avoid scanning through source code if there are simply no spaces in the
      // literal.
      if (!value.contains(" ")) {
        return NO_MATCH;
      }
      String source = state.getSourceForNode(tree);
      // Tokenize the source to make sure we omit comments. Desugaring of "a" + "b" into a single
      // string literal happens really early in compilation, and we want to ensure we don't match
      // on any "\s" in comments.
      var tokens = ErrorProneTokens.getTokens(source, state.context);
      for (var token : tokens) {
        if (!token.kind().equals(TokenKind.STRINGLITERAL)) {
          continue;
        }
        var sourceWithQuotes = source.substring(token.pos(), token.endPos());
        boolean isBlockLiteral = sourceWithQuotes.startsWith("\"\"\"");
        int quoteSize = isBlockLiteral ? 3 : 1;
        var literal = sourceWithQuotes.substring(quoteSize, sourceWithQuotes.length() - quoteSize);
        boolean seenEscape = false;
        for (int i = 0; i < literal.length(); ++i) {
          switch (literal.charAt(i)) {
            case '\n':
              seenEscape = false;
              break;
            case '\\':
              i++;
              if (literal.charAt(i) == 's') {
                seenEscape = true;
                break;
              }
            // fall through
            default:
              if (seenEscape) {
                return describeMatch(tree);
              }
              break;
          }
        }
        // Catch _trailing_ \s at the end of non-block literals.
        if (seenEscape && !isBlockLiteral) {
          return describeMatch(tree);
        }
      }
    }
    return NO_MATCH;
  }
}
