package me.deob;

import com.github.javaparser.Position;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.expr.*;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

public class Util {
  private static class Js {
    public static ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
  }

  public static LiteralStringValueExpr foldBinaryExprToLiteral(Scope scope, Position before, BinaryExpr be) throws MutationHasNoLiteralValueException {
    for (NameExpr nameExpr : be.findAll(NameExpr.class)) {
      if (nameExpr.getParentNode().map(p -> p instanceof MethodCallExpr m && m.getScope().map(s -> s == nameExpr).orElse(false)).orElse(false)) {
        continue;
      }

      var f = Util.convertNameRefToLiteral(scope, before, nameExpr);
      nameExpr.replace(f);
    }

    try {
      String strExpr = be.toString();
      if (strExpr.contains("--")) {
        strExpr = strExpr.replaceAll("-(-\\d+(\\.\\d+E\\d)?)", "-($1)");
      }
      else if (strExpr.contains("~-")) {
        strExpr = strExpr.replaceAll("~(-\\d+(\\.\\d+E\\d)?)", "~($1)");
      }

      if (!Pattern.matches(".*[a-zA-Z]{2,}.*", strExpr)) {
        var expr = "(" + strExpr + ")";
        if (be.getLeft().isIntegerLiteralExpr() && be.getRight().isIntegerLiteralExpr()) {
          expr = "parseInt(" + expr + ").toFixed(0)";
        } else {
          expr = "+" + expr;
        }

        final Object evalResult = Js.engine.eval(expr);
        if (evalResult != null) {
          String value;
          if (!(be.getLeft().isIntegerLiteralExpr() && be.getRight().isIntegerLiteralExpr()) && evalResult instanceof Number) {
            value = new BigDecimal(evalResult.toString()).toPlainString();
          } else {
            value = evalResult.toString();
          }

          return new IntegerLiteralExpr(be.getTokenRange().get(), value);
        }
      }

//      System.out.println("WARN: can't evaluate expr: " + strExpr);
    } catch (ScriptException e) {
      // noop
    }
    throw new RuntimeException("Failed to eval expr: " + be);
    /*var nameExprs = new ArrayList<NameExpr>();
    do {
      nameExprs.clear();
      if (be.getLeft() instanceof NameExpr ne) {
        nameExprs.add(ne);
      }
      if (be.getRight() instanceof NameExpr ne) {
        nameExprs.add(ne);
      }

      for (var ne : nameExprs) {
        // replace name ref to literal
        LiteralExpr replacement = convertNameRefToLiteral(scope, before, ne);
        ne.replace(replacement);
      }
    } while (!nameExprs.isEmpty());

    var binExprs = new ArrayList<BinaryExpr>();
    do {
      binExprs.clear();
      if (be.getLeft() instanceof BinaryExpr ne) {
        binExprs.add(ne);
      }
      if (be.getRight() instanceof BinaryExpr ne) {
        binExprs.add(ne);
      }

      for (var ne : binExprs) {
        // replace name ref to literal
        LiteralExpr replacement = foldBinaryExprToLiteral(scope, before, ne);
        ne.replace(replacement);
      }
    } while (!binExprs.isEmpty());

    var unaryExprs = new ArrayList<UnaryExpr>();
    do {
      unaryExprs.clear();
      if (be.getLeft() instanceof UnaryExpr ne) {
        unaryExprs.add(ne);
      }
      if (be.getRight() instanceof UnaryExpr ne) {
        unaryExprs.add(ne);
      }

      for (var ne : unaryExprs) {
        // replace name ref to literal
        LiteralExpr replacement = foldUnaryExpr(scope, ne, ne.getTokenRange().get());
        ne.replace(replacement);
      }
    } while (!unaryExprs.isEmpty());

    var enclosedExprs = new ArrayList<EnclosedExpr>();
    do {
      enclosedExprs.clear();
      if (be.getLeft() instanceof EnclosedExpr ne) {
        enclosedExprs.add(ne);
      }
      if (be.getRight() instanceof EnclosedExpr ne) {
        enclosedExprs.add(ne);
      }

      for (var ne : enclosedExprs) {
        var replacement = foldEnclosedExpr(scope, ne, before);
        ne.replace(replacement);
      }
    } while (!enclosedExprs.isEmpty());

    final TokenRange tokenRange = be.getTokenRange().get();
    LiteralStringValueExpr primitiveAssignValue = null;
    final BinaryExpr.Operator binOp = be.getOperator();
    if (be.getLeft() instanceof LiteralExpr lLiterExpr && be.getRight() instanceof LiteralExpr rLiterExpr) {
      if (lLiterExpr.isIntegerLiteralExpr() && rLiterExpr.isIntegerLiteralExpr()) {
        primitiveAssignValue = calculateIntegerBinaryExpr(binOp,
            lLiterExpr.asIntegerLiteralExpr(), rLiterExpr.asIntegerLiteralExpr(), tokenRange);
      } else if (lLiterExpr.isStringLiteralExpr() || rLiterExpr.isStringLiteralExpr()) {
        primitiveAssignValue = calculateStringBinaryExpr(binOp,
            lLiterExpr.asLiteralStringValueExpr(), rLiterExpr.asLiteralStringValueExpr(), tokenRange);
      } else if (lLiterExpr.isDoubleLiteralExpr() || rLiterExpr.isDoubleLiteralExpr()) {
        primitiveAssignValue = calculateDoubleBinaryExpr(binOp,
            lLiterExpr.asLiteralStringValueExpr(), rLiterExpr.asLiteralStringValueExpr(), tokenRange);
      } else {
        throw new RuntimeException("Unsupported expr: " + be);
//        return null;
      }
    } else {
      return null;
    }

    return primitiveAssignValue;*/
  }

