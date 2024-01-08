package me.deob;

import com.github.javaparser.ast.expr.Expression;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class RawUtf8BytesParserUtil {
  public static String parse(String raw) {
    var tokens = raw.split(",");
    var strBytes = new byte[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      String t = tokens[i];
      strBytes[i] = Byte.parseByte(t);
    }

    var in = new DataInputStream(new ByteArrayInputStream(strBytes));
    try {
      return in.readUTF();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public static Object parseFromExprOrDefault(Expression expr, Object defaultValue) {
    return expr.getComment()
        .map(c -> (Object) RawUtf8BytesParserUtil.parse(c.asBlockComment().getContent()))
        .orElse(defaultValue);
  }
}
