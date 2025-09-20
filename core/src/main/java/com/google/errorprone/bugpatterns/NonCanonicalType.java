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

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.fixes.SuggestedFixes.qualifyType;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasExplicitSource;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.Visibility;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Flags types being referred to by their non-canonical name. */
@BugPattern(
    summary = "This type is referred to by a non-canonical name, which may be misleading.",
    severity = WARNING)
public final class NonCanonicalType extends BugChecker implements MemberSelectTreeMatcher {
  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    // Only match on the outermost member select.
    if (state.getPath().getParentPath().getLeaf() instanceof MemberSelectTree) {
      return NO_MATCH;
    }
    String canonicalName = canonicalName(tree);
    if (canonicalName == null) {
      return NO_MATCH;
    }
    // Skip generated code. There are some noisy cases in AutoValue.
    if (canonicalName.contains("$")) {
      return NO_MATCH;
    }
    String nonCanonicalName = getNonCanonicalName(tree);
    if (canonicalName.equals(nonCanonicalName)) {
      return NO_MATCH;
    }
    for (Symbol symbol = getSymbol(tree); symbol != null; symbol = enclosingClass(symbol)) {
      if (!Visibility.fromModifiers(symbol.getModifiers()).shouldBeVisible(tree, state)) {
        return NO_MATCH;
      }
    }
    if (!hasExplicitSource(tree, state)) {
      // Can't suggest changing a synthetic type tree
      return NO_MATCH;
    }
    SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
    SuggestedFix fix =
        fixBuilder.replace(tree, qualifyType(state, fixBuilder, canonicalName)).build();
    return buildDescription(tree)
        .setMessage(createDescription(canonicalName, nonCanonicalName))
        .addFix(fix)
        .build();
  }

  private static @Nullable String canonicalName(MemberSelectTree tree) {
    Symbol sym = getSymbol(tree);
    if (sym == null) {
      return null;
    }
    if (!(sym instanceof Symbol.TypeSymbol)) {
      return null;
    }
    Symbol owner = sym.owner;
    if (owner == null) {
      // module symbols don't have owners
      return null;
    }
    return owner.getQualifiedName() + "." + sym.getSimpleName();
  }

  private static final Pattern PACKAGE_CLASS_NAME_SPLITTER = Pattern.compile("(.*?)\\.([A-Z].*)");

  private static String createDescription(String canonicalName, String nonCanonicalName) {
    Matcher canonicalNameMatcher = PACKAGE_CLASS_NAME_SPLITTER.matcher(canonicalName);
    Matcher nonCanonicalNameMatcher = PACKAGE_CLASS_NAME_SPLITTER.matcher(nonCanonicalName);

    if (canonicalNameMatcher.matches() && nonCanonicalNameMatcher.matches()) {
      if (!canonicalNameMatcher.group(2).equals(nonCanonicalNameMatcher.group(2))) {
        return String.format(
            "The type `%s` was referred to by the non-canonical name `%s`. This may be"
                + " misleading.",
            canonicalNameMatcher.group(2), nonCanonicalNameMatcher.group(2));
      }
    }
    return String.format(
        "The type `%s` was referred to by the non-canonical name `%s`. This may be"
            + " misleading.",
        canonicalName, nonCanonicalName);
  }

  /**
   * Find the non-canonical name which is being used to refer to this type. We can't just use {@code
   * getSymbol}, given that points to the same symbol as the canonical name.
   */
  private static String getNonCanonicalName(Tree tree) {
    return switch (tree) {
      case IdentifierTree identifierTree -> getSymbol(tree).getQualifiedName().toString();
      case MemberSelectTree memberSelectTree -> {
        Symbol expressionSymbol = getSymbol(memberSelectTree.getExpression());
        if (!(expressionSymbol instanceof TypeSymbol)) {
          yield getSymbol(tree).getQualifiedName().toString();
        }
        yield getNonCanonicalName(memberSelectTree.getExpression())
            + "."
            + memberSelectTree.getIdentifier();
      }
      case ParameterizedTypeTree parameterizedTypeTree ->
          getNonCanonicalName(parameterizedTypeTree.getType());
      case AnnotatedTypeTree annotatedTypeTree ->
          getNonCanonicalName(annotatedTypeTree.getUnderlyingType());
      default -> throw new AssertionError(tree.getKind());
    };
  }
}
