/*
 * Copyright 2017 The Error Prone Authors.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.BugCheckerRefactoringTestHelper.FixChoosers;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link LambdaFunctionalInterface}Test */
@RunWith(JUnit4.class)
public class LambdaFunctionalInterfaceTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(LambdaFunctionalInterface.class, getClass());

  private final BugCheckerRefactoringTestHelper refactoringHelper =
      BugCheckerRefactoringTestHelper.newInstance(LambdaFunctionalInterface.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "LambdaFunctionalInterfacePositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class LambdaFunctionalInterfacePositiveCases {

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private double fooIntToDoubleFunctionPr(int x, Function<Integer, Double> fn) {
    return fn.apply(x);
  }

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private long fooIntToLongFunction(int x, Function<Integer, Long> fn) {
    return fn.apply(x);
  }

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private long fooIntToIntFunction(int x, Function<Integer, Long> fn) {
    return fn.apply(x);
  }

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private double fooDoubleToDoubleFunction(double x, Function<Double, Double> fn) {
    return fn.apply(x);
  }

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private int fooDoubleToIntFunction(double x, Function<Double, Integer> fn) {
    return fn.apply(x);
  }

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private void fooInterface(String str, Function<Integer, Double> func) {}

  // BUG: Diagnostic contains: [LambdaFunctionalInterface]
  private double fooDouble(double x, Function<Double, Integer> fn) {
    return fn.apply(x);
  }

  public static class WithCallSiteExplicitFunction {

    private static double generateDataSeries(Function<Double, Double> curveFunction) {
      final double scale = 100;
      final double modX = 2.0;
      return modX / curveFunction.apply(scale);
    }

    // call site
    private static double generateSpendCurveForMetric(double curved) {
      // explicit Function variable creation
      Function<Double, Double> curveFunction = x -> Math.pow(x, 1 / curved) * 100;
      return generateDataSeries(curveFunction);
    }

    // call site: lambda Function
    public Double getMu() {
      return generateDataSeries(mu -> 2.3);
    }
  }

  public static class WithCallSiteAnonymousFunction {

    private static double findOptimalMu(Function<Double, Long> costFunc, double mid) {
      return costFunc.apply(mid);
    }

    // call site: anonymous Function
    public Double getMu() {
      return findOptimalMu(
          new Function<Double, Long>() {
            @Override
            public Long apply(Double mu) {
              return 0L;
            }
          },
          3.0);
    }
  }

  public static class WithCallSiteLambdaFunction {

    // BUG: Diagnostic contains: [LambdaFunctionalInterface]
    private static double findOptimalMuLambda(Function<Double, Long> costFunc, double mid) {
      return costFunc.apply(mid);
    }

    // call site: lambda
    public Double getMu() {
      return findOptimalMuLambda(mu -> 0L, 3.0);
    }

    // call site: lambda
    public Double getTu() {
      return findOptimalMuLambda(mu -> 2L, 4.0);
    }
  }

  public static class TwoLambdaFunctions {

    // BUG: Diagnostic contains: [LambdaFunctionalInterface]
    private static double find(
        Function<Double, Long> firstFunc, Function<Integer, Long> secondFun, double mid) {
      firstFunc.apply(mid + 2);

      return firstFunc.apply(mid);
    }

    // call site: lambda
    public Double getMu() {
      return find(mu -> 0L, nu -> 1L, 3.0);
    }

    // call site: lambda
    public Double getTu() {
      return find(mu -> 2L, nu -> 3L, 4.0);
    }
  }

  public static class NumbertoT {

    // BUG: Diagnostic contains: [LambdaFunctionalInterface]
    private static <T extends Number> List<T> numToTFunction(Function<Double, T> converter) {
      List<T> namedNumberIntervals = new ArrayList<>();
      T min = converter.apply(2.9);
      T max = converter.apply(5.6);
      namedNumberIntervals.add(min);
      namedNumberIntervals.add(max);
      return namedNumberIntervals;
    }

    // call site: lambda
    public List<Integer> getIntList() {
      List<Integer> result = numToTFunction(num -> 2 + 3);

      return result;
    }

    // call site: lambda
    public List<Double> getDoubleList() {
      List<Double> result = numToTFunction(num -> 2.3);

      return result;
    }
  }

  public static class TtoNumber {

    // BUG: Diagnostic contains: [LambdaFunctionalInterface]
    private <T> int sumAll(Function<T, Integer> sizeConv) {
      return sizeConv.apply((T) Integer.valueOf(3));
    }

    public int getSumAll() {
      return sumAll(o -> 2);
    }
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "LambdaFunctionalInterfaceNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.function.Function;
import java.util.function.IntToDoubleFunction;

public class LambdaFunctionalInterfaceNegativeCases {

  public double fooIntToDoubleFunction(int x, Function<Integer, Double> fn) {
    return fn.apply(x).doubleValue();
  }

  public void fooIntToDoubleUtil(int y, IntToDoubleFunction fn) {
    fn.applyAsDouble(y);
  }

  public long fooIntToLongFunction(int x, Function<Integer, Long> fn) {
    return fn.apply(x);
  }

  public long fooIntToIntFunction(int x, Function<Integer, Long> fn) {
    return fn.apply(x);
  }

  public double fooDoubleToDoubleFunction(double x, Function<Double, Double> fn) {
    return fn.apply(x);
  }

  public int fooDoubleToIntFunction(double x, Function<Double, Integer> fn) {
    return fn.apply(x);
  }

  public String add(String string, Function<String, String> func) {
    return func.apply(string);
  }

  public void fooInterface(String str, Function<Integer, Double> func) {}

  public double fooDouble(double x, Function<Double, Integer> fn) {
    return fn.apply(x);
  }

  public static class WithCallSiteExplicitFunction {

    public static double generateDataSeries(Function<Double, Double> curveFunction) {
      final double scale = 100;
      final double modX = 2.0;
      return modX / curveFunction.apply(scale);
    }

    // call site
    private static double generateSpendCurveForMetric(double curved) {
      // explicit Function variable creation
      Function<Double, Double> curveFunction = x -> Math.pow(x, 1 / curved) * 100;
      return generateDataSeries(curveFunction);
    }
  }

  public static class WithCallSiteAnonymousFunction {

    public static double findOptimalMu(Function<Double, Long> costFunc, double mid) {
      return costFunc.apply(mid);
    }

    // call site: anonymous Function
    public Double getMu() {
      return findOptimalMu(
          new Function<Double, Long>() {
            @Override
            public Long apply(Double mu) {
              return 0L;
            }
          },
          3.0);
    }
  }

  public static class WithCallSiteLambdaFunction {

    public static double findOptimalMuLambda(Function<Double, Long> costFunc, double mid) {
      return costFunc.apply(mid);
    }

    // call site: anonymous Function
    public Double getMu() {
      return findOptimalMuLambda(mu -> 0L, 3.0);
    }
  }
}\
""")
        .doTest();
  }

  @Test
  public void refactoringTwo() {
    refactoringHelper
        .addInputLines(
            "in/TwoLambdaFunctions.java",
"""
import java.util.function.Function;

public class TwoLambdaFunctions {
  private static double find(
      Function<Double, Long> firstSpecial, Function<Integer, Long> secondSpecial, double mid) {
    secondSpecial.apply(2);
    return firstSpecial.apply(mid);
  }

  public Double getMu() {
    return find(mu -> 0L, nu -> 1L, 3.0);
  }

  public Double getTu() {
    return find(mu -> 2L, nu -> 3L, 4.0);
  }
}
""")
        .addOutputLines(
            "out/TwoLambdaFunctions.java",
            """
            import java.util.function.DoubleToLongFunction;
            import java.util.function.Function;
            import java.util.function.IntToLongFunction;

            public class TwoLambdaFunctions {
              private static double find(
                  DoubleToLongFunction firstSpecial, IntToLongFunction secondSpecial, double mid) {
                secondSpecial.applyAsLong(2);
                return firstSpecial.applyAsLong(mid);
              }

              public Double getMu() {
                return find(mu -> 0L, nu -> 1L, 3.0);
              }

              public Double getTu() {
                return find(mu -> 2L, nu -> 3L, 4.0);
              }
            }
            """)
        .setFixChooser(FixChoosers.FIRST)
        .doTest();
  }

  @Test
  public void refactoringInteger() {
    refactoringHelper
        .addInputLines(
            "in/TwoLambdaFunctions.java",
"""
import java.util.function.Function;

public class TwoLambdaFunctions {
  private static int find(
      Function<Integer, Integer> firstSpecial, Function<Integer, Double> secondSpecial, int mid) {
    secondSpecial.apply(2);
    return firstSpecial.apply(mid);
  }

  public Integer getMu() {
    return find(mu -> 0, nu -> 1.1, 3);
  }

  public Integer getTu() {
    return find(mu -> 2, nu -> 3.2, 4);
  }
}
""")
        .addOutputLines(
            "out/TwoLambdaFunctions.java",
            """
            import java.util.function.Function;
            import java.util.function.IntFunction;
            import java.util.function.IntToDoubleFunction;

            public class TwoLambdaFunctions {
              private static int find(
                  IntFunction<Integer> firstSpecial, IntToDoubleFunction secondSpecial, int mid) {
                secondSpecial.applyAsDouble(2);
                return firstSpecial.apply(mid);
              }

              public Integer getMu() {
                return find(mu -> 0, nu -> 1.1, 3);
              }

              public Integer getTu() {
                return find(mu -> 2, nu -> 3.2, 4);
              }
            }
            """)
        .setFixChooser(FixChoosers.FIRST)
        .doTest();
  }

  @Test
  public void refactoringPrimitiveToGeneric() {
    refactoringHelper
        .addInputLines(
            "in/NumbertoT.java",
"""
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

public class NumbertoT {
  private static <T extends Number> List<T> numToTFunction(Function<Double, T> converter) {
    List<T> namedNumberIntervals = new ArrayList<>();
    T min = converter.apply(2.9);
    T max = converter.apply(5.6);
    namedNumberIntervals.add(min);
    namedNumberIntervals.add(max);
    return namedNumberIntervals;
  }

  public List<Integer> getIntList() {
    List<Integer> result = numToTFunction(num -> 2);
    return result;
  }

  public List<Double> getDoubleList() {
    List<Double> result = numToTFunction(num -> 3.2);
    return result;
  }
}
""")
        .addOutputLines(
            "out/NumbertoT.java",
"""
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Function;

public class NumbertoT {
  private static <T extends Number> List<T> numToTFunction(DoubleFunction<T> converter) {
    List<T> namedNumberIntervals = new ArrayList<>();
    T min = converter.apply(2.9);
    T max = converter.apply(5.6);
    namedNumberIntervals.add(min);
    namedNumberIntervals.add(max);
    return namedNumberIntervals;
  }

  public List<Integer> getIntList() {
    List<Integer> result = numToTFunction(num -> 2);
    return result;
  }

  public List<Double> getDoubleList() {
    List<Double> result = numToTFunction(num -> 3.2);
    return result;
  }
}
""")
        .setFixChooser(FixChoosers.FIRST)
        .doTest();
  }

  @Test
  public void refactoringGenericToPrimitive() {
    refactoringHelper
        .addInputLines(
            "in/NumbertoT.java",
            """
            import java.util.function.Function;

            public class NumbertoT {
              private <T> int sumAll(Function<T, Integer> sizeConv) {
                return sizeConv.apply((T) Integer.valueOf(3));
              }

              public int getSumAll() {
                return sumAll(o -> 2);
              }
            }
            """)
        .addOutputLines(
            "out/NumbertoT.java",
            """
            import java.util.function.Function;
            import java.util.function.ToIntFunction;

            public class NumbertoT {
              private <T> int sumAll(ToIntFunction<T> sizeConv) {
                return sizeConv.applyAsInt((T) Integer.valueOf(3));
              }

              public int getSumAll() {
                return sumAll(o -> 2);
              }
            }
            """)
        .setFixChooser(FixChoosers.FIRST)
        .doTest();
  }

  @Test
  public void onEnum() {
    compilationHelper
        .addSourceLines(
            "E.java",
            """
            import java.util.function.Function;

            public enum E {
              VALUE(String::length);

              // BUG: Diagnostic contains:
              E(Function<String, Integer> func) {}
            }
            """)
        .doTest();
  }
}
