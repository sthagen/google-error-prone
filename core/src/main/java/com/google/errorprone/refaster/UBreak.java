/*
 * Copyright 2014 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@code UTree} representation of {@code BreakTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
final class UBreak extends USimpleStatement implements BreakTree {

  private final StringName label;

  UBreak(StringName label) {
    this.label = label;
  }

  static UBreak create(@Nullable CharSequence label) {
    return new UBreak((label == null) ? null : StringName.of(label));
  }

  // TODO(b/176098078): Add @Override once compiling JDK 12+
  public @Nullable ExpressionTree getValue() {
    return null;
  }

  @Override
  public @Nullable StringName getLabel() {
    return label;
  }

  @Override
  public Kind getKind() {
    return Kind.BREAK;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitBreak(this, data);
  }

  private ULabeledStatement.Key key() {
    return new ULabeledStatement.Key(getLabel());
  }

  @Override
  public JCBreak inline(Inliner inliner) {
    return inliner.maker().Break(ULabeledStatement.inlineLabel(getLabel(), inliner));
  }

  @Override
  public Choice<Unifier> visitBreak(BreakTree node, Unifier unifier) {
    if (getLabel() == null) {
      return Choice.condition(node.getLabel() == null, unifier);
    } else {
      CharSequence boundName = unifier.getBinding(key());
      return Choice.condition(
          boundName != null && node.getLabel().contentEquals(boundName), unifier);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(label);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UBreak other && Objects.equals(label, other.label);
  }
}
