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

import static com.google.common.base.Ascii.toUpperCase;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Streams.stream;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.VisitorState.memoize;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.predicates.TypePredicates.isDescendantOf;
import static com.google.errorprone.util.ASTHelpers.enumValues;
import static com.google.errorprone.util.ASTHelpers.getEnclosedElements;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.getUpperBound;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.ASTHelpers.isAbstract;
import static com.google.errorprone.util.ASTHelpers.isSameType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.predicates.TypePredicate;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.jspecify.annotations.Nullable;

/** A BugPattern; see the summary. */
@BugPattern(
    summary = "A field was set twice in the same chained expression.",
    severity = ERROR,
    altNames = "ProtoRedundantSet",
    tags = StandardTags.FRAGILE_CODE)
public final class RedundantSetterCall extends BugChecker implements MethodInvocationTreeMatcher {

  /** Matches a fluent setter method. */
  private static final Matcher<ExpressionTree> FLUENT_SETTER =
      anyOf(
          instanceMethod()
              .onDescendantOfAny(
                  "com.google.protobuf.GeneratedMessage.Builder",
                  "com.google.protobuf.GeneratedMessageLite.Builder")
              .withNameMatching(Pattern.compile("^(set|add|clear|put).+")),
          (tree, state) -> {
            if (!(tree instanceof MethodInvocationTree methodInvocationTree)) {
              return false;
            }
            var symbol = getSymbol(methodInvocationTree);
            return isAbstract(symbol)
                && isWithinAutoValueBuilder(symbol, state)
                && isSameType(symbol.owner.type, symbol.getReturnType(), state);
          });

  private static final TypePredicate ONE_OF_ENUM =
      isDescendantOf("com.google.protobuf.AbstractMessageLite.InternalOneOfEnum");

  /**
   * Matches a terminal setter. That is, a fluent builder method which is either not followed by
   * another method invocation, or by a method invocation which is not a {@link #FLUENT_SETTER}.
   */
  private static final Matcher<ExpressionTree> TERMINAL_FLUENT_SETTER =
      allOf(
          FLUENT_SETTER,
          (tree, state) ->
              !(state.getPath().getParentPath().getLeaf() instanceof MemberSelectTree
                  && FLUENT_SETTER.matches(
                      (ExpressionTree) state.getPath().getParentPath().getParentPath().getLeaf(),
                      state)));

  private final boolean improvements;
  private final boolean matchLiteProtos;

  @Inject
  RedundantSetterCall(ErrorProneFlags flags) {
    this.improvements = flags.getBoolean("RedundantSetterCall:Improvements").orElse(true);
    this.matchLiteProtos = flags.getBoolean("OneOfChecks:MatchLiteProtos").orElse(true);
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (!TERMINAL_FLUENT_SETTER.matches(tree, state)) {
      return Description.NO_MATCH;
    }

    var owner = getUpperBound(getType(tree), state.getTypes()).tsym.owner;
    boolean isProto = owner != null && isSubtype(owner.type, MESSAGE_LITE.get(state), state);

    ListMultimap<Field, FieldWithValue> setters = ArrayListMultimap.create();
    ImmutableMap<String, OneOfField> oneOfSetters =
        isProto ? scanForOneOfSetters(owner, state) : ImmutableMap.of();
    ImmutableSet<String> fieldNames = isProto ? getFields(owner) : ImmutableSet.of();

    Type type = ASTHelpers.getReturnType(tree);
    for (ExpressionTree current = tree;
        FLUENT_SETTER.matches(current, state);
        current = ASTHelpers.getReceiver(current)) {
      MethodInvocationTree method = (MethodInvocationTree) current;
      if (!ASTHelpers.isSameType(type, ASTHelpers.getReturnType(current), state)) {
        break;
      }
      Symbol symbol = getSymbol(current);
      if (!(symbol instanceof MethodSymbol)) {
        break;
      }
      String methodName = symbol.getSimpleName().toString();
      // Break on methods like "addFooBuilder", otherwise we might be building a nested proto of the
      // same type.
      if (methodName.endsWith("Builder")) {
        break;
      }
      if (improvements && isProto && methodName.startsWith("set")) {
        String withoutSet = methodName.replaceFirst("^set", "");
        if (!fieldNames.contains(toUpperCase(withoutSet))) {
          if (methodName.endsWith("Value")) {
            methodName = methodName.replaceFirst("Value$", "");
          }
          if (methodName.endsWith("Bytes")) {
            methodName = methodName.replaceFirst("Bytes$", "");
          }
        }
      }
      for (FieldType fieldType : FieldType.values()) {
        FieldWithValue match = fieldType.match(methodName, method, state);
        if (match != null) {
          setters.put(match.getField(), match);
          if (oneOfSetters.containsKey(methodName)) {
            setters.put(oneOfSetters.get(methodName), match);
          }
        }
      }
    }

    setters.asMap().entrySet().removeIf(entry -> entry.getValue().size() <= 1);

    if (setters.isEmpty()) {
      return Description.NO_MATCH;
    }

    for (Map.Entry<Field, Collection<FieldWithValue>> entry : setters.asMap().entrySet()) {
      Field field = entry.getKey();
      Collection<FieldWithValue> values = entry.getValue();
      state.reportMatch(describe(field, values, state));
    }
    return Description.NO_MATCH;
  }

