/*
 * Copyright 2013 The Error Prone Authors.
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

import static com.google.errorprone.refaster.Unifier.unifications;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link UTree} version of {@link MethodInvocationTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UMethodInvocation extends UExpression implements MethodInvocationTree {
  public static UMethodInvocation create(
      List<? extends UExpression> typeArguments,
      UExpression methodSelect,
      List<UExpression> arguments) {
    return new AutoValue_UMethodInvocation(
        ImmutableList.copyOf(typeArguments), methodSelect, ImmutableList.copyOf(arguments));
  }

  public static UMethodInvocation create(
      List<? extends UExpression> typeArguments,
      UExpression methodSelect,
      UExpression... arguments) {
    return create(typeArguments, methodSelect, ImmutableList.copyOf(arguments));
  }

  public static UMethodInvocation create(UExpression methodSelect, UExpression... arguments) {
    return create(ImmutableList.of(), methodSelect, ImmutableList.copyOf(arguments));
  }

  @Override
  public abstract ImmutableList<UExpression> getTypeArguments();

  @Override
  public abstract UExpression getMethodSelect();

  @Override
  public abstract ImmutableList<UExpression> getArguments();

  @Override
  public @Nullable Choice<Unifier> visitMethodInvocation(
      MethodInvocationTree methodInvocation, @Nullable Unifier unifier) {
    return getMethodSelect()
        .unify(methodInvocation.getMethodSelect(), unifier)
        .flatMap(
            unifications(
                getArguments(), methodInvocation.getArguments(), /* allowVarargs= */ true));
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitMethodInvocation(this, data);
  }

  @Override
  public Kind getKind() {
    return Kind.METHOD_INVOCATION;
  }

  @Override
  public JCMethodInvocation inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner
        .maker()
        .Apply(
            inliner.<JCExpression>inlineList(getTypeArguments()),
            getMethodSelect().inline(inliner),
            inliner.<JCExpression>inlineList(getArguments()));
  }
}