  public static IntegerLiteralExpr calculateIntUnaryExpr(IntegerLiteralExpr intLiter, UnaryExpr.Operator operator,
                                                         TokenRange tokenRange) {
    var currValue = intLiter.asNumber().intValue();
    Integer calculatedValue = switch (operator) {
      case PLUS -> currValue;
      case MINUS -> currValue * -1;
      case PREFIX_INCREMENT -> ++currValue;
      case PREFIX_DECREMENT -> --currValue;
      case LOGICAL_COMPLEMENT -> throw new IllegalStateException();
      case BITWISE_COMPLEMENT -> ~currValue;
      case POSTFIX_INCREMENT -> ++currValue; // we will replace initializer by const so we need value to be changed
      case POSTFIX_DECREMENT -> --currValue; // we will replace initializer by const so we need value to be changed
    };

    return new IntegerLiteralExpr(tokenRange, calculatedValue.toString());
  }

  public static IntegerLiteralExpr calculateDoubleUnaryExpr(DoubleLiteralExpr intLiter, UnaryExpr.Operator operator,
                                                         TokenRange tokenRange) {
    var currValue = intLiter.asDouble();
    Double calculatedValue = switch (operator) {
      case PLUS -> currValue;
      case MINUS -> currValue * -1;
      case PREFIX_INCREMENT -> ++currValue;
      case PREFIX_DECREMENT -> --currValue;
      case LOGICAL_COMPLEMENT -> throw new IllegalStateException();
      case BITWISE_COMPLEMENT -> (double)~(int)currValue;
      case POSTFIX_INCREMENT -> ++currValue; // we will replace initializer by const so we need value to be changed
      case POSTFIX_DECREMENT -> --currValue; // we will replace initializer by const so we need value to be changed
    };

    return new IntegerLiteralExpr(tokenRange, calculatedValue.toString());
  }

