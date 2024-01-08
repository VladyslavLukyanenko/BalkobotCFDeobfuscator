package me.deob;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;

public class VarDecl {
  private final String name;
  private final Node decl;
  private Expression init;
  private Value initValue;

  private VarDecl(String name, Node decl, Expression init, Value value) {
    if (name == null) {
      throw new IllegalArgumentException("Name is null");
    }

    if (decl == null) {
      throw new IllegalArgumentException("Decl is null");
    }

    if (value == Value.UNINITIALIZED && init != null) {
      throw new IllegalArgumentException("init must be null for uninitialized var decl");
    }

    if (init != null && !(decl instanceof VariableDeclarator)) {
      throw new IllegalArgumentException("decl must be VariableDeclarator if init provided");
    }

    this.name = name;
    this.decl = decl;
    this.init = init;
    this.initValue = value;
  }

  public static VarDecl field(VariableDeclarator variable) {
    Expression init = null;
    if (variable.getInitializer().isPresent()) {
      init = variable.getInitializer().get();
    }

    return new VarDecl(variable.getNameAsString(), variable, init, Value.FIELD_VAR);
  }

  public static VarDecl field(EnumConstantDeclaration variable) {
    return new VarDecl(variable.getNameAsString(), variable, null, Value.FIELD_VAR);
  }

  public static VarDecl methodParam(Parameter parameter) {
    return new VarDecl(parameter.getNameAsString(), parameter, null, Value.METHOD_PARAM);
  }

  public static VarDecl complex(VariableDeclarator variable) {
    return new VarDecl(variable.getNameAsString(), variable, variable.getInitializer().get(), Value.COMPLEX);
  }

  public static VarDecl uninitialized(VariableDeclarator variable) {
    return new VarDecl(variable.getNameAsString(), variable, null, Value.UNINITIALIZED);
  }

  public static VarDecl literal(VariableDeclarator variable, Object value) {
    return new VarDecl(variable.getNameAsString(), variable, variable.getInitializer().get(), new Value(value));
  }

  public void setInitializer(Expression newInit) {
    if (!(decl instanceof VariableDeclarator d)) {
      throw new IllegalStateException("Can't set initializer for parameter");
    }

    init = newInit;
    d.setInitializer(newInit);
  }

  public Range getRange() {
    if (init != null) {
      return init.getRange().get();
    }

    if (decl != null) {
      return decl.getRange().get();
    }

    throw new IllegalStateException("Can't get range: decl and init are nulls");
  }

  public Expression init() {
    return init;
  }

  public void setInitialValue(Object value) {
    initValue = new Value(value);
  }

  public Value getInitialValue() {
    if (initValue == null) {
      throw new IllegalStateException("No initial value was set");
    }

    return initValue;
  }

  public String getName() {
    return name;
  }

  public boolean hasInitialValue() {
    return initValue != null && initValue.isSimpleInitialized();
  }

/*
  public VarMutation initial(Expression expr, Value value) {
    return new VarMutation(value, expr, this, true);
  }

  public VarMutation initialComplex(Expression expr) {
    return new VarMutation(Value.COMPLEX, expr, decl, true);
  }

  public VarMutation complexMutation(Expression expr) {
    return new VarMutation(Value.COMPLEX, expr, this, false);
  }

  public VarMutation initialComplex(VarMutation prevSameMutation) {
    if (!prevSameMutation.isComplex()) {
      throw new IllegalArgumentException("Mutation must be complex");
    }

    return initialComplex(prevSameMutation.decl, prevSameMutation.expr);
  }*/

  @Override
  public String toString() {
    return "VarDecl{" +
        name +
        ", initValue=" + initValue +
        ", decl=" + decl +
        ", init=" + init +
        '}';
  }
}
