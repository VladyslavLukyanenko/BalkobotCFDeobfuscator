package me.deob;

import com.github.javaparser.ast.expr.AssignExpr;

public class CalcUtil {
  private final Scope scope;

  public CalcUtil(Scope scope) {
    this.scope = scope;
  }

  public int calculateInt(Object value, VarDecl decl, AssignExpr expr) {
    var operand = IntUtil.safeParse(value.toString());
    int calculatedValue;
    if (expr.getOperator() == AssignExpr.Operator.ASSIGN) {
      calculatedValue = operand;
    } else {
      var last = scope.getLastMutation(decl.getName(), expr.getRange().get().begin);
      var lastValue = IntUtil.safeParse(last.value().toString()); // todo: why here can be a String?
      calculatedValue = switch (expr.getOperator()) {
        case ASSIGN -> operand;
        case PLUS -> lastValue + operand;
        case MINUS -> lastValue - operand;
        case MULTIPLY -> lastValue * operand;
        case DIVIDE -> lastValue / operand;
        case BINARY_AND -> lastValue & operand;
        case BINARY_OR -> lastValue | operand;
        case XOR -> lastValue ^ operand;
        case REMAINDER -> lastValue % operand;
        case LEFT_SHIFT -> lastValue << operand;
        case SIGNED_RIGHT_SHIFT -> lastValue >> operand;
        case UNSIGNED_RIGHT_SHIFT -> lastValue >>> operand;
      };
    }

    return calculatedValue;
  }
}
