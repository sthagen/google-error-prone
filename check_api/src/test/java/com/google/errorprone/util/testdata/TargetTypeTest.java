/*
 * Copyright 2018 The Error Prone Authors.
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

package com.google.errorprone.util.testdata;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class TargetTypeTest {
  void unary() {
    System.out.println(
        // BUG: Diagnostic contains: boolean
        !detectWrappedBoolean());
    System.out.println(
        // BUG: Diagnostic contains: boolean
        !detectPrimitiveBoolean());
    System.out.println(
        // BUG: Diagnostic contains: int
        ~detectPrimitiveInt());
    System.out.println(
        // BUG: Diagnostic contains: int
        ~detectWrappedInteger());
    System.out.println(
        // BUG: Diagnostic contains: int
        ~detectPrimitiveByte());
  }

  void binary(boolean b) {
    // BUG: Diagnostic contains: int
    long a1 = detectPrimitiveInt() + 20;
    // BUG: Diagnostic contains: int
    long a2 = detectWrappedInteger() - 20;
    // BUG: Diagnostic contains: long
    long a3 = detectPrimitiveInt() + 20L;
    // BUG: Diagnostic contains: long
    long a4 = detectWrappedInteger() - 20L;

    // BUG: Diagnostic contains: boolean
    boolean b1 = detectPrimitiveBoolean() & b;
    // BUG: Diagnostic contains: boolean
    boolean b2 = b || detectWrappedBoolean();

    // BUG: Diagnostic contains: java.lang.String
    String s1 = detectString() + "";
    // BUG: Diagnostic contains: java.lang.String
    String s2 = null + detectString();
    // BUG: Diagnostic contains: java.lang.String
    String s3 = 0 + detectString();

    // BUG: Diagnostic contains: java.lang.String
    boolean eq1 = detectString() == "";
    // BUG: Diagnostic contains: java.lang.String
    boolean eq2 = null != detectString();

    // BUG: Diagnostic contains: int
    boolean eq3 = detectPrimitiveInt() == 0;
    // BUG: Diagnostic contains: int
    boolean eq4 = 0 == detectWrappedInteger();
  }

  void binary_shift() {
    // The shift operator is unusual in terms of binary operators, in that the operands undergo
    // unary numeric promotion separately.

    // BUG: Diagnostic contains: int
    System.out.println(detectPrimitiveInt() << 1L);
    // BUG: Diagnostic contains: int
    System.out.println(1L << detectPrimitiveInt());

    int i = 1;
    // BUG: Diagnostic contains: int
    i >>>= detectPrimitiveInt();
    // BUG: Diagnostic contains: int
    i >>>= detectWrappedInteger();

    long a = 1;
    // BUG: Diagnostic contains: int
    a <<= detectPrimitiveInt();
    // BUG: Diagnostic contains: int
    a >>>= detectWrappedInteger();
    // BUG: Diagnostic contains: int
    a >>= detectWrappedInteger();
  }

  void conditional_condition() {
    // BUG: Diagnostic contains: boolean
    System.out.println(detectPrimitiveBoolean() ? "" : "");
    // BUG: Diagnostic contains: boolean
    System.out.println(detectWrappedBoolean() ? "" : "");
  }

  void conditional_trueExpression(boolean b) {
    // BUG: Diagnostic contains: int
    System.out.println(b ? detectWrappedInteger() : 0);
  }

  void conditional_trueExpression_noUnboxing(boolean b) {
    // BUG: Diagnostic contains: java.lang.Integer
    System.out.println(b ? detectWrappedInteger() : Integer.valueOf(0));
  }

  void conditional_conditionalInCondition(boolean b1, boolean b2) {
    // BUG: Diagnostic contains: long
    System.out.println(((detectPrimitiveInt() != 0L) ? b1 : b2) ? "" : "");
  }

  void ifStatement() {
    if (
    // BUG: Diagnostic contains: boolean
    detectPrimitiveBoolean()) {}
    if (
    // BUG: Diagnostic contains: boolean
    detectWrappedBoolean()) {}
  }

  void ifElseStatement() {
    if (true) {
    } else if (
    // BUG: Diagnostic contains: boolean
    detectPrimitiveBoolean()) {
    }
    if (true) {
    } else if (
    // BUG: Diagnostic contains: boolean
    detectWrappedBoolean()) {
    }
  }

  void whileLoop() {
    while (
    // BUG: Diagnostic contains: boolean
    detectPrimitiveBoolean()) {}
    while (
    // BUG: Diagnostic contains: boolean
    detectWrappedBoolean()) {}
  }

  void doWhileLoop() {
    do {} while (
    // BUG: Diagnostic contains: boolean
    detectPrimitiveBoolean());
    do {} while (
    // BUG: Diagnostic contains: boolean
    detectWrappedBoolean());
  }

  void forLoop() {
    for (;
        // BUG: Diagnostic contains: boolean
        detectPrimitiveBoolean(); ) {}
    for (;
        // BUG: Diagnostic contains: boolean
        detectWrappedBoolean(); ) {}
  }

  void typesOfDetectMethods() {
    // BUG: Diagnostic contains: byte
    byte primitiveByte = detectPrimitiveByte();
    // BUG: Diagnostic contains: boolean
    boolean primitiveBoolean = detectPrimitiveBoolean();
    // BUG: Diagnostic contains: int
    int primitiveInt = detectPrimitiveInt();
    // BUG: Diagnostic contains: java.lang.Boolean
    Boolean wrappedBoolean = detectWrappedBoolean();
    // BUG: Diagnostic contains: java.lang.Integer
    Integer wrappedInteger = detectWrappedInteger();
  }

  void arrayAccess(String[] s) {
    // BUG: Diagnostic contains: int
    System.out.println(s[detectPrimitiveInt()]);
    // BUG: Diagnostic contains: int
    System.out.println(s[detectWrappedInteger()]);
    // BUG: Diagnostic contains: java.lang.String[]
    System.out.println(detectStringArray()[0]);
  }

  void switchStatement() {
    // BUG: Diagnostic contains: int
    switch (detectPrimitiveInt()) {
    }
    // BUG: Diagnostic contains: int
    switch (detectWrappedInteger()) {
    }
    // BUG: Diagnostic contains: java.lang.String
    switch (detectString()) {
    }
    // BUG: Diagnostic contains: com.google.errorprone.util.testdata.TargetTypeTest.ThisEnum
    switch (detectThisEnum()) {
    }
  }

  int[] array_intInPrimitiveIntArray() {
    // BUG: Diagnostic contains: int
    int[] array = {detectPrimitiveInt()};
    // BUG: Diagnostic contains: int
    return new int[] {detectPrimitiveInt()};
  }

  int[][] array_intInPrimitiveIntArray2D() {
    // BUG: Diagnostic contains: int
    int[][] array = {{detectPrimitiveInt()}};
    // BUG: Diagnostic contains: int
    return new int[][] {{detectPrimitiveInt()}};
  }

  int[][][] array_byteInPrimitiveIntArray3D() {
    // BUG: Diagnostic contains: int
    int[][][] array = {{{detectPrimitiveByte()}}};
    // BUG: Diagnostic contains: int
    return new int[][][] {{{detectPrimitiveByte()}}};
  }

  int[] array_byteInPrimitiveIntArray() {
    // BUG: Diagnostic contains: int
    int[] array = {detectPrimitiveByte()};
    // BUG: Diagnostic contains: int
    return new int[] {detectPrimitiveByte()};
  }

  Integer[] array_intInWrappedIntegerArray() {
    // BUG: Diagnostic contains: java.lang.Integer
    Integer[] array = {detectPrimitiveInt()};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Integer[] {detectPrimitiveInt()};
  }

  Integer[] array_integerInWrappedIntegerArray() {
    // BUG: Diagnostic contains: java.lang.Integer
    Integer[] array = {detectWrappedInteger()};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Integer[] {detectWrappedInteger()};
  }

  int[] array_integerInPrimitiveIntArray() {
    // BUG: Diagnostic contains: int
    int[] array = {detectWrappedInteger()};
    // BUG: Diagnostic contains: int
    return new int[] {detectWrappedInteger()};
  }

  Integer[][] array_integerInWrappedIntegerArray2D() {
    // BUG: Diagnostic contains: java.lang.Integer
    Integer[][] array = {{detectWrappedInteger()}};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Integer[][] {{detectWrappedInteger()}};
  }

  Integer[][][] array_integerInWrappedIntegerArray3D() {
    // BUG: Diagnostic contains: java.lang.Integer
    Integer[][][] array = {{{detectWrappedInteger()}}};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Integer[][][] {{{detectWrappedInteger()}}};
  }

  Serializable[] array_integerInSerializableArray() {
    // BUG: Diagnostic contains: java.io.Serializable
    Serializable[] array = {detectWrappedInteger()};
    // BUG: Diagnostic contains: java.io.Serializable
    return new Serializable[] {detectWrappedInteger()};
  }

  Object[][][] array_integerInObjectArray3D() {
    // BUG: Diagnostic contains: java.lang.Object
    Object[][][] array = {{{detectWrappedInteger()}}};
    // BUG: Diagnostic contains: java.lang.Object
    return new Object[][][] {{{detectWrappedInteger()}}};
  }

  Object[][] array_integerArrayInObjectArray() {
    // BUG: Diagnostic contains: java.lang.Integer
    Object[][] array = {new Integer[] {detectPrimitiveInt()}};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Object[][] {new Integer[] {detectPrimitiveInt()}};
  }

  Object[][] array_arrayHiddenInsideObjectArray() {
    // BUG: Diagnostic contains: java.lang.Integer
    Object[][] array = {{new Integer[] {detectPrimitiveInt()}}};
    // BUG: Diagnostic contains: java.lang.Integer
    return new Object[][] {{new Integer[] {detectPrimitiveInt()}}};
  }

  Integer[][] array_primitiveByteInDimensions() {
    // BUG: Diagnostic contains: int
    return new Integer[detectPrimitiveByte()][];
  }

  String[][] array_wrappedIntegerInDimensions() {
    // BUG: Diagnostic contains: int
    return new String[detectWrappedInteger()][];
  }

  String[][] array_initializeWithArray() {
    // BUG: Diagnostic contains: java.lang.String[]
    String[][] s = {detectStringArray()};
    return s;
  }

  String methodChain() {
    // BUG: Diagnostic contains: java.lang.Boolean
    Boolean b = TargetTypeTest.detectWrappedBoolean();

    // BUG: Diagnostic contains: java.lang.Object
    return detectWrappedInteger().toString();
  }

  void compoundAssignment_numeric(Integer i, int j, Long k) {
    // BUG: Diagnostic contains: int
    i /= detectWrappedInteger();

    // BUG: Diagnostic contains: int
    i *= (detectWrappedInteger());

    // BUG: Diagnostic contains: int
    j -= detectWrappedInteger();

    // BUG: Diagnostic contains: long
    k /= detectWrappedInteger();
  }

  void compoundAssignment_string(String s, Object o) {
    // BUG: Diagnostic contains: java.lang.String
    s += detectWrappedInteger();

    // BUG: Diagnostic contains: java.lang.String
    s += detectPrimitiveInt();

    // BUG: Diagnostic contains: java.lang.String
    o += detectString();
  }

  void compoundAssignment_boolean(boolean b) {
    // BUG: Diagnostic contains: boolean
    b &= detectWrappedBoolean();

    // BUG: Diagnostic contains: boolean
    b |= detectPrimitiveBoolean();
  }

  void concatenation(String s, Object a) {
    // BUG: Diagnostic contains: java.lang.String
    a = s + detectWrappedInteger();

    // BUG: Diagnostic contains: java.lang.String
    a = s + detectPrimitiveByte();

    // BUG: Diagnostic contains: java.lang.String
    a = s + detectVoid();

    // BUG: Diagnostic contains: java.lang.String
    a = s + detectStringArray();
  }

  abstract <T> T id(T t);

  abstract <T> List<T> list(List<T> t);

  void generic() {
    // BUG: Diagnostic contains: java.lang.String
    String s = id(detectString());
    // BUG: Diagnostic contains: java.lang.Integer
    int i = id(detectPrimitiveInt());
    // BUG: Diagnostic contains: java.util.List<java.lang.String>
    List<String> y = id(detectStringList());
    // BUG: Diagnostic contains: java.lang.Integer
    Integer z = id(detectPrimitiveInt());
  }

  void typeCast() {
    // BUG: Diagnostic contains: int
    long a = (int) detectPrimitiveByte();

    // BUG: Diagnostic contains: java.lang.Object
    String s = (String) (Object) detectString();
  }

  void enhancedForLoop() {
    // BUG: Diagnostic contains: java.lang.String[]
    for (String s : detectStringArray()) {}

    // BUG: Diagnostic contains: java.lang.Object[]
    for (Object s : detectStringArray()) {}

    // BUG: Diagnostic contains: int[]
    for (int i : detectPrimitiveIntArray()) {}

    // BUG: Diagnostic contains: long[]
    for (long i : detectPrimitiveIntArray()) {}

    // BUG: Diagnostic contains: java.lang.Integer[]
    for (Integer i : detectPrimitiveIntArray()) {}

    // BUG: Diagnostic contains: java.lang.Object[]
    for (Object i : detectPrimitiveIntArray()) {}

    // BUG: Diagnostic contains: java.lang.Iterable<? extends java.lang.String>
    for (String s : detectStringList()) {}

    // BUG: Diagnostic contains: java.lang.Iterable<? extends java.lang.Object>
    for (Object s : detectStringList()) {}

    // BUG: Diagnostic contains: java.lang.Iterable<? extends java.lang.Integer>
    for (int s : detectIntegerList()) {}
  }

  void testAssert() {
    // BUG: Diagnostic contains: boolean
    assert detectPrimitiveBoolean();

    // BUG: Diagnostic contains: boolean
    assert detectWrappedBoolean();

    // BUG: Diagnostic contains: boolean
    assert detectPrimitiveBoolean() : "";

    // BUG: Diagnostic contains: java.lang.String
    assert false : detectString();

    // BUG: Diagnostic contains: java.lang.String
    assert false : detectPrimitiveInt();
  }

  void testSwitch(int anInt, String aString) {
    final int detectInt = 0;
    switch (anInt) {
      // BUG: Diagnostic contains: int
      case detectInt:
        break;
    }

    final byte detectByte = 0;
    switch (anInt) {
      // BUG: Diagnostic contains: int
      case detectByte:
        break;
    }

    final String detectString = "";
    switch (aString) {
      // BUG: Diagnostic contains: java.lang.String
      case detectString:
        break;
    }
  }

  void instanceOf() {
    // BUG: Diagnostic contains: java.lang.Object
    if (detectString() instanceof String) {}

    // BUG: Diagnostic contains: java.lang.Object
    if (detectStringList() instanceof Serializable) {}
  }

  void testSynchronized() {
    // BUG: Diagnostic contains: java.lang.Object
    synchronized (detectString()) {
    }
  }

  void testThrow() throws IOException {
    if (System.currentTimeMillis() > 0) {
      // BUG: Diagnostic contains: java.lang.IllegalArgumentException
      throw detectIllegalArgumentException();
    }
    if (System.currentTimeMillis() > 0) {
      // BUG: Diagnostic contains: java.io.IOException
      throw detectIoException();
    }
  }

  void newClass() {
    // BUG: Diagnostic contains: java.lang.String
    new IllegalArgumentException(detectString());

    // BUG: Diagnostic contains: HasInner
    detectHasInner().new Inner();

    // BUG: Diagnostic contains: HasInner
    detectDifferentName().new Inner();
  }

  // Helper methods that we can search for.
  static byte detectPrimitiveByte() {
    return 0;
  }

  static boolean detectPrimitiveBoolean() {
    return true;
  }

  static int detectPrimitiveInt() {
    return 0;
  }

  static int[] detectPrimitiveIntArray() {
    return new int[0];
  }

  static Boolean detectWrappedBoolean() {
    return true;
  }

  static Integer detectWrappedInteger() {
    return 0;
  }

  static String detectString() {
    return "";
  }

  static String[] detectStringArray() {
    return new String[] {};
  }

  static ThisEnum detectThisEnum() {
    return null;
  }

  static Void detectVoid() {
    return null;
  }

  static List<String> detectStringList() {
    return null;
  }

  static List<Integer> detectIntegerList() {
    return null;
  }

  static IllegalArgumentException detectIllegalArgumentException() {
    return null;
  }

  static IOException detectIoException() {
    return null;
  }

  static HasInner detectHasInner() {
    return null;
  }

  static DifferentName detectDifferentName() {
    return null;
  }

  enum ThisEnum {}

  class HasInner {
    class Inner {}
  }

  // Not called the obvious "ExtendsHasInner" in order to avoid erroneously matching the "HasInner"
  // part of it.
  class DifferentName extends HasInner {}

  interface A<T> {
    void foo();
  }

  class B<T> implements A<T> {
    @Override
    public void foo() {}
  }

  class C<T> extends B<T> {}

  class D implements A<Long> {
    @Override
    public void foo() {}
  }

  void overrides() {
    C detectMe = new C();
    // BUG: Diagnostic contains: TargetTypeTest.A$
    detectMe.foo();

    B detectMeAlso = new B();
    // BUG: Diagnostic contains: TargetTypeTest.A$
    detectMeAlso.foo();

    // NOTE(ghm): I think we ought to be resolving the type arguments here, but if no callers
    // care...

    B<Integer> detectMeAlso2 = new B<Integer>();
    // BUG: Diagnostic contains: TargetTypeTest.A<T>$
    detectMeAlso2.foo();

    D detectMeAlso3 = new D();
    // BUG: Diagnostic contains: TargetTypeTest.A<T>$
    detectMeAlso3.foo();
  }

  interface AA {
    List<Integer> bar();
  }

  final class BB implements AA {
    @Override
    public ArrayList<Integer> bar() {
      return null;
    }
  }

  void covariantReturnTypes() {
    BB detectMe = new BB();

    // BUG: Diagnostic contains: AA
    detectMe.bar().get(0);

    // BUG: Diagnostic contains: BB
    detectMe.bar().ensureCapacity(1);

    // NOTE(ghm): Should be AA.
    // BUG: Diagnostic contains: BB
    supplies(detectMe.bar()::size);

    // BUG: Diagnostic contains: BB
    consumes(detectMe.bar()::ensureCapacity);

    // NOTE(ghm): Should be AA.
    // BUG: Diagnostic contains: List<?>
    suppliesList(detectMe::bar);

    // NOTE(ghm): Should be BB.
    // BUG: Diagnostic contains: List<?>
    suppliesArrayList(detectMe::bar);
  }

  void supplies(Supplier<Integer> x) {}

  void consumes(Consumer<Integer> x) {}

  void suppliesList(Supplier<List<?>> x) {}

  void suppliesArrayList(Supplier<ArrayList<?>> x) {}
}
