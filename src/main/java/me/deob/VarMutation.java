package me.deob;

import com.github.javaparser.Position;
import com.github.javaparser.ast.expr.Expression;

public class VarMutation {
  private final Value src;
  private final Expression expr;
  private final VarDecl decl;
  private final boolean initial;

  private VarMutation(Value src, Expression expr, VarDecl decl) {
    this(src, expr, decl, false);
  }

  private VarMutation(Value src, Expression expr, VarDecl decl, boolean initial) {
    this.initial = initial;
    if (src == null) {
      throw new IllegalArgumentException("src is null");
    }

    if (decl == null) {
      throw new IllegalArgumentException("decl is null");
    }

    if (!initial && expr == null) {
      throw new IllegalArgumentException("expr is null");
    }

    this.decl = decl;
    this.src = src;
    this.expr = expr;
  }

  public static VarMutation initial(VarDecl decl, Expression expr, Value value) {
    return new VarMutation(value, expr, decl, true);
  }

  public static VarMutation initialComplex(VarDecl decl, Expression expr) {
    return new VarMutation(Value.COMPLEX, expr, decl, true);
  }

  public VarMutation mutate(Expression mutation) {
    return complex(decl, mutation);
  }

  public static VarMutation uninitializedInit(VarDecl decl, Expression expr) {
    return new VarMutation(Value.UNINITIALIZED, expr, decl, true);
  }

  public static VarMutation methodParam(VarDecl decl, Expression param) {
    return new VarMutation(Value.METHOD_PARAM, param, decl, false);
  }

  public static VarMutation complex(VarDecl decl, Expression expr) {
    return new VarMutation(Value.COMPLEX, expr, decl);
  }

  public static VarMutation uninitialized(VarDecl decl, Expression expr) {
    return new VarMutation(Value.UNINITIALIZED, expr, decl);
  }

  public static VarMutation of(VarDecl decl, Expression expr, Value value) {
    return new VarMutation(value, expr, decl);
  }

  public VarDecl decl() {
    return decl;
  }

  public Object value() {
//    if (src == null) {
//      throw new IllegalStateException("Not primitive value nor computed");
//    }

    return src.value();
  }

  public Expression expr() {
    return this.expr;
  }

  @Override
  public String toString() {
    String srcStr = "<none>";
    if (src != null) {
      srcStr = src.toString();
    }

    return "VarMutation{" +
        decl.getName() +
        ", src=" + srcStr +
        ", expr=" + expr +
        (initial ? ", <initial> " : "") +
        '}';
  }

  public boolean isSimpleInitialized() {
    return src.isSimpleInitialized();
  }

  public Position getBegin() {
    if (initial) {
      return decl.getRange().begin;
    }

    if (expr != null) {
      return expr.getRange().get().begin;
    }

    throw new IllegalStateException();
  }

  public boolean isInitial() {
    return initial;
  }
}
