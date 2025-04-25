/*
 * Copyright 2016 The Error Prone Authors.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.zip;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;
import static com.google.errorprone.util.ASTHelpers.streamSuperMethods;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.Signatures;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary = "Overloads will be ambiguous when passing lambda arguments.",
    severity = WARNING)
public class FunctionalInterfaceClash extends BugChecker implements ClassTreeMatcher {
  @Override
  public Description matchClass(ClassTree tree, VisitorState state) {
    ClassSymbol origin = getSymbol(tree);
    Types types = state.getTypes();
    // collect declared and inherited methods whose signature contains a functional interface
    SetMultimap<String, MethodSymbol> methodsByName = HashMultimap.create();
    for (Symbol sym :
        types.membersClosure(getType(tree), /* skipInterface= */ false).getSymbols()) {
      if (!(sym instanceof MethodSymbol msym)) {
        continue;
      }
      if (msym.getParameters().stream()
          .noneMatch(p -> maybeFunctionalInterface(p.type, types, state))) {
        continue;
      }
      if (msym.isConstructor() && !msym.owner.equals(origin)) {
        continue;
      }
      methodsByName.put(msym.getSimpleName().toString(), msym);
    }

    // Only consider methods which don't have strictly more specific overloads; these won't actually
    // clash.
    SetMultimap<String, MethodSymbol> methodsBySignature = HashMultimap.create();
    for (MethodSymbol msym : methodsByName.values()) {
      if (methodsByName.get(msym.getSimpleName().toString()).stream()
          .anyMatch(
              o ->
                  !msym.overrides(o, (TypeSymbol) msym.owner, types, /* checkResult= */ true)
                      && !o.equals(msym)
                      && o.getParameters().length() == msym.getParameters().length()
                      && zip(
                              msym.getParameters().stream(),
                              o.getParameters().stream(),
                              (a, b) -> isSubtype(a.type, b.type, state))
                          .allMatch(x -> x))) {
        continue;
      }
      methodsBySignature.put(functionalInterfaceSignature(state, msym), msym);
    }
    // check if any declared members clash with another declared or inherited member
    // (don't report clashes between inherited members)
    for (Tree member : tree.getMembers()) {
      if (!(member instanceof MethodTree methodTree)) {
        continue;
      }
      MethodSymbol msym = getSymbol(methodTree);
      if (msym.getParameters().stream()
          .noneMatch(p -> maybeFunctionalInterface(p.type, types, state))) {
        continue;
      }
      List<MethodSymbol> clash =
          new ArrayList<>(methodsBySignature.removeAll(functionalInterfaceSignature(state, msym)));

      // Ignore inherited methodsBySignature that are overridden in the original class. Note that we
      // have to
      // handle transitive inheritance explicitly to handle cases where the visibility of an
      // overridden method is expanded somewhere in the type hierarchy.
      Deque<MethodSymbol> worklist = new ArrayDeque<>();
      worklist.push(msym);
      clash.remove(msym);
      while (!worklist.isEmpty()) {
        MethodSymbol msym2 = worklist.removeFirst();
        ImmutableList<MethodSymbol> overrides =
            clash.stream()
                .filter(m -> msym2.overrides(m, origin, types, /* checkResult= */ false))
                .collect(toImmutableList());
        worklist.addAll(overrides);
        clash.removeAll(overrides);
      }

      if (!clash.isEmpty()) {
        // ignore if there are overridden clashing methodsBySignature in class
        if (streamSuperMethods(msym, types).anyMatch(t -> !t.owner.isInterface())
            && clash.stream()
                .anyMatch(
                    methodSymbol ->
                        streamSuperMethods(methodSymbol, types)
                            .anyMatch(t -> !t.owner.isInterface()))) {
          continue;
        }

        if (isSuppressed(member, state)) {
          continue;
        }

        String message =
            "When passing lambda arguments to this function, callers will need a cast to"
                + " disambiguate with: "
                + clash.stream()
                    .map(m -> "\n    " + Signatures.prettyMethodSignature(origin, m))
                    .sorted()
                    .collect(joining(""));
        state.reportMatch(buildDescription(member).setMessage(message).build());
      }
    }
    return NO_MATCH;
  }

  /**
   * A string representation of a method descriptor, where all parameters whose type is a functional
   * interface are "erased" to the interface's function type. For example, `foo(Supplier<String>)`
   * is represented as `foo(()->Ljava/lang/String;)`.
   */
  private static String functionalInterfaceSignature(VisitorState state, MethodSymbol msym) {
    return String.format(
        "%s(%s)",
        msym.getSimpleName(),
        msym.getParameters().stream()
            .map(p -> functionalInterfaceSignature(state, p.type))
            .collect(joining(",")));
  }

  private static String functionalInterfaceSignature(VisitorState state, Type type) {
    Types types = state.getTypes();
    if (!maybeFunctionalInterface(type, types, state)) {
      return Signatures.descriptor(type, state);
    }
    Type descriptorType = types.findDescriptorType(type);
    List<Type> fiparams = descriptorType.getParameterTypes();
    // Implicitly typed block-statement-bodied lambdas are potentially compatible with
    // void-returning and value-returning functional interface types, so we don't consider return
    // types in general. The except is nullary functional interfaces, since the lambda parameters
    // will never be implicitly typed.
    String result =
        fiparams.isEmpty() ? Signatures.descriptor(descriptorType.getReturnType(), state) : "_";
    return String.format(
        "(%s)->%s",
        fiparams.stream().map(t -> Signatures.descriptor(t, state)).collect(joining(",")), result);
  }

  private static boolean maybeFunctionalInterface(Type type, Types types, VisitorState state) {
    try {
      return types.isFunctionalInterface(type);
    } catch (CompletionFailure e) {
      // Report completion errors to avoid e.g. https://github.com/bazelbuild/bazel/issues/4105
      Check.instance(state.context)
          .completionError((DiagnosticPosition) state.getPath().getLeaf(), e);
      return false;
    }
  }
}
