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

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.getType;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.code.Type;
import javax.inject.Inject;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    summary =
        "Comparison using reference equality instead of value equality. Reference equality of"
            + " boxed primitive types is usually not useful, as they are value objects, and it is"
            + " bug-prone, as instances are cached for some values but not others.",
    altNames = {"NumericEquality"},
    severity = ERROR)
public final class BoxedPrimitiveEquality extends AbstractReferenceEquality {
  @Inject
  BoxedPrimitiveEquality() {}

  @Override
  protected boolean matchArgument(ExpressionTree tree, VisitorState state) {
    var type = getType(tree);
    return type != null && isRelevantType(type, state);
  }

  private boolean isRelevantType(Type type, VisitorState state) {
    return !type.isPrimitive() && state.getTypes().unboxedType(type).isPrimitive();
  }
}