  private ImmutableMap<String, OneOfField> scanForOneOfSetters(Symbol proto, VisitorState state) {
    if (!matchLiteProtos && isSubtype(proto.type, GENERATED_MESSAGE_LITE.get(state), state)) {
      return ImmutableMap.of();
    }
    var builder = ImmutableMap.<String, OneOfField>builder();
    for (Symbol element : getEnclosedElements(proto)) {
      if (!ONE_OF_ENUM.apply(element.type, state)) {
        continue;
      }
      var oneOfField = OneOfField.of(element.getSimpleName().toString().replaceFirst("Case$", ""));
      for (String enumName : enumValues(element.type.tsym)) {
        if (enumName.equals("ONEOF_NOT_SET")) {
          continue;
        }
        builder.put("set" + UPPER_UNDERSCORE.to(UPPER_CAMEL, enumName), oneOfField);
      }
    }
    return builder.buildOrThrow();
  }

  /**
   * Returns all the field names in the proto in uppercase with all components concatenated.
   *
   * <p>This is an odd format, but it works to compare the existence of a field based on a getter,
   * given both {@code foo_bar} and {@code fooBar} as field names generate a getter named {@code
   * getFooBar} (so we normalise to {@code FOOBAR}).
   */
  private static ImmutableSet<String> getFields(Symbol proto) {
    return getEnclosedElements(proto).stream()
        .map(element -> element.getSimpleName().toString())
        .filter(name -> name.endsWith("_FIELD_NUMBER"))
        .map(name -> toUpperCase(name.replaceFirst("_FIELD_NUMBER$", "").replace("_", "")))
        .collect(toImmutableSet());
  }

  private static final Supplier<Type> GENERATED_MESSAGE_LITE =
      memoize(state -> state.getTypeFromString("com.google.protobuf.GeneratedMessageLite"));

  private static final Supplier<Type> MESSAGE_LITE =
      memoize(state -> state.getTypeFromString("com.google.protobuf.MessageLite"));

  private Description describe(
      Field field, Collection<FieldWithValue> locations, VisitorState state) {
    // We flag up all duplicate sets, but only suggest a fix if the setter is given the same
    // argument (based on source code). This is to avoid the temptation to apply the fix in
    // cases like,
    //   MyProto.newBuilder().setFoo(copy.getFoo()).setFoo(copy.getBar())
    // where the correct fix is probably to replace the second 'setFoo' with 'setBar'.
    SuggestedFix.Builder fix = SuggestedFix.builder();
    long values =
        locations.stream().map(l -> state.getSourceForNode(l.getArgument())).distinct().count();
    if (field.identicalValuesShouldBeRemoved() && values == 1) {
      for (FieldWithValue fieldWithValue : Iterables.skip(locations, 1)) {
        MethodInvocationTree method = fieldWithValue.getMethodInvocation();
        int startPos = state.getEndPosition(ASTHelpers.getReceiver(method));
        int endPos = state.getEndPosition(method);
        fix.replace(startPos, endPos, "");
      }
    }
    return buildDescription(locations.iterator().next().getArgument())
        .setMessage(
            String.format(
                "%s was called %s%s. Setting the same field multiple times is redundant, and "
                    + "could mask a bug.",
                field.toString(locations),
                nTimes(locations.size()),
                field.identicalValuesShouldBeRemoved()
                    ? (values == 1 ? " with the same argument" : " with different arguments")
                    : ""))
        .addFix(fix.build())
        .build();
  }

  private static String nTimes(int n) {
    return n == 2 ? "twice" : String.format("%d times", n);
  }

