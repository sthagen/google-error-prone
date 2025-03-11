/*
 * Copyright 2014 The Error Prone Authors.
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

package com.google.errorprone.refaster;

import static com.google.errorprone.refaster.Unifier.unifications;
import static com.google.errorprone.refaster.Unifier.unifyList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.TryTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCTry;
import org.jspecify.annotations.Nullable;

/**
 * {@code UTree} representation of a {@code TryTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
abstract class UTry extends USimpleStatement implements TryTree {
  static UTry create(
      Iterable<? extends UTree<?>> resources,
      UBlock block,
      Iterable<UCatch> catches,
      @Nullable UBlock finallyBlock) {
    return new AutoValue_UTry(
        ImmutableList.copyOf(resources), block, ImmutableList.copyOf(catches), finallyBlock);
  }

  @Override
  public abstract ImmutableList<UTree<?>> getResources();

  @Override
  public abstract UBlock getBlock();

  @Override
  public abstract ImmutableList<UCatch> getCatches();

  @Override
  public abstract @Nullable UBlock getFinallyBlock();

  @Override
  public Kind getKind() {
    return Kind.TRY;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitTry(this, data);
  }

  @Override
  public JCTry inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner
        .maker()
        .Try(
            inliner.<JCTree>inlineList(getResources()),
            getBlock().inline(inliner),
            inliner.inlineList(getCatches()),
            inlineFinallyBlock(inliner));
  }

  /** Skips the finally block if the result would be empty. */
  private @Nullable JCBlock inlineFinallyBlock(Inliner inliner)
      throws CouldNotResolveImportException {
    if (getFinallyBlock() != null) {
      JCBlock block = getFinallyBlock().inline(inliner);
      if (!block.getStatements().isEmpty()) {
        return block;
      }
    }
    return null;
  }

  @Override
  public @Nullable Choice<Unifier> visitTry(TryTree node, @Nullable Unifier unifier) {
    return unifyList(unifier, getResources(), node.getResources())
        .flatMap(unifications(getBlock(), node.getBlock()))
        .flatMap(unifications(getCatches(), node.getCatches()))
        .flatMap(unifications(getFinallyBlock(), node.getFinallyBlock()));
  }
}
