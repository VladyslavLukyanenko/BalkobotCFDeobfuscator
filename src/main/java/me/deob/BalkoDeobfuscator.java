package me.deob;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.utils.StringEscapeUtils;
import me.deob.str.b;
import me.deob.str.e;

import java.util.function.BiFunction;

public class BalkoDeobfuscator {
  public static DeobfuscationResult tryDeobfuscate(MethodCallExpr call, Scope scope) {
    if (!call.getScope()
        .map(s -> {
          if (s.isNameExpr()) {
            final var name = s.asNameExpr().getNameAsString();
            return name.equals("b") || name.equals("e");
          }

          if (!(s instanceof FieldAccessExpr field) || !(field.getScope() instanceof NameExpr fieldScope)) {
            return false;
          }

          final var fieldName = field.getNameAsString();
          return fieldScope.getNameAsString().equals("Adidas")
              && (fieldName.equals("b") || fieldName.equals("e"));
        })
        .orElse(false)) {
      return DeobfuscationResult.FAILURE;
    }

    Expression strHolder;
    Expression paramHolder;
    BiFunction<Integer, String, String> deobfuscator;
    final var callname = call.getNameAsString();
    String replacementLiteral;
    switch (callname) {
      case "indexOf": {
        paramHolder = call.getArguments().get(0);
        strHolder = call.getArguments().get(1);
        deobfuscator = b::indexOf;
        break;
      }
      case "concat": {
        strHolder = call.getArguments().get(0);
        paramHolder = call.getArguments().get(1);
        deobfuscator = (n, s) -> e.concat(s, n);
        break;
      }
      default:
        return DeobfuscationResult.FAILURE;
    }


    final int deobfParam;
    final String obfuscatedStr;

    if (paramHolder.isLiteralExpr()) {
      deobfParam = IntUtil.safeParse(paramHolder.asLiteralStringValueExpr().getValue());
    } else if (paramHolder.isBinaryExpr()) {
      if (scope == null) {
        return DeobfuscationResult.FAILURE;
      }

      try {
        deobfParam = IntUtil.safeParse(
            Util.foldBinaryExprToLiteral(scope, paramHolder.getBegin().get(), paramHolder.asBinaryExpr()).getValue());
      } catch (MutationHasNoLiteralValueException e) {
        return DeobfuscationResult.FAILURE;
      }
    } else {
      if (scope == null) {
        return DeobfuscationResult.FAILURE;
      }

      var name = paramHolder.asNameExpr().getNameAsString();

      var variable = scope.getLastMutation(name, paramHolder.getBegin().get());
      if (variable == null || !variable.isSimpleInitialized()) {
        return DeobfuscationResult.FAILURE;
      }
      deobfParam = IntUtil.safeParse(variable.value().toString());
    }

    if (strHolder.isLiteralExpr()) {
//      if (strHolder.getComment().isPresent()) {
//        obfuscatedStr = (String) RawUtf8BytesParserUtil.parseFromExprOrDefault(strHolder, strHolder.asStringLiteralExpr().asString());
//      } else {
        obfuscatedStr = ((StringLiteralExpr) strHolder).getValue();
//      }
    } else if (strHolder.isCastExpr()) {
      if (scope == null) {
        return DeobfuscationResult.FAILURE;
      }

      var name = strHolder.asCastExpr().getExpression().asNameExpr().getNameAsString();

      var variable = scope.getLastMutation(name, strHolder.getBegin().get());
      if (!variable.isSimpleInitialized()) {
        return DeobfuscationResult.FAILURE;
      }
      obfuscatedStr = variable.value().toString();
    } else {
      if (scope == null) {
        return DeobfuscationResult.FAILURE;
      }

      var name = strHolder.asNameExpr().getNameAsString();

      var variable = scope.getLastMutation(name, strHolder.getBegin().get());
      if (!variable.isSimpleInitialized()) {
        return DeobfuscationResult.FAILURE;
      }
      obfuscatedStr = variable.value().toString();
    }

    final var unescapedStr = StringEscapeUtils.unescapeJava(obfuscatedStr);
    replacementLiteral = deobfuscator.apply(deobfParam, unescapedStr);
    final var escapedValue = StringEscapeUtils.escapeJava(replacementLiteral);

    call.replace(new StringLiteralExpr(call.getTokenRange().get(), escapedValue));
    return DeobfuscationResult.of(escapedValue);
  }
}
