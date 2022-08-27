/*
 * Copyright 2022 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.checkreturnvalue;

import static com.google.errorprone.bugpatterns.checkreturnvalue.ResultUsePolicy.EXPECTED;
import static com.google.errorprone.bugpatterns.checkreturnvalue.ResultUsePolicy.OPTIONAL;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.ASTHelpers.isAbstract;
import static com.google.errorprone.util.ASTHelpers.isSameType;
import static com.google.errorprone.util.ASTHelpers.streamSuperMethods;
import static java.util.stream.Stream.concat;

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.checkreturnvalue.ResultUseRule.MethodRule;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.Optional;
import java.util.stream.Stream;

/** Rules for {@code @AutoValue}, {@code @AutoValue.Builder}, and {@code @AutoBuilder} types. */
public final class AutoValueRules {
  private AutoValueRules() {}

  /** Returns a rule for {@code abstract} methods on {@code @AutoValue} types. */
  public static ResultUseRule autoValues() {
    return new ValueRule();
  }

  /** Returns a rule for {@code abstract} methods on {@code @AutoValue.Builder} types. */
  public static ResultUseRule autoValueBuilders() {
    return new BuilderRule("AutoValue.Builder");
  }

  /** Returns a rule for {@code abstract} methods on {@code @AutoBuilder} types. */
  public static ResultUseRule autoBuilders() {
    return new BuilderRule("AutoBuilder");
  }

  private static final class ValueRule extends AbstractAutoRule {
    ValueRule() {
      super("AutoValue");
    }

    @Override
    protected ResultUsePolicy autoMethodPolicy(
        MethodSymbol abstractMethod, ClassSymbol autoClass, VisitorState state) {
      return EXPECTED;
    }
  }

  private static final class BuilderRule extends AbstractAutoRule {
    BuilderRule(String annotation) {
      super(annotation);
    }

    @Override
    protected ResultUsePolicy autoMethodPolicy(
        MethodSymbol abstractMethod, ClassSymbol autoClass, VisitorState state) {
      return abstractMethod.getParameters().size() == 1
              && isSameType(abstractMethod.getReturnType(), autoClass.type, state)
          ? OPTIONAL
          : EXPECTED;
    }
  }

  private abstract static class AbstractAutoRule extends MethodRule {
    private static final String PACKAGE = "com.google.auto.value.";

    private final String simpleAnnotation;
    private final String qualifiedAnnotation;

    AbstractAutoRule(String simpleAnnotation) {
      this.simpleAnnotation = simpleAnnotation;
      this.qualifiedAnnotation = PACKAGE + simpleAnnotation;
    }

    @Override
    public String id() {
      return '@' + simpleAnnotation;
    }

    protected abstract ResultUsePolicy autoMethodPolicy(
        MethodSymbol abstractMethod, ClassSymbol autoClass, VisitorState state);

    @Override
    public Optional<ResultUsePolicy> evaluateMethod(MethodSymbol method, VisitorState state) {
      /*
       * Sometimes, calls are made on an object whose static type is one of the AutoValue generated
       * classes:
       *
       * - A variable is declared with a type like `AutoValue_Foo`, or it implicitly has that type
       *   because it's declared with `var`.
       *
       * - An AutoValue extension's Builder class's methods delegates to supermethods also
       *   generated by AutoValue.Builder.
       *
       * To handle this, we walk up the type hierarchy.
       */
      return concat(Stream.of(method), streamSuperMethods(method, state.getTypes()))
          .filter(
              m -> isAbstract(m) && hasAnnotation(enclosingClass(m), qualifiedAnnotation, state))
          .findFirst()
          .map(methodSymbol -> autoMethodPolicy(methodSymbol, enclosingClass(methodSymbol), state));
    }
  }
}
