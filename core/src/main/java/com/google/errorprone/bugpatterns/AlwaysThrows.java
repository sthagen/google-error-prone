/*
 * Copyright 2021 The Error Prone Authors.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.constValue;
import static com.google.errorprone.util.ASTHelpers.getReceiver;
import static java.util.Arrays.stream;

import com.google.common.base.Throwables;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.threadsafety.ConstantExpressions;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.google.protobuf.ByteString;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;
import org.jspecify.annotations.Nullable;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(summary = "Detects calls that will fail at runtime", severity = ERROR)
public class AlwaysThrows extends BugChecker implements MethodInvocationTreeMatcher {

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private static final ImmutableMap<String, Consumer<CharSequence>> VALIDATORS =
      ImmutableMap.<String, Consumer<CharSequence>>builder()
          .put("java.time.Duration", java.time.Duration::parse)
          .put("java.time.Instant", java.time.Instant::parse)
          .put("java.time.LocalDate", java.time.LocalDate::parse)
          .put("java.time.LocalDateTime", java.time.LocalDateTime::parse)
          .put("java.time.LocalTime", java.time.LocalTime::parse)
          .put("java.time.MonthDay", java.time.MonthDay::parse)
          .put("java.time.OffsetDateTime", java.time.OffsetDateTime::parse)
          .put("java.time.OffsetTime", java.time.OffsetTime::parse)
          .put("java.time.Period", java.time.Period::parse)
          .put("java.time.Year", java.time.Year::parse)
          .put("java.time.YearMonth", java.time.YearMonth::parse)
          .put("java.time.ZonedDateTime", java.time.ZonedDateTime::parse)
          .buildOrThrow();

  private static final Matcher<ExpressionTree> IMMUTABLE_MAP_OF =
      staticMethod().onDescendantOf("com.google.common.collect.ImmutableMap").named("of");

  private static final Matcher<ExpressionTree> IMMUTABLE_BI_MAP_OF =
      staticMethod().onDescendantOf("com.google.common.collect.ImmutableBiMap").named("of");

  private static final Matcher<ExpressionTree> IMMUTABLE_MAP_PUT =
      instanceMethod()
          .onDescendantOf("com.google.common.collect.ImmutableMap.Builder")
          .namedAnyOf("put")
          .withParameters("java.lang.Object", "java.lang.Object");

  private static final Matcher<ExpressionTree> IMMUTABLE_BI_MAP_PUT =
      instanceMethod()
          .onDescendantOf("com.google.common.collect.ImmutableBiMap.Builder")
          .namedAnyOf("put")
          .withParameters("java.lang.Object", "java.lang.Object");

  enum Api {
    PARSE_TIME(
        staticMethod()
            .onClassAny(VALIDATORS.keySet())
            .named("parse")
            .withParameters("java.lang.CharSequence")) {
      @Override
      void validate(MethodInvocationTree tree, String argument) {
        MethodSymbol sym = ASTHelpers.getSymbol(tree);
        VALIDATORS.get(sym.owner.getQualifiedName().toString()).accept(argument);
      }
    },
    BYTE_STRING(
        staticMethod()
            .onClass("com.google.protobuf.ByteString")
            .named("fromHex")
            .withParameters("java.lang.String")) {
      @Override
      void validate(MethodInvocationTree tree, String argument) {
        // ByteString.fromHex was only added in 2021; use reflection to be tolerant of old proto
        // versions.
        try {
          ByteString.class.getMethod("fromHex", String.class).invoke(null, argument);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          return;
        } catch (InvocationTargetException e) {
          throw Throwables.getCauseAs(e.getCause(), NumberFormatException.class);
        }
      }
    },
    UUID_PARSE(staticMethod().onClass("java.util.UUID").named("fromString")) {
      @Override
      void validate(MethodInvocationTree tree, String argument) {
        var unused = UUID.fromString(argument);
      }
    };

    Api(Matcher<ExpressionTree> matcher) {
      this.matcher = matcher;
    }

    @SuppressWarnings("ImmutableEnumChecker") // is immutable
    private final Matcher<ExpressionTree> matcher;

    abstract void validate(MethodInvocationTree tree, String argument) throws Exception;
  }

  private final ConstantExpressions constantExpressions;

  @Inject
  AlwaysThrows(ConstantExpressions constantExpressions) {
    this.constantExpressions = constantExpressions;
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (IMMUTABLE_MAP_PUT.matches(tree, state)) {
      if (state.getPath().getParentPath() != null
          && state.getPath().getParentPath().getParentPath() != null) {
        Tree grandParent = state.getPath().getParentPath().getParentPath().getLeaf();
        if (grandParent instanceof ExpressionTree expressionTree
            && IMMUTABLE_MAP_PUT.matches(expressionTree, state)) {
          return NO_MATCH;
        }
      }
      Description description = checkImmutableMapBuilder(tree, /* index= */ 0, state);
      if (!description.equals(NO_MATCH)) {
        return description;
      }
      if (IMMUTABLE_BI_MAP_PUT.matches(tree, state)) {
        return checkImmutableMapBuilder(tree, /* index= */ 1, state);
      }
    }
    if (IMMUTABLE_MAP_OF.matches(tree, state)) {
      Description description = checkImmutableMapOf(tree, /* index= */ 0, state);
      if (!description.equals(NO_MATCH)) {
        return description;
      }
      if (IMMUTABLE_BI_MAP_OF.matches(tree, state)) {
        return checkImmutableMapOf(tree, /* index= */ 1, state);
      }
    }
    Api api =
        stream(Api.values()).filter(m -> m.matcher.matches(tree, state)).findAny().orElse(null);
    if (api == null) {
      return NO_MATCH;
    }
    String argument = constValue(Iterables.getOnlyElement(tree.getArguments()), String.class);
    if (argument == null) {
      return NO_MATCH;
    }
    try {
      api.validate(tree, argument);
    } catch (Exception t) {
      return buildDescription(tree)
          .setMessage(
              String.format(
                  "This call will fail at runtime with a %s: %s",
                  t.getClass().getSimpleName(), t.getMessage()))
          .build();
    }
    return NO_MATCH;
  }

  private Description checkImmutableMapBuilder(
      MethodInvocationTree tree, int index, VisitorState state) {
    Multiset<Object> keys = HashMultiset.create();
    ExpressionTree receiver = tree;
    for (;
        receiver instanceof MethodInvocationTree && IMMUTABLE_MAP_PUT.matches(receiver, state);
        receiver = getReceiver(receiver)) {
      Object constantKey =
          getConstantKey(((MethodInvocationTree) receiver).getArguments().get(index), state);
      if (constantKey == null) {
        continue;
      }
      keys.add(constantKey);
    }
    return checkForRepeatedKeys(tree, keys);
  }

  private Description checkImmutableMapOf(
      MethodInvocationTree tree, int index, VisitorState state) {
    Multiset<Object> keys = HashMultiset.create();
    for (int i = 0; i < tree.getArguments().size(); i += 2) {
      Object constantKey = getConstantKey(tree.getArguments().get(i + index), state);
      if (constantKey == null) {
        continue;
      }
      keys.add(constantKey);
    }
    return checkForRepeatedKeys(tree, keys);
  }

  private @Nullable Object getConstantKey(ExpressionTree key, VisitorState state) {
    return constantExpressions.constantExpression(key, state).orElse(null);
  }

  private Description checkForRepeatedKeys(MethodInvocationTree tree, Multiset<Object> keys) {
    ImmutableSet<Object> repeatedKeys =
        keys.entrySet().stream().filter(e -> e.getCount() > 1).collect(toImmutableSet());
    if (repeatedKeys.isEmpty()) {
      return NO_MATCH;
    }
    return buildDescription(tree)
        .setMessage(
            "This ImmutableMap construction will throw (or have known duplicates overwritten) due"
                + " to duplicates: "
                + repeatedKeys.stream()
                    .map(
                        k ->
                            k instanceof VarSymbol varSymbol
                                ? varSymbol.getSimpleName()
                                : k.toString())
                    .collect(toImmutableSet()))
        .build();
  }
}