  enum FieldType {
    SINGLE {
      @Override
      @Nullable FieldWithValue match(String name, MethodInvocationTree tree, VisitorState state) {
        if ((name.startsWith("set") || isWithinAutoValueBuilder(getSymbol(tree), state))
            && tree.getArguments().size() == 1) {
          return FieldWithValue.of(SingleField.of(name), tree, tree.getArguments().get(0));
        }
        return null;
      }
    },
    REPEATED {
      @Override
      @Nullable FieldWithValue match(String name, MethodInvocationTree tree, VisitorState state) {
        if (name.startsWith("set") && tree.getArguments().size() == 2) {
          Integer index = ASTHelpers.constValue(tree.getArguments().get(0), Integer.class);
          if (index != null) {
            return FieldWithValue.of(
                RepeatedField.of(name, index), tree, tree.getArguments().get(1));
          }
        }
        return null;
      }
    },
    MAP {
      @Override
      @Nullable FieldWithValue match(String name, MethodInvocationTree tree, VisitorState state) {
        if (name.startsWith("put") && tree.getArguments().size() == 2) {
          Object key = ASTHelpers.constValue(tree.getArguments().get(0), Object.class);
          if (key != null) {
            return FieldWithValue.of(MapField.of(name, key), tree, tree.getArguments().get(1));
          }
        }
        return null;
      }
    };

    abstract FieldWithValue match(String name, MethodInvocationTree tree, VisitorState state);
  }

  private static boolean isWithinAutoValueBuilder(MethodSymbol symbol, VisitorState state) {
    return hasAnnotation(symbol.owner, "com.google.auto.value.AutoValue.Builder", state);
  }

  interface Field {
    @Override
    boolean equals(@Nullable Object other);

    @Override
    int hashCode();

    boolean identicalValuesShouldBeRemoved();

    String toString(Iterable<FieldWithValue> locations);
  }

  @AutoValue
  abstract static class SingleField implements Field {
    abstract String getName();

    static SingleField of(String name) {
      return new AutoValue_RedundantSetterCall_SingleField(name);
    }

    @Override
    public final String toString(Iterable<FieldWithValue> locations) {
      return String.format("%s(..)", getName());
    }

    @Override
    public boolean identicalValuesShouldBeRemoved() {
      return true;
    }
  }

  @AutoValue
  abstract static class RepeatedField implements Field {
    abstract String getName();

    abstract int getIndex();

    static RepeatedField of(String name, int index) {
      return new AutoValue_RedundantSetterCall_RepeatedField(name, index);
    }

    @Override
    public final String toString(Iterable<FieldWithValue> locations) {
      return String.format("%s(%s, ..)", getName(), getIndex());
    }

    @Override
    public boolean identicalValuesShouldBeRemoved() {
      return true;
    }
  }

  @AutoValue
  abstract static class MapField implements Field {
    abstract String getName();

    abstract Object getKey();

    static MapField of(String name, Object key) {
      return new AutoValue_RedundantSetterCall_MapField(name, key);
    }

    @Override
    public final String toString(Iterable<FieldWithValue> locations) {
      return String.format("%s(%s, ..)", getName(), getKey());
    }

    @Override
    public boolean identicalValuesShouldBeRemoved() {
      return true;
    }
  }

  @AutoValue
  abstract static class OneOfField implements Field {
    abstract String oneOfName();

    static OneOfField of(String oneOfName) {
      return new AutoValue_RedundantSetterCall_OneOfField(oneOfName);
    }

    @Override
    public final String toString(Iterable<FieldWithValue> locations) {
      return String.format(
          "The oneof `%s` (set via %s)",
          oneOfName(),
          stream(locations)
              .map(l -> getSymbol(l.getMethodInvocation()).getSimpleName().toString())
              .distinct()
              .sorted()
              .collect(joining(", ")));
    }

    @Override
    public boolean identicalValuesShouldBeRemoved() {
      return false;
    }
  }

  @AutoValue
  abstract static class FieldWithValue {
    abstract Field getField();

    abstract MethodInvocationTree getMethodInvocation();

    abstract ExpressionTree getArgument();

    static FieldWithValue of(
        Field field, MethodInvocationTree methodInvocationTree, ExpressionTree argumentTree) {
      return new AutoValue_RedundantSetterCall_FieldWithValue(
          field, methodInvocationTree, argumentTree);
    }
  }
}