  public static LiteralStringValueExpr foldUnaryExpr(Scope scope, UnaryExpr expr, TokenRange tokenRange) throws MutationHasNoLiteralValueException {
    if (expr.getExpression() instanceof IntegerLiteralExpr ile) {
      return calculateIntUnaryExpr(ile, expr.getOperator(), tokenRange);
    } else if (expr.getExpression() instanceof DoubleLiteralExpr dle) {
      return calculateDoubleUnaryExpr(dle, expr.getOperator(), tokenRange);
    } else if (expr.getExpression() instanceof NameExpr nameExpr) {
      var literalExpr = convertNameRefToLiteral(scope, tokenRange.getBegin().getRange().get().begin, nameExpr);
      if (literalExpr instanceof IntegerLiteralExpr i) {
        return calculateIntUnaryExpr(i, expr.getOperator(), tokenRange);
      }
    } else if (expr.getExpression() instanceof EnclosedExpr enclosedExpr) {
      if (enclosedExpr.getInner() instanceof BinaryExpr binaryExpr) {
        return foldBinaryExprToLiteral(scope, expr.getRange().get().begin, binaryExpr);
      }
    }

    throw new RuntimeException("Not supported unary: " + expr);
  }

  public static LiteralExpr foldEnclosedExpr(Scope scope, EnclosedExpr ne, Position before) throws MutationHasNoLiteralValueException {
    if (ne.getInner() instanceof BinaryExpr innBe) {
      return foldBinaryExprToLiteral(scope, before, innBe);
    } else if (ne.getInner() instanceof LiteralStringValueExpr s) {
      return s;
    }

    throw new RuntimeException("Not supported enclosed inner: " + ne);
  }

  public static IntegerLiteralExpr calculateIntegerBinaryExpr(BinaryExpr.Operator binOp, IntegerLiteralExpr left,
                                                              IntegerLiteralExpr right, TokenRange range) {
    final Number lNum = left.asNumber();
    final Number rNum = right.asNumber();
    Object o = switch (binOp) {
      case OR -> throw new UnsupportedOperationException();
      case AND -> throw new UnsupportedOperationException();
      case BINARY_OR -> lNum.intValue() | rNum.intValue();
      case BINARY_AND -> lNum.intValue() & rNum.intValue();
      case XOR -> lNum.intValue() ^ rNum.intValue();
      case EQUALS -> throw new UnsupportedOperationException();
      case NOT_EQUALS -> throw new UnsupportedOperationException();
      case LESS -> throw new UnsupportedOperationException();
      case GREATER -> throw new UnsupportedOperationException();
      case LESS_EQUALS -> throw new UnsupportedOperationException();
      case GREATER_EQUALS -> throw new UnsupportedOperationException();
      case LEFT_SHIFT -> lNum.intValue() << rNum.intValue();
      case SIGNED_RIGHT_SHIFT -> lNum.intValue() >> rNum.intValue();
      case UNSIGNED_RIGHT_SHIFT -> lNum.intValue() >>> rNum.intValue();
      case PLUS -> lNum.intValue() + rNum.intValue();
      case MINUS -> lNum.intValue() - rNum.intValue();
      case MULTIPLY -> lNum.intValue() * rNum.intValue();
      case DIVIDE -> lNum.intValue() / rNum.intValue();
      case REMAINDER -> lNum.intValue() % rNum.intValue();
    };

    return new IntegerLiteralExpr(range, o.toString());
  }

  public static DoubleLiteralExpr calculateDoubleBinaryExpr(BinaryExpr.Operator binOp, LiteralStringValueExpr left,
                                                            LiteralStringValueExpr right, TokenRange range) {
    final Number lNum = Double.parseDouble(left.getValue());
    final Number rNum = Double.parseDouble(right.getValue());
    Object o = switch (binOp) {
      case OR -> throw new UnsupportedOperationException();
      case AND -> throw new UnsupportedOperationException();
      case BINARY_OR -> lNum.intValue() | rNum.intValue();
      case BINARY_AND -> lNum.intValue() & rNum.intValue();
      case XOR -> lNum.intValue() ^ rNum.intValue();
      case EQUALS -> throw new UnsupportedOperationException();
      case NOT_EQUALS -> throw new UnsupportedOperationException();
      case LESS -> throw new UnsupportedOperationException();
      case GREATER -> throw new UnsupportedOperationException();
      case LESS_EQUALS -> throw new UnsupportedOperationException();
      case GREATER_EQUALS -> throw new UnsupportedOperationException();
      case LEFT_SHIFT -> lNum.intValue() << rNum.intValue();
      case SIGNED_RIGHT_SHIFT -> lNum.intValue() >> rNum.intValue();
      case UNSIGNED_RIGHT_SHIFT -> lNum.intValue() >>> rNum.intValue();
      case PLUS -> lNum.doubleValue() + rNum.doubleValue();
      case MINUS -> lNum.doubleValue() - rNum.doubleValue();
      case MULTIPLY -> lNum.doubleValue() * rNum.doubleValue();
      case DIVIDE -> lNum.doubleValue() / rNum.doubleValue();
      case REMAINDER -> lNum.doubleValue() % rNum.doubleValue();
    };

    return new DoubleLiteralExpr(range, o.toString());
  }

