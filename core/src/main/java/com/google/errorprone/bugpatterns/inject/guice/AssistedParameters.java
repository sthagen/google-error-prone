/*
 * Copyright 2013 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.errorprone.bugpatterns.inject.guice;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.InjectMatchers.ASSISTED_ANNOTATION;
import static com.google.errorprone.matchers.InjectMatchers.ASSISTED_INJECT_ANNOTATION;
import static com.google.errorprone.matchers.InjectMatchers.hasInjectAnnotation;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.hasAnnotation;
import static com.google.errorprone.matchers.Matchers.methodHasParameters;
import static com.google.errorprone.matchers.Matchers.methodIsConstructor;

import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.MultiMatcher;
import com.google.errorprone.matchers.MultiMatcher.MultiMatchResult;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
@BugPattern(
    name = "GuiceAssistedParameters",
    summary =
        "A constructor cannot have two @Assisted parameters of the same type unless they are "
            + "disambiguated with named @Assisted annotations.",
    severity = ERROR)
public class AssistedParameters extends BugChecker implements MethodTreeMatcher {

  private static final Matcher<MethodTree> IS_CONSTRUCTOR_WITH_INJECT_OR_ASSISTED =
      allOf(
          methodIsConstructor(),
          anyOf(hasInjectAnnotation(), hasAnnotation(ASSISTED_INJECT_ANNOTATION)));

  private static final MultiMatcher<MethodTree, VariableTree> ASSISTED_PARAMETER_MATCHER =
      methodHasParameters(MatchType.AT_LEAST_ONE, Matchers.hasAnnotation(ASSISTED_ANNOTATION));

  @Override
  public final Description matchMethod(MethodTree constructor, VisitorState state) {
    if (!IS_CONSTRUCTOR_WITH_INJECT_OR_ASSISTED.matches(constructor, state)) {
      return Description.NO_MATCH;
    }

    // Gather @Assisted parameters, partition by type
    MultiMatchResult<VariableTree> assistedParameters =
        ASSISTED_PARAMETER_MATCHER.multiMatchResult(constructor, state);
    if (!assistedParameters.matches()) {
      return Description.NO_MATCH;
    }

    Multimap<Type, VariableTree> parametersByType =
        partitionParametersByType(assistedParameters.matchingNodes(), state);

    // If there's more than one parameter with the same type, they could conflict unless their
    // @Assisted values are different.
    List<ConflictResult> conflicts = new ArrayList<>();
    for (Map.Entry<Type, Collection<VariableTree>> typeAndParameters :
        parametersByType.asMap().entrySet()) {
      Collection<VariableTree> parametersForThisType = typeAndParameters.getValue();
      if (parametersForThisType.size() < 2) {
        continue;
      }

      // Gather the @Assisted value from each parameter. If any value is repeated amongst the
      // parameters in this type, it's a compile error.
      ImmutableListMultimap<String, VariableTree> keyForAssistedVariable =
          Multimaps.index(parametersForThisType, AssistedParameters::valueFromAssistedAnnotation);

      for (Map.Entry<String, List<VariableTree>> assistedValueToParameters :
          Multimaps.asMap(keyForAssistedVariable).entrySet()) {
        if (assistedValueToParameters.getValue().size() > 1) {
          conflicts.add(
              ConflictResult.create(
                  typeAndParameters.getKey(),
                  assistedValueToParameters.getKey(),
                  ImmutableList.copyOf(assistedValueToParameters.getValue())));
        }
      }
    }

    if (conflicts.isEmpty()) {
      return Description.NO_MATCH;
    }

    return buildDescription(constructor).setMessage(buildErrorMessage(conflicts)).build();
  }

  private static String valueFromAssistedAnnotation(VariableTree variableTree) {
    for (Compound c : ASTHelpers.getSymbol(variableTree).getAnnotationMirrors()) {
      if (MoreElements.asType(c.getAnnotationType().asElement())
          .getQualifiedName()
          .contentEquals(ASSISTED_ANNOTATION)) {
        // Assisted only has 'value', and value can only contain 1 element.
        Collection<Attribute> valueEntries = c.getElementValues().values();
        if (!valueEntries.isEmpty()) {
          return Iterables.getOnlyElement(valueEntries).getValue().toString();
        }
      }
    }
    return "";
  }

  private static String buildErrorMessage(List<ConflictResult> conflicts) {
    StringBuilder sb =
        new StringBuilder(
            " Assisted parameters of the same type need to have distinct values for the @Assisted"
                + " annotation. There are conflicts between the annotations on this constructor:");

    for (ConflictResult conflict : conflicts) {
      sb.append("\n").append(conflict.type());

      if (!conflict.value().isEmpty()) {
        sb.append(", @Assisted(\"").append(conflict.value()).append("\")");
      }
      sb.append(": ");

      List<String> simpleParameterNames =
          Lists.transform(conflict.parameters(), t -> t.getName().toString());
      Joiner.on(", ").appendTo(sb, simpleParameterNames);
    }

    return sb.toString();
  }

  private record ConflictResult(Type type, String value, ImmutableList<VariableTree> parameters) {
    static ConflictResult create(Type t, String v, ImmutableList<VariableTree> p) {
      return new ConflictResult(t, v, p);
    }
  }

  // Since Type doesn't have strong equality semantics, we have to use Types.isSameType to
  // determine which parameters are conflicting with each other.
  private static ListMultimap<Type, VariableTree> partitionParametersByType(
      List<VariableTree> parameters, VisitorState state) {

    Types types = state.getTypes();
    ListMultimap<Type, VariableTree> multimap = LinkedListMultimap.create();

    variables:
    for (VariableTree node : parameters) {
      // Normalize Integer => int
      Type type = types.unboxedTypeOrType(ASTHelpers.getType(node));
      for (Type existingType : multimap.keySet()) {
        if (types.isSameType(existingType, type)) {
          multimap.put(existingType, node);
          continue variables;
        }
      }

      // A new type for the map.
      multimap.put(type, node);
    }

    return multimap;
  }
}
