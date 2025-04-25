/*
 * Copyright 2013 The Error Prone Authors.
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

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.receiverOfInvocation;
import static com.google.errorprone.matchers.Matchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.getReceiver;
import static com.google.errorprone.util.ASTHelpers.getStartPosition;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isConsideredFinal;
import static com.google.errorprone.util.ASTHelpers.isSameType;
import static com.google.errorprone.util.ASTHelpers.stripParentheses;
import static java.lang.String.format;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.jspecify.annotations.Nullable;

/** Matches comparison of proto fields to {@code null}. */
@BugPattern(
    summary = "This value cannot be null, and comparing it to null may be misleading.",
    name = "ImpossibleNullComparison",
    altNames = "ProtoFieldNullComparison",
    severity = ERROR)
public final class ImpossibleNullComparison extends BugChecker
    implements CompilationUnitTreeMatcher {

  // TODO(b/111109484): Try to consolidate these with NullnessPropagationTransfer.
  private static final Matcher<ExpressionTree> CHECK_NOT_NULL =
      anyOf(
          staticMethod().onClass("com.google.common.base.Preconditions").named("checkNotNull"),
          staticMethod().onClass("com.google.common.base.Verify").named("verifyNotNull"),
          staticMethod().onClass("java.util.Objects").named("requireNonNull"));

  private static final Matcher<ExpressionTree> ASSERT_NOT_NULL =
      anyOf(
          staticMethod().onClass("junit.framework.Assert").named("assertNotNull"),
          staticMethod().onClass("org.junit.Assert").named("assertNotNull"));

  private static final Matcher<MethodInvocationTree> TRUTH_NOT_NULL =
      allOf(
          instanceMethod().anyClass().named("isNotNull"),
          receiverOfInvocation(
              anyOf(
                  staticMethod().anyClass().namedAnyOf("assertThat"),
                  instanceMethod()
                      .onDescendantOf("com.google.common.truth.StandardSubjectBuilder")
                      .named("that"))));

  private static final Matcher<Tree> RETURNS_LIST = Matchers.isSubtypeOf("java.util.List");

  private static final ImmutableSet<Kind> COMPARISON_OPERATORS =
      Sets.immutableEnumSet(Kind.EQUAL_TO, Kind.NOT_EQUAL_TO);

  private static final Matcher<ExpressionTree> EXTENSION_METHODS_WITH_FIX =
      instanceMethod()
          .onDescendantOf("com.google.protobuf.GeneratedMessage.ExtendableMessage")
          .named("getExtension")
          .withParameters("com.google.protobuf.ExtensionLite");

  private static final Matcher<ExpressionTree> EXTENSION_METHODS_WITH_NO_FIX =
      anyOf(
          instanceMethod()
              .onDescendantOf("com.google.protobuf.MessageOrBuilder")
              .named("getRepeatedField")
              .withParameters("com.google.protobuf.Descriptors.FieldDescriptor", "int"),
          instanceMethod()
              .onDescendantOf("com.google.protobuf.GeneratedMessage.ExtendableMessage")
              .named("getExtension")
              .withParameters("com.google.protobuf.ExtensionLite", "int"),
          instanceMethod()
              .onDescendantOf("com.google.protobuf.MessageOrBuilder")
              .named("getField")
              .withParameters("com.google.protobuf.Descriptors.FieldDescriptor"));

  private static final Matcher<ExpressionTree> OF_NULLABLE =
      anyOf(
          staticMethod().onClass("java.util.Optional").named("ofNullable"),
          staticMethod().onClass("com.google.common.base.Optional").named("fromNullable"));

  private static boolean isNull(ExpressionTree tree) {
    return tree.getKind() == Kind.NULL_LITERAL;
  }

  /** Matcher for generated protobufs. */
  private static final Matcher<ExpressionTree> PROTO_RECEIVER =
      instanceMethod()
          .onDescendantOfAny(
              "com.google.protobuf.GeneratedMessageLite", "com.google.protobuf.GeneratedMessage");

  private final boolean matchTestAssertions;
  private final boolean checkPrimitives;
  private final boolean checkValueOf;

  @Inject
  ImpossibleNullComparison(ErrorProneFlags flags) {
    this.matchTestAssertions =
        flags.getBoolean("ProtoFieldNullComparison:MatchTestAssertions").orElse(true);
    this.checkPrimitives = flags.getBoolean("ImmutableNullComparison:CheckPrimitives").orElse(true);
    this.checkValueOf = flags.getBoolean("ImpossibleNullComparison:CheckValueOf").orElse(true);
  }

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    NullComparisonScanner scanner = new NullComparisonScanner(state);
    scanner.scan(state.getPath(), null);
    return Description.NO_MATCH;
  }

  private class NullComparisonScanner extends TreePathScanner<Void, Void> {
    private final Map<Symbol, ExpressionTree> effectivelyFinalValues = new HashMap<>();
    private final VisitorState state;

    private NullComparisonScanner(VisitorState state) {
      this.state = state;
    }

    @Override
    public Void visitMethod(MethodTree method, Void unused) {
      return isSuppressed(method, state) ? null : super.visitMethod(method, null);
    }

    @Override
    public Void visitClass(ClassTree clazz, Void unused) {
      return isSuppressed(clazz, state) ? null : super.visitClass(clazz, null);
    }

    @Override
    public Void visitVariable(VariableTree variable, Void unused) {
      Symbol symbol = ASTHelpers.getSymbol(variable);
      if (variable.getInitializer() != null && isConsideredFinal(symbol)) {
        getInitializer(variable.getInitializer())
            .ifPresent(e -> effectivelyFinalValues.put(symbol, e));
      }
      return isSuppressed(variable, state) ? null : super.visitVariable(variable, null);
    }

    private Optional<ExpressionTree> getInitializer(ExpressionTree tree) {
      return Optional.ofNullable(
          new SimpleTreeVisitor<ExpressionTree, Void>() {
            @Override
            public @Nullable ExpressionTree visitMethodInvocation(
                MethodInvocationTree node, Void unused) {
              return PROTO_RECEIVER.matches(node, state) ? node : null;
            }

            @Override
            public ExpressionTree visitParenthesized(ParenthesizedTree node, Void unused) {
              return visit(node.getExpression(), null);
            }

            @Override
            public ExpressionTree visitTypeCast(TypeCastTree node, Void unused) {
              return visit(node.getExpression(), null);
            }
          }.visit(tree, null));
    }

    @Override
    public Void visitSwitch(SwitchTree tree, Void unused) {
      handleSwitch(tree.getExpression(), tree.getCases());
      return super.visitSwitch(tree, null);
    }

    @Override
    public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) {
      handleSwitch(tree.getExpression(), tree.getCases());
      return super.visitSwitchExpression(tree, null);
    }

    private void handleSwitch(ExpressionTree expression, List<? extends CaseTree> cases) {
      var withoutParens = stripParentheses(expression);
      VisitorState subState = state.withPath(getCurrentPath());
      for (var caseTree : cases) {
        caseTree.getExpressions().stream()
            .filter(e -> isNull(e))
            .findFirst()
            // We're not using the fixer, just using it to see if there's a problem.
            .filter(unused -> getFixer(withoutParens, subState).isPresent())
            .ifPresent(
                e ->
                    // NOTE: This fix is possibly too big: you can write `case null, default ->`.
                    state.reportMatch(describeMatch(caseTree, SuggestedFix.delete(caseTree))));
      }
    }

    @Override
    public Void visitBinary(BinaryTree binary, Void unused) {
      if (!COMPARISON_OPERATORS.contains(binary.getKind())) {
        return super.visitBinary(binary, null);
      }
      VisitorState subState = state.withPath(getCurrentPath());
      Optional<Fixer> fixer = Optional.empty();
      if (isNull(binary.getLeftOperand())) {
        fixer = getFixer(binary.getRightOperand(), subState);
      }
      if (isNull(binary.getRightOperand())) {
        fixer = getFixer(binary.getLeftOperand(), subState);
      }
      fixer
          .map(f -> describeMatch(binary, ProblemUsage.COMPARISON.fix(f, binary, subState)))
          .ifPresent(state::reportMatch);
      return super.visitBinary(binary, null);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
      VisitorState subState = state.withPath(getCurrentPath());
      ExpressionTree argument;
      ProblemUsage problemType;
      if (CHECK_NOT_NULL.matches(node, subState)) {
        argument = node.getArguments().get(0);
        problemType = ProblemUsage.CHECK_NOT_NULL;
      } else if (matchTestAssertions && ASSERT_NOT_NULL.matches(node, subState)) {
        argument = getLast(node.getArguments());
        problemType = ProblemUsage.JUNIT;
      } else if (OF_NULLABLE.matches(node, subState)) {
        argument = getOnlyElement(node.getArguments());
        problemType = ProblemUsage.OPTIONAL;
      } else if (matchTestAssertions && TRUTH_NOT_NULL.matches(node, subState)) {
        argument = getOnlyElement(((MethodInvocationTree) getReceiver(node)).getArguments());
        problemType = ProblemUsage.TRUTH;
      } else {
        return super.visitMethodInvocation(node, null);
      }
      getFixer(argument, subState)
          .map(f -> describeMatch(node, problemType.fix(f, node, subState)))
          .ifPresent(state::reportMatch);

      return super.visitMethodInvocation(node, null);
    }

    private Optional<Fixer> getFixer(ExpressionTree tree, VisitorState state) {
      ExpressionTree resolvedTree = getEffectiveTree(tree);
      if (resolvedTree == null) {
        return Optional.empty();
      }
      return stream(GetterTypes.values())
          .filter(gt -> !gt.equals(GetterTypes.PRIMITIVE) || checkPrimitives)
          .filter(gt -> !gt.equals(GetterTypes.VALUE_OF) || checkValueOf)
          .map(type -> type.match(resolvedTree, state))
          .filter(Objects::nonNull)
          .findFirst();
    }

    private @Nullable ExpressionTree getEffectiveTree(ExpressionTree tree) {
      return tree instanceof IdentifierTree
          ? effectivelyFinalValues.getOrDefault(ASTHelpers.getSymbol(tree), tree)
          : tree;
    }
  }

  private static String getMethodName(ExpressionTree tree) {
    MethodInvocationTree method = (MethodInvocationTree) tree;
    ExpressionTree expressionTree = method.getMethodSelect();
    JCFieldAccess access = (JCFieldAccess) expressionTree;
    return access.sym.getQualifiedName().toString();
  }

  private static String replaceLast(String text, String pattern, String replacement) {
    StringBuilder builder = new StringBuilder(text);
    int lastIndexOf = builder.lastIndexOf(pattern);
    return builder.replace(lastIndexOf, lastIndexOf + pattern.length(), replacement).toString();
  }

  /** Generates a replacement, if available. */
  private interface Fixer {
    /**
     * @param negated whether the replacement should be negated.
     */
    Optional<String> getReplacement(boolean negated, VisitorState state);
  }

  private static final Matcher<ExpressionTree> OPTIONAL_GET_MATCHER =
      instanceMethod().onExactClass("java.util.Optional").namedAnyOf("get", "orElseThrow");

  private static final Matcher<ExpressionTree> GUAVA_OPTIONAL_GET_MATCHER =
      instanceMethod().onExactClass("com.google.common.base.Optional").named("get");

  private static final Matcher<ExpressionTree> MULTIMAP_GET_MATCHER =
      instanceMethod().onDescendantOf("com.google.common.collect.Multimap").named("get");

  private static final Matcher<ExpressionTree> TABLE_ROW_MATCHER =
      instanceMethod().onDescendantOf("com.google.common.collect.Table").named("row");

  private static final Matcher<ExpressionTree> TABLE_COLUMN_MATCHER =
      instanceMethod().onDescendantOf("com.google.common.collect.Table").named("column");

  private static final Matcher<ExpressionTree> NON_NULL_VALUE_OF =
      staticMethod()
          .onDescendantOfAny("java.lang.Enum", "java.lang.Number")
          .named("valueOf")
          .withParameters("java.lang.String");

  private enum GetterTypes {
    OPTIONAL_GET {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!OPTIONAL_GET_MATCHER.matches(tree, state)) {
          return null;
        }
        return (n, s) ->
            Optional.of(
                s.getSourceForNode(getReceiver(tree)) + (n ? ".isEmpty()" : ".isPresent()"));
      }
    },
    GUAVA_OPTIONAL_GET {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!GUAVA_OPTIONAL_GET_MATCHER.matches(tree, state)) {
          return null;
        }
        return (n, s) ->
            Optional.of((n ? "!" : "") + s.getSourceForNode(getReceiver(tree)) + ".isPresent()");
      }
    },
    MULTIMAP_GET {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!MULTIMAP_GET_MATCHER.matches(tree, state)) {
          return null;
        }
        return (n, s) ->
            Optional.of(
                format(
                    "%s%s.containsKey(%s)",
                    n ? "!" : "",
                    s.getSourceForNode(getReceiver(tree)),
                    s.getSourceForNode(
                        getOnlyElement(((MethodInvocationTree) tree).getArguments()))));
      }
    },
    TABLE_ROW_GET {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!TABLE_ROW_MATCHER.matches(tree, state)) {
          return null;
        }
        return (n, s) ->
            Optional.of(
                format(
                    "%s%s.containsRow(%s)",
                    n ? "!" : "",
                    s.getSourceForNode(getReceiver(tree)),
                    s.getSourceForNode(
                        getOnlyElement(((MethodInvocationTree) tree).getArguments()))));
      }
    },
    TABLE_COLUMN_GET {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!TABLE_COLUMN_MATCHER.matches(tree, state)) {
          return null;
        }
        return (n, s) ->
            Optional.of(
                format(
                    "%s%s.containsColumn(%s)",
                    n ? "!" : "",
                    s.getSourceForNode(getReceiver(tree)),
                    s.getSourceForNode(
                        getOnlyElement(((MethodInvocationTree) tree).getArguments()))));
      }
    },
    PRIMITIVE {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        var type = getType(tree);
        return type != null && type.isPrimitive() ? GetterTypes::emptyFix : null;
      }
    },
    VALUE_OF {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!NON_NULL_VALUE_OF.matches(tree, state)) {
          return null;
        }
        // TODO(cpovirk): Suggest Enums.getIfPresent, Ints.tryParse, etc.
        return GetterTypes::emptyFix;
      }
    },
    /** {@code proto.getFoo()} */
    SCALAR {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!PROTO_RECEIVER.matches(tree, state)) {
          return null;
        }
        if (!(tree instanceof MethodInvocationTree method)) {
          return null;
        }
        if (!method.getArguments().isEmpty()) {
          return null;
        }
        if (RETURNS_LIST.matches(method, state)) {
          return null;
        }
        ExpressionTree expressionTree = method.getMethodSelect();
        return isGetter(expressionTree) ? (n, s) -> generateFix(method, n, s) : null;
      }

      private Optional<String> generateFix(
          MethodInvocationTree methodInvocation, boolean negated, VisitorState state) {
        String methodName = ASTHelpers.getSymbol(methodInvocation).getQualifiedName().toString();
        String hasMethod = methodName.replaceFirst("get", "has");

        // proto3 does not generate has methods for scalar types, e.g. ByteString and String.
        // Do not provide a replacement in these cases.
        Set<MethodSymbol> hasMethods =
            ASTHelpers.findMatchingMethods(
                state.getName(hasMethod),
                ms -> ms.params().isEmpty(),
                getType(getReceiver(methodInvocation)),
                state.getTypes());
        if (hasMethods.isEmpty()) {
          return Optional.empty();
        }
        String replacement =
            replaceLast(state.getSourceForNode(methodInvocation), methodName, hasMethod);
        return Optional.of(negated ? ("!" + replacement) : replacement);
      }

      private String replaceLast(String text, String pattern, String replacement) {
        StringBuilder builder = new StringBuilder(text);
        int lastIndexOf = builder.lastIndexOf(pattern);
        return builder.replace(lastIndexOf, lastIndexOf + pattern.length(), replacement).toString();
      }
    },
    /** {@code proto.getRepeatedFoo(index)} */
    VECTOR_INDEXED {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!PROTO_RECEIVER.matches(tree, state)) {
          return null;
        }
        if (!(tree instanceof MethodInvocationTree method)) {
          return null;
        }
        if (method.getArguments().size() != 1 || !isGetter(method.getMethodSelect())) {
          return null;
        }
        if (!isSameType(
            getType(getOnlyElement(method.getArguments())), state.getSymtab().intType, state)) {
          return null;
        }
        return (n, s) -> Optional.of(generateFix(method, n, state));
      }

      private String generateFix(
          MethodInvocationTree methodInvocation, boolean negated, VisitorState visitorState) {
        String methodName = ASTHelpers.getSymbol(methodInvocation).getQualifiedName().toString();
        String countMethod = methodName + "Count";
        return format(
            "%s.%s() %s %s",
            visitorState.getSourceForNode(getReceiver(methodInvocation)),
            countMethod,
            negated ? "<=" : ">",
            visitorState.getSourceForNode(getOnlyElement(methodInvocation.getArguments())));
      }
    },
    /** {@code proto.getRepeatedFooList()} */
    VECTOR {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!PROTO_RECEIVER.matches(tree, state)) {
          return null;
        }
        if (!(tree instanceof MethodInvocationTree method)) {
          return null;
        }
        if (!method.getArguments().isEmpty()) {
          return null;
        }
        if (!RETURNS_LIST.matches(method, state)) {
          return null;
        }
        ExpressionTree expressionTree = method.getMethodSelect();
        return isGetter(expressionTree)
            ? (n, s) -> Optional.of(generateFix(n, method, state))
            : null;
      }

      private String generateFix(
          boolean negated, ExpressionTree methodInvocation, VisitorState state) {
        String replacement = state.getSourceForNode(methodInvocation) + ".isEmpty()";
        return negated ? replacement : ("!" + replacement);
      }
    },
    /** {@code proto.getField(f)} or {@code proto.getExtension(outer, extension)}; */
    EXTENSION_METHOD {
      @Override
      @Nullable Fixer match(ExpressionTree tree, VisitorState state) {
        if (!PROTO_RECEIVER.matches(tree, state)) {
          return null;
        }
        if (EXTENSION_METHODS_WITH_NO_FIX.matches(tree, state)) {
          return GetterTypes::emptyFix;
        }
        if (EXTENSION_METHODS_WITH_FIX.matches(tree, state)) {
          // If the extension represents a repeated field (i.e.: it's an ExtensionLite<T, List<R>>),
          // the suggested fix from get->has isn't appropriate,so we shouldn't suggest a replacement

          MethodInvocationTree methodInvocation = (MethodInvocationTree) tree;
          Type argumentType =
              ASTHelpers.getType(Iterables.getOnlyElement(methodInvocation.getArguments()));
          Symbol extension = COM_GOOGLE_PROTOBUF_EXTENSIONLITE.get(state);
          Type genericsArgument = state.getTypes().asSuper(argumentType, extension);

          // If there are not two arguments then it is a raw type
          // We can't make a fix on a raw type because there is not a way to guarantee that
          // it does not contain a repeated field
          if (genericsArgument.getTypeArguments().size() != 2) {
            return GetterTypes::emptyFix;
          }

          // If the second element within the generic argument is a subtype of list,
          // that means it is a repeated field and therefore we cannot make a fix.
          if (ASTHelpers.isSubtype(
              genericsArgument.getTypeArguments().get(1), state.getSymtab().listType, state)) {
            return GetterTypes::emptyFix;
          }
          // Now that it is guaranteed that there is not a repeated field, providing a fix is safe
          return generateFix(methodInvocation);
        }
        return null;
      }

      private Fixer generateFix(MethodInvocationTree methodInvocation) {
        return (negated, state) -> {
          String methodName = getMethodName(methodInvocation);
          String hasMethod = methodName.replaceFirst("get", "has");
          String replacement =
              replaceLast(state.getSourceForNode(methodInvocation), methodName, hasMethod);
          return Optional.of(negated ? "!" + replacement : replacement);
        };
      }
    };

    /**
     * Returns a Fixer representing a situation where we don't have a fix, but want to mark a
     * callsite as containing a bug.
     */
    private static Optional<String> emptyFix(boolean n, VisitorState s) {
      return Optional.empty();
    }

    private static boolean isGetter(ExpressionTree expressionTree) {
      if (!(expressionTree instanceof JCFieldAccess access)) {
        return false;
      }
      String methodName = access.sym.getQualifiedName().toString();
      return methodName.startsWith("get");
    }

    abstract Fixer match(ExpressionTree tree, VisitorState state);
  }

  private enum ProblemUsage {
    /** Matches direct comparisons to null. */
    COMPARISON {
      @Override
      SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state) {
        Optional<String> replacement = fixer.getReplacement(tree.getKind() == Kind.EQUAL_TO, state);
        return replacement.map(r -> SuggestedFix.replace(tree, r)).orElse(SuggestedFix.emptyFix());
      }
    },
    /** Matches comparisons with Truth, i.e. {@code assertThat(proto.getField()).isNull()}. */
    TRUTH {
      @Override
      SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state) {
        return fixer
            .getReplacement(/* negated= */ false, state)
            .map(
                r -> {
                  MethodInvocationTree receiver = (MethodInvocationTree) getReceiver(tree);
                  return SuggestedFix.replace(
                      tree,
                      format(
                          "%s(%s).isTrue()",
                          state.getSourceForNode(receiver.getMethodSelect()), r));
                })
            .orElse(SuggestedFix.emptyFix());
      }
    },
    /** Matches comparisons with JUnit, i.e. {@code assertNotNull(proto.getField())}. */
    JUNIT {
      @Override
      SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state) {
        MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
        return fixer
            .getReplacement(/* negated= */ false, state)
            .map(
                r -> {
                  int startPos = getStartPosition(methodInvocationTree);
                  return SuggestedFix.builder()
                      .replace(getLast(methodInvocationTree.getArguments()), r)
                      .replace(startPos, startPos + "assertNotNull".length(), "assertTrue")
                      .build();
                })
            .orElse(SuggestedFix.emptyFix());
      }
    },
    /** Matches precondition checks, i.e. {@code checkNotNull(proto.getField())}. */
    CHECK_NOT_NULL {
      @Override
      SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state) {
        MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
        Tree parent = state.getPath().getParentPath().getLeaf();
        return parent instanceof ExpressionStatementTree
            ? SuggestedFix.delete(parent)
            : SuggestedFix.replace(
                tree, state.getSourceForNode(methodInvocationTree.getArguments().get(0)));
      }
    },
    /** Matches comparisons with JUnit, i.e. {@code assertNotNull(proto.getField())}. */
    OPTIONAL {
      @Override
      SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state) {
        MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
        return SuggestedFixes.renameMethodInvocation(methodInvocationTree, "of", state);
      }
    };

    abstract SuggestedFix fix(Fixer fixer, ExpressionTree tree, VisitorState state);
  }

  private static final Supplier<Symbol> COM_GOOGLE_PROTOBUF_EXTENSIONLITE =
      VisitorState.memoize(state -> state.getSymbolFromString("com.google.protobuf.ExtensionLite"));
}