  public static StringLiteralExpr calculateStringBinaryExpr(BinaryExpr.Operator binOp, LiteralStringValueExpr left,
                                                            LiteralStringValueExpr right, TokenRange range) {
    final String lStr = left.getValue();
    final String rStr = right.getValue();
    Object o = switch (binOp) {
      case OR -> throw new UnsupportedOperationException();
      case AND -> throw new UnsupportedOperationException();
      case BINARY_OR -> throw new UnsupportedOperationException();
      case BINARY_AND -> throw new UnsupportedOperationException();
      case XOR -> throw new UnsupportedOperationException();
      case EQUALS -> throw new UnsupportedOperationException();
      case NOT_EQUALS -> throw new UnsupportedOperationException();
      case LESS -> throw new UnsupportedOperationException();
      case GREATER -> throw new UnsupportedOperationException();
      case LESS_EQUALS -> throw new UnsupportedOperationException();
      case GREATER_EQUALS -> throw new UnsupportedOperationException();
      case LEFT_SHIFT -> throw new UnsupportedOperationException();
      case SIGNED_RIGHT_SHIFT -> throw new UnsupportedOperationException();
      case UNSIGNED_RIGHT_SHIFT -> throw new UnsupportedOperationException();
      case PLUS -> lStr + rStr;
      case MINUS -> throw new UnsupportedOperationException();
      case MULTIPLY -> throw new UnsupportedOperationException();
      case DIVIDE -> throw new UnsupportedOperationException();
      case REMAINDER -> throw new UnsupportedOperationException();
    };

    return new StringLiteralExpr(range, o.toString());
  }

  public static LiteralStringValueExpr convertNameRefToLiteral(Scope scope, Position before, NameExpr ne) throws MutationHasNoLiteralValueException {
    VarMutation usedInAssignVar = scope.getLastMutation(ne.getNameAsString(), before);
    if (usedInAssignVar == null) {
      throw new RuntimeException("For now will throw, can't find");
    }

    if (!usedInAssignVar.isSimpleInitialized()) {
      throw new MutationHasNoLiteralValueException(usedInAssignVar, "Mutation is complex value. Can't be converted to literal");
    }

    LiteralStringValueExpr replacement;
    var tokenRange = usedInAssignVar.expr().getTokenRange().get();
    if (usedInAssignVar.expr().isStringLiteralExpr()) {
      replacement = new StringLiteralExpr(tokenRange, usedInAssignVar.value().toString());
    } else {
      replacement = new IntegerLiteralExpr(tokenRange, usedInAssignVar.value().toString());
    }

    return replacement;
  }

  public static Object getValueFromLiteral(LiteralExpr le) {
    Object value;
   /* if (le instanceof IntegerLiteralExpr il) {
      value = il.asNumber();
    } else if (le instanceof LongLiteralExpr il) {
      value = il.asNumber();
    } else */
    if (le instanceof LiteralStringValueExpr sl) {
      value = sl.getValue();
    } else if (le instanceof NullLiteralExpr) {
      value = null;
    } else if (le instanceof BooleanLiteralExpr bl) {
      value = bl.getValue();
    } else {
      throw new UnsupportedOperationException("Unsupported literal type " + le.getClass().getSimpleName());
    }

    return value;
  }
}
