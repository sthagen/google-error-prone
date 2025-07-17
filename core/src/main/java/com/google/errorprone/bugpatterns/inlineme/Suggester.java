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

package com.google.errorprone.bugpatterns.inlineme;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.InjectMatchers.hasProvidesAnnotation;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.ASTHelpers.hasDirectAnnotationWithSimpleName;
import static com.google.errorprone.util.AnnotationNames.DO_NOT_CALL_ANNOTATION;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.WellKnownKeep;
import com.google.errorprone.bugpatterns.inlineme.InlinabilityResult.InlineValidationErrorReason;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MethodTree;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;

/** Checker that recommends using {@code @InlineMe} on single-statement deprecated APIs. */
@BugPattern(
    name = "InlineMeSuggester",
    summary =
        "This deprecated API looks inlineable. If you'd like the body of the API to be"
            + " automatically inlined to its callers, please annotate it with @InlineMe."
            + " NOTE: the suggested fix makes the method final if it was not already.",
    severity = WARNING)
public final class Suggester extends BugChecker implements MethodTreeMatcher {
  private static final String INLINE_ME = "com.google.errorprone.annotations.InlineMe";

  private final WellKnownKeep wellKnownKeep;

  @Inject
  Suggester(WellKnownKeep wellKnownKeep) {
    this.wellKnownKeep = wellKnownKeep;
  }

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    // only suggest @InlineMe on @Deprecated APIs
    if (!hasAnnotation(tree, Deprecated.class.getName(), state)) {
      return Description.NO_MATCH;
    }

    // if the API is already annotated with @InlineMe, then return no match
    if (hasDirectAnnotationWithSimpleName(tree, "InlineMe")) {
      return Description.NO_MATCH;
    }

    // if the API is already annotated with @DoNotCall, then return no match
    if (hasAnnotation(tree, DO_NOT_CALL_ANNOTATION, state)) {
      return Description.NO_MATCH;
    }

    // don't suggest on APIs that get called reflectively
    if (wellKnownKeep.shouldKeep(tree) || hasProvidesAnnotation().matches(tree, state)) {
      return Description.NO_MATCH;
    }

    // if the body is not inlinable, then return no match
    InlinabilityResult inlinabilityResult = InlinabilityResult.forMethod(tree, state);
    if (!inlinabilityResult.isValidForSuggester()) {
      return Description.NO_MATCH;
    }

    // We attempt to actually build the annotation as a SuggestedFix.
    SuggestedFix.Builder fixBuilder =
        SuggestedFix.builder()
            .addImport(INLINE_ME)
            .prefixWith(
                tree,
                InlineMeData.buildExpectedInlineMeAnnotation(state, inlinabilityResult.body())
                    .buildAnnotation());
    if (inlinabilityResult.error()
        == InlineValidationErrorReason.METHOD_CAN_BE_OVERRIDDEN_BUT_CAN_BE_FIXED) {
      SuggestedFixes.addModifiers(tree, state, Modifier.FINAL).ifPresent(fixBuilder::merge);
    }
    return describeMatch(tree, fixBuilder.build());
  }
}
