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

package com.google.errorprone.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.errorprone.util.ASTHelpers.isConsideredFinal;
import static com.google.errorprone.util.ASTHelpers.isStatic;
import static com.google.errorprone.util.Reachability.canCompleteNormally;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Kinds.KindSelector;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.Name;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.StreamSupport;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/** A helper class to find all identifiers in scope at a given program point. */
public final class FindIdentifiers {

  /** Finds a variable declaration with the given name that is in scope at the current location. */
  public static Symbol findIdent(String name, VisitorState state) {
    return findIdent(name, state, KindSelector.VAR);
  }

  /** Finds a declaration with the given name and type that is in scope at the current location. */
  public static @Nullable Symbol findIdent(String name, VisitorState state, KindSelector kind) {
    ClassType enclosingClass = ASTHelpers.getType(getEnclosingClass(state.getPath()));
    Env<AttrContext> env;
    if (enclosingClass == null || enclosingClass.tsym == null) {
      env =
          Enter.instance(state.context)
              .getTopLevelEnv((JCCompilationUnit) state.getPath().getCompilationUnit());
    } else {
      env = Enter.instance(state.context).getClassEnv(enclosingClass.tsym);
      MethodTree enclosingMethod = state.findEnclosing(MethodTree.class);
      if (enclosingMethod != null) {
        env = MemberEnter.instance(state.context).getMethodEnv((JCMethodDecl) enclosingMethod, env);
      }
    }
    try {
      Symbol result = findIdent(name, state, kind, env);
      return result.exists() ? result : null;
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  // Signature was changed in Java 13: https://bugs.openjdk.java.net/browse/JDK-8223305
  private static Symbol findIdent(
      String name, VisitorState state, KindSelector kind, Env<AttrContext> env)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    if (Runtime.version().feature() >= 13) {
      Method method =
          Resolve.class.getDeclaredMethod(
              "findIdent", DiagnosticPosition.class, Env.class, Name.class, KindSelector.class);
      method.setAccessible(true);
      return (Symbol)
          method.invoke(Resolve.instance(state.context), null, env, state.getName(name), kind);
    }
    Method method =
        Resolve.class.getDeclaredMethod("findIdent", Env.class, Name.class, KindSelector.class);
    method.setAccessible(true);
    return (Symbol) method.invoke(Resolve.instance(state.context), env, state.getName(name), kind);
  }

  private static @Nullable ClassTree getEnclosingClass(TreePath treePath) {
    if (treePath.getLeaf() instanceof ClassTree) {
      return (ClassTree) treePath.getLeaf();
    }

    while (true) {
      TreePath parent = treePath.getParentPath();
      if (parent == null) {
        return null;
      }
      Tree leaf = parent.getLeaf();
      if (leaf instanceof ClassTree classTree
          && classTree.getMembers().contains(treePath.getLeaf())) {
        return classTree;
      }
      treePath = parent;
    }
  }

  /**
   * Finds the set of all bare variable identifiers in scope at the current location. Identifiers
   * are ordered by ascending distance/scope count from the current location to match shadowing
   * rules. That is, if two variables with the same simple names appear in the set, the one that
   * appears first in iteration order is the one you get if you use the bare name in the source
   * code.
   *
   * <p>We do not report variables that would require a qualified access. We also do not handle
   * wildcard imports.
   */
  public static ImmutableSet<VarSymbol> findAllIdents(VisitorState state) {
    ImmutableSet.Builder<VarSymbol> result = new ImmutableSet.Builder<>();

    // If we're in a binary tree, scan up separately to find anything to the left that implies us.
    Tree prev = state.getPath().getLeaf();
    loop:
    for (Tree curr : state.getPath().getParentPath()) {
      switch (curr.getKind()) {
        case CONDITIONAL_AND -> {
          BinaryTree binaryTree = (BinaryTree) curr;
          if (prev == binaryTree.getRightOperand()) {
            findBindingVariables(binaryTree.getLeftOperand(), result, /* startNegated= */ false);
          }
        }
        case CONDITIONAL_OR -> {
          BinaryTree binaryTree = (BinaryTree) curr;
          if (prev == binaryTree.getRightOperand()) {
            findBindingVariables(binaryTree.getLeftOperand(), result, /* startNegated= */ true);
          }
        }
        default -> {
          if (!(curr instanceof ExpressionTree)) {
            break loop;
          }
        }
      }

      prev = curr;
    }

    prev = state.getPath().getLeaf();
    for (Tree curr : state.getPath().getParentPath()) {
      switch (curr.getKind()) {
        case BLOCK -> {
          for (StatementTree stmt : ((BlockTree) curr).getStatements()) {
            if (stmt.equals(prev)) {
              break;
            }
            addIfVariable(stmt, result);
            if (stmt instanceof IfTree ifTree && !canCompleteNormally(ifTree.getThenStatement())) {
              findBindingVariables(ifTree.getCondition(), result, /* startNegated= */ true);
            }
          }
        }
        case LAMBDA_EXPRESSION -> {
          for (VariableTree param : ((LambdaExpressionTree) curr).getParameters()) {
            result.add(ASTHelpers.getSymbol(param));
          }
        }
        case METHOD -> {
          for (VariableTree param : ((MethodTree) curr).getParameters()) {
            result.add(ASTHelpers.getSymbol(param));
          }
        }
        case CATCH -> result.add(ASTHelpers.getSymbol(((CatchTree) curr).getParameter()));
        case CLASS, INTERFACE, ENUM, ANNOTATION_TYPE -> {
          // Collect fields declared in this class.  If we are in a field initializer, only
          // include fields declared before this one. JLS 8.3.3 allows forward references if the
          // field is referred to by qualified name, but we don't support that.
          for (Tree member : ((ClassTree) curr).getMembers()) {
            if (member.equals(prev)) {
              break;
            }
            addIfVariable(member, result);
          }

          // Collect inherited fields.
          Type classType = ASTHelpers.getType(curr);
          List<Type> classTypeClosure = state.getTypes().closure(classType);
          List<Type> superTypes =
              classTypeClosure.size() <= 1
                  ? Collections.emptyList()
                  : classTypeClosure.subList(1, classTypeClosure.size());
          for (Type type : superTypes) {
            Scope scope = type.tsym.members();
            ImmutableList.Builder<VarSymbol> varsList = ImmutableList.builder();
            for (Symbol var : scope.getSymbols(VarSymbol.class::isInstance)) {
              varsList.add((VarSymbol) var);
            }
            result.addAll(varsList.build().reverse());
          }
        }
        case FOR_LOOP -> addAllIfVariable(((ForLoopTree) curr).getInitializer(), result);
        case ENHANCED_FOR_LOOP ->
            result.add(ASTHelpers.getSymbol(((EnhancedForLoopTree) curr).getVariable()));
        case TRY -> {
          TryTree tryTree = (TryTree) curr;
          boolean inResources = false;
          for (Tree resource : tryTree.getResources()) {
            if (resource.equals(prev)) {
              inResources = true;
              break;
            }
          }
          if (inResources) {
            // Case 1: we're in one of the resource declarations
            for (Tree resource : tryTree.getResources()) {
              if (resource.equals(prev)) {
                break;
              }
              addIfVariable(resource, result);
            }
          } else if (tryTree.getBlock().equals(prev)) {
            // Case 2: We're in the block (not a catch or finally)
            addAllIfVariable(tryTree.getResources(), result);
          }
        }
        case IF -> {
          var ifTree = (IfTree) curr;
          if (prev == ifTree.getThenStatement()) {
            findBindingVariables(ifTree.getCondition(), result, /* startNegated= */ false);
          }
          if (prev == ifTree.getElseStatement()) {
            findBindingVariables(ifTree.getCondition(), result, /* startNegated= */ true);
          }
        }
        case CONDITIONAL_EXPRESSION -> {
          ConditionalExpressionTree conditionalExpressionTree = (ConditionalExpressionTree) curr;
          if (prev == conditionalExpressionTree.getTrueExpression()) {
            findBindingVariables(
                conditionalExpressionTree.getCondition(), result, /* startNegated= */ false);
          }
          if (prev == conditionalExpressionTree.getFalseExpression()) {
            findBindingVariables(
                conditionalExpressionTree.getCondition(), result, /* startNegated= */ true);
          }
        }
        case COMPILATION_UNIT -> {
          for (ImportTree importTree : ((CompilationUnitTree) curr).getImports()) {
            if (importTree.isStatic()
                && importTree.getQualifiedIdentifier().getKind() == Kind.MEMBER_SELECT) {
              MemberSelectTree memberSelectTree =
                  (MemberSelectTree) importTree.getQualifiedIdentifier();
              Scope scope =
                  state
                      .getTypes()
                      .membersClosure(
                          ASTHelpers.getType(memberSelectTree.getExpression()),
                          /* skipInterface= */ false);
              for (Symbol var :
                  scope.getSymbols(
                      sym ->
                          sym instanceof VarSymbol
                              && sym.getSimpleName().equals(memberSelectTree.getIdentifier()))) {
                result.add((VarSymbol) var);
              }
            }
          }
        }
        default -> {
          // other node types don't introduce variables
        }
      }

      prev = curr;
    }

    return result.build().stream()
        .filter(variable -> isVisible(variable, state.getPath()))
        .collect(toImmutableSet());
  }

  private static void findBindingVariables(
      Tree tree, ImmutableSet.Builder<VarSymbol> result, boolean startNegated) {
    new SimpleTreeVisitor<Void, Void>() {
      boolean negated = startNegated;

      @Override
      public Void visitInstanceOf(InstanceOfTree node, Void unused) {
        if (!negated && node.getPattern() instanceof BindingPatternTree bpt) {
          addIfVariable(bpt.getVariable(), result);
        }
        return null;
      }

      @Override
      public Void visitParenthesized(ParenthesizedTree node, Void unused) {
        return visit(node.getExpression(), null);
      }

      @Override
      public Void visitUnary(UnaryTree node, Void unused) {
        if (node.getKind().equals(Kind.LOGICAL_COMPLEMENT)) {
          negated = !negated;
          return visit(node.getExpression(), null);
        }
        return null;
      }

      @Override
      public Void visitBinary(BinaryTree node, Void unused) {
        if (node.getKind().equals(Kind.CONDITIONAL_AND) && !negated) {
          visit(node.getLeftOperand(), null);
          visit(node.getRightOperand(), null);
        }
        if (node.getKind().equals(Kind.CONDITIONAL_OR) && negated) {
          visit(node.getLeftOperand(), null);
          visit(node.getRightOperand(), null);
        }
        return null;
      }
    }.visit(tree, null);
  }

  /**
   * Finds all variable declarations which are unused at this point in the AST (i.e. they might be
   * used further on).
   */
  public static ImmutableSet<VarSymbol> findUnusedIdentifiers(VisitorState state) {
    ImmutableSet.Builder<VarSymbol> definedVariables = ImmutableSet.builder();
    ImmutableSet.Builder<Symbol> usedSymbols = ImmutableSet.builder();
    Tree prev = state.getPath().getLeaf();
    for (Tree curr : state.getPath().getParentPath()) {
      createFindIdentifiersScanner(usedSymbols, prev).scan(curr, null);
      switch (curr.getKind()) {
        case BLOCK -> {
          // If we see a block then walk over each statement to see if it defines a variable
          for (StatementTree statement : ((BlockTree) curr).getStatements()) {
            if (statement.equals(prev)) {
              // break if we see the tree we have just processed so that we only consider things
              // declared/used before us in the tree
              break;
            }
            addIfVariable(statement, definedVariables);
          }
        }
        case FOR_LOOP -> {
          ForLoopTree forLoop = (ForLoopTree) curr;
          forLoop.getInitializer().stream().forEach(t -> addIfVariable(t, definedVariables));
        }
        case ENHANCED_FOR_LOOP -> {
          EnhancedForLoopTree enhancedFor = (EnhancedForLoopTree) curr;
          addIfVariable(enhancedFor.getVariable(), definedVariables);
        }
        default -> {}
      }
      prev = curr;
    }
    return ImmutableSet.copyOf(Sets.difference(definedVariables.build(), usedSymbols.build()));
  }

  /** Find the set of all identifiers referenced within this Tree */
  public static ImmutableSet<Symbol> findReferencedIdentifiers(Tree tree) {
    ImmutableSet.Builder<Symbol> builder = ImmutableSet.builder();
    createFindIdentifiersScanner(builder, null).scan(tree, null);
    return builder.build();
  }

  /** Finds all the visible fields declared or inherited in the target class */
  public static ImmutableList<VarSymbol> findAllFields(Type classType, VisitorState state) {
    return state.getTypes().closure(classType).stream()
        .flatMap(
            type -> {
              TypeSymbol tsym = type.tsym;
              if (tsym == null) {
                return ImmutableList.<VarSymbol>of().stream();
              }
              WriteableScope scope = tsym.members();
              if (scope == null) {
                return ImmutableList.<VarSymbol>of().stream();
              }
              return ImmutableList.copyOf(scope.getSymbols(VarSymbol.class::isInstance))
                  .reverse()
                  .stream()
                  .map(v -> (VarSymbol) v)
                  .filter(v -> isVisible(v, state.getPath()));
            })
        .collect(toImmutableList());
  }

  /**
   * Finds all identifiers in a tree. Takes an optional stop point as its argument: the depth-first
   * walk will stop if this node is encountered.
   */
  private static TreeScanner<Void, Void> createFindIdentifiersScanner(
      ImmutableSet.Builder<Symbol> builder, @Nullable Tree stoppingPoint) {
    return new TreeScanner<Void, Void>() {
      @Override
      public Void scan(Tree tree, Void unused) {
        return Objects.equals(stoppingPoint, tree) ? null : super.scan(tree, null);
      }

      @Override
      public Void scan(Iterable<? extends Tree> iterable, Void unused) {
        if (stoppingPoint != null && iterable != null) {
          ImmutableList.Builder<Tree> builder = ImmutableList.builder();
          for (Tree t : iterable) {
            if (stoppingPoint.equals(t)) {
              break;
            }
            builder.add(t);
          }
          iterable = builder.build();
        }
        return super.scan(iterable, null);
      }

      @Override
      public Void visitIdentifier(IdentifierTree identifierTree, Void unused) {
        Symbol symbol = ASTHelpers.getSymbol(identifierTree);
        if (symbol != null) {
          builder.add(symbol);
        }
        return null;
      }
    };
  }

  private static boolean isVisible(VarSymbol var, TreePath path) {
    switch (var.getKind()) {
      case ENUM_CONSTANT, FIELD -> {
        ImmutableList<ClassSymbol> enclosingClasses =
            StreamSupport.stream(path.spliterator(), false)
                .filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast)
                .map(ASTHelpers::getSymbol)
                .collect(toImmutableList());

        if (!var.isStatic()) {
          // Instance fields are not visible if we are in a static context...
          if (inStaticContext(path)) {
            return false;
          }

          // ... or if we're in a static nested class and the instance fields are declared outside
          // the enclosing static nested class (JLS 8.5.1).
          if (lowerThan(
              path,
              (curr, unused) -> {
                Symbol sym = ASTHelpers.getSymbol(curr);
                return sym != null && isStatic(sym);
              },
              (curr, unused) ->
                  curr instanceof ClassTree && ASTHelpers.getSymbol(curr).equals(var.owner))) {
            return false;
          }
        }

        // If we're lexically enclosed by the same class that defined var, we can access private
        // fields (JLS 6.6.1).
        if (enclosingClasses.contains(ASTHelpers.enclosingClass(var))) {
          return true;
        }

        PackageSymbol enclosingPackage = ((JCCompilationUnit) path.getCompilationUnit()).packge;
        Set<Modifier> modifiers = var.getModifiers();
        // If we're in the same package where var was defined, we can access package-private fields
        // (JLS 6.6.1).
        if (Objects.equals(enclosingPackage, ASTHelpers.enclosingPackage(var))) {
          return !modifiers.contains(Modifier.PRIVATE);
        }

        // Otherwise we can only access public and protected fields (JLS 6.6.1, plus the fact
        // that the only enum constants and fields usable by simple name are either defined
        // in the enclosing class or a superclass).
        return modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.PROTECTED);
      }
      case PARAMETER, LOCAL_VARIABLE, BINDING_VARIABLE -> {
        // If we are in an anonymous inner class, lambda, or local class, any local variable or
        // method parameter we access that is defined outside the anonymous class/lambda must be
        // final or effectively final (JLS 8.1.3).
        if (lowerThan(
            path,
            (curr, parent) ->
                curr.getKind() == Kind.LAMBDA_EXPRESSION
                    || (curr.getKind() == Kind.NEW_CLASS
                        && ((NewClassTree) curr).getClassBody() != null)
                    || (curr.getKind() == Kind.CLASS && parent.getKind() == Kind.BLOCK),
            (curr, unused) -> Objects.equals(var.owner, ASTHelpers.getSymbol(curr)))) {
          if (!isConsideredFinal(var)) {
            return false;
          }
        }
        return true;
      }
      case EXCEPTION_PARAMETER, RESOURCE_VARIABLE -> {
        return true;
      }
      default -> throw new IllegalArgumentException("Unexpected variable type: " + var.getKind());
    }
  }

  /** Returns true iff the leaf node of the {@code path} occurs in a JLS 8.3.1 static context. */
  private static boolean inStaticContext(TreePath path) {
    ClassSymbol enclosingClass =
        ASTHelpers.getSymbol(ASTHelpers.findEnclosingNode(path, ClassTree.class));
    ClassSymbol directSuperClass = (ClassSymbol) enclosingClass.getSuperclass().tsym;

    Tree prev = path.getLeaf();
    path = path.getParentPath();

    for (Tree tree : path) {
      switch (tree.getKind()) {
        case METHOD -> {
          return isStatic(ASTHelpers.getSymbol(tree));
        }
        case BLOCK -> {
          // static initializer
          if (((BlockTree) tree).isStatic()) {
            return true;
          }
        }
        case VARIABLE -> {
          // variable initializer of static variable
          VariableTree variableTree = (VariableTree) tree;
          VarSymbol variableSym = ASTHelpers.getSymbol(variableTree);
          if (variableSym.getKind() == ElementKind.FIELD) {
            return Objects.equals(variableTree.getInitializer(), prev) && variableSym.isStatic();
          }
        }
        case METHOD_INVOCATION -> {
          // JLS 8.8.7.1 explicit constructor invocation
          MethodSymbol methodSym = ASTHelpers.getSymbol((MethodInvocationTree) tree);
          if (methodSym.isConstructor()
              && (Objects.equals(methodSym.owner, enclosingClass)
                  || Objects.equals(methodSym.owner, directSuperClass))) {
            return true;
          }
        }
        default -> {}
      }
      prev = tree;
    }
    return false;
  }

  private static void addIfVariable(Tree tree, ImmutableSet.Builder<VarSymbol> setBuilder) {
    if (tree.getKind() == Kind.VARIABLE) {
      setBuilder.add(ASTHelpers.getSymbol((VariableTree) tree));
    }
  }

  private static void addAllIfVariable(
      List<? extends Tree> list, ImmutableSet.Builder<VarSymbol> setBuilder) {
    for (Tree tree : list) {
      addIfVariable(tree, setBuilder);
    }
  }

  /**
   * Walks up the given {@code path} and returns true iff the first node matching {@code predicate1}
   * occurs lower in the AST than the first node node matching {@code predicate2}. Returns false if
   * no node matches {@code predicate1} or if no node matches {@code predicate2}.
   *
   * @param predicate1 A {@link BiPredicate} that accepts the current node and its parent
   * @param predicate2 A {@link BiPredicate} that accepts the current node and its parent
   */
  private static boolean lowerThan(
      TreePath path, BiPredicate<Tree, Tree> predicate1, BiPredicate<Tree, Tree> predicate2) {
    int index1 = -1;
    int index2 = -1;
    int count = 0;
    path = path.getParentPath();
    while (path != null) {
      Tree curr = path.getLeaf();
      TreePath parentPath = path.getParentPath();
      if (index1 < 0 && predicate1.test(curr, parentPath == null ? null : parentPath.getLeaf())) {
        index1 = count;
      }
      if (index2 < 0 && predicate2.test(curr, parentPath == null ? null : parentPath.getLeaf())) {
        index2 = count;
      }
      if (index1 >= 0 && index2 >= 0) {
        break;
      }
      path = parentPath;
      count++;
    }

    return (index1 >= 0) && (index1 < index2);
  }

  private FindIdentifiers() {}
}
