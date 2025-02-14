/*
 * Copyright 2020 The Error Prone Authors.
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

import static com.google.errorprone.bugpatterns.SerializableReads.BANNED_OBJECT_INPUT_STREAM_METHODS;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.enclosingClass;
import static com.google.errorprone.matchers.Matchers.enclosingMethod;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.isSubtypeOf;
import static com.google.errorprone.matchers.Matchers.methodIsNamed;
import static com.google.errorprone.matchers.Matchers.not;

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.MethodInvocationTree;

/** A {@link BugChecker} that detects use of the unsafe {@link java.io.Serializable} API. */
@BugPattern(
    summary = "Deserializing user input via the `Serializable` API is extremely dangerous",
    severity = SeverityLevel.ERROR)
public final class BanSerializableRead extends AbstractBanUnsafeAPIChecker
    implements MethodInvocationTreeMatcher {

  private static final Matcher<MethodInvocationTree> EXEMPT =
      anyOf(
          //  This is called through ObjectInputStream; a call further up the callstack will have
          // been exempt.
          allOf(
              enclosingClass(isSubtypeOf("java.io.Serializable")),
              enclosingMethod(methodIsNamed("readObject"))),
          allOf(
              enclosingClass(isSubtypeOf("java.io.ObjectInputStream")),
              enclosingMethod(
                  (methodTree, state) ->
                      BANNED_OBJECT_INPUT_STREAM_METHODS.contains(
                          methodTree.getName().toString()))));

  /** Checks for unsafe deserialization calls on an ObjectInputStream in an ExpressionTree. */
  private static final Matcher<MethodInvocationTree> OBJECT_INPUT_STREAM_DESERIALIZE_MATCHER =
      allOf(
          anyOf(
              // this matches calls to the ObjectInputStream to read some objects
              instanceMethod()
                  .onDescendantOf("java.io.ObjectInputStream")
                  .namedAnyOf(BANNED_OBJECT_INPUT_STREAM_METHODS),

              // because in the next part we exempt readObject functions, here we
              // check for calls to those functions
              instanceMethod()
                  .onDescendantOf("java.io.Serializable")
                  .named("readObject")
                  .withParameters("java.io.ObjectInputStream"),

              // we need to ban java.io.ObjectInput.readObject too, but most of the time it's called
              // inside java.io.Externalizable.readExternal. Also ban direct calls of readExternal,
              // unless it's inside another readExternal
              allOf(
                  anyOf(
                      instanceMethod().onDescendantOf("java.io.ObjectInput").named("readObject"),
                      instanceMethod()
                          .onDescendantOf("java.io.Externalizable")
                          .named("readExternal")),
                  // skip banning things inside readExternal implementation
                  not(
                      allOf(
                          enclosingMethod(methodIsNamed("readExternal")),
                          enclosingClass(isSubtypeOf("java.io.Externalizable")))))),

          // Java lets you override or add to the default deserialization behaviour
          // by defining a 'readObject' on your class. In this case, it's super common
          // to see calls to deserialize methods (after all, it's what *would* happen
          // if it *were* deserialized). We specifically want to allow such members to
          // be defined, but never called
          not(EXEMPT));

  /** Checks for unsafe uses of the Java deserialization API. */
  private static final Matcher<MethodInvocationTree> MATCHER =
      OBJECT_INPUT_STREAM_DESERIALIZE_MATCHER;

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    return this.matchHelper(tree, state, MATCHER);
  }
}
