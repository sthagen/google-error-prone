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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.Parser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.mockito.hamcrest.MockitoHamcrest;

/**
 * Basics for testing {@code UTree} implementations.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
public abstract class AbstractUTreeTest {
  protected Context context;
  protected Unifier unifier;
  protected Inliner inliner;

  @Before
  public void createContext() {
    context = new Context();
    JavacFileManager.preRegister(context);
    JavaCompiler compiler = JavaCompiler.instance(context);
    compiler.initModules(List.nil());
    unifier = new Unifier(context);
    inliner = unifier.createInliner();
  }

  public void assertUnifiesAndInlines(String expression, UTree<?> template) {
    assertUnifies(expression, template);
    assertInlines(expression, template);
  }

  public void assertUnifies(String expression, UTree<?> template) {
    assertWithMessage(
            String.format("Expected template %s to unify with expression %s", template, expression))
        .that(template.unify(parseExpression(expression), unifier).findFirst())
        .isPresent();
  }

  public void assertInlines(String expression, UTree<?> template) {
    try {
      assertWithMessage(
              String.format(
                  "Expected template %s to inline to expression %s", template, expression))
          .that(template.inline(inliner).toString())
          .isEqualTo(expression);
    } catch (CouldNotResolveImportException e) {
      throw new RuntimeException(e);
    }
  }

  public void assertInlines(String expression, UStatement template) {
    try {
      // javac's pretty-printer uses the platform line terminator
      assertWithMessage(
              String.format(
                  "Expected template %s to inline to expression %s", template, expression))
          .that(Joiner.on(System.lineSeparator()).join(template.inlineStatements(inliner)))
          .isEqualTo(expression);
    } catch (CouldNotResolveImportException e) {
      throw new RuntimeException(e);
    }
  }

  protected JCExpression parseExpression(String contents) {
    Parser parser =
        ParserFactory.instance(context)
            .newParser(
                contents,
                /* keepDocComments= */ false,
                /* keepEndPos= */ false,
                /* keepLineMap= */ true);
    return parser.parseExpression();
  }

  protected <V> void bind(Bindings.Key<V> key, V value) {
    Bindings bindings = Bindings.create(inliner.bindings);
    bindings.putBinding(key, value);
    inliner = new Inliner(context, bindings);
  }

  protected JCExpression ident(String name) {
    return MockitoHamcrest.argThat(
        new TypeSafeMatcher<JCExpression>() {
          @Override
          public void describeTo(Description description) {
            description.appendText("Identifier matching \"" + name + "\"");
          }

          @Override
          public boolean matchesSafely(JCExpression item) {
            return item instanceof JCIdent jCIdent && jCIdent.getName().contentEquals(name);
          }
        });
  }
}
