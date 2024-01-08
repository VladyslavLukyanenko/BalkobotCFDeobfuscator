package org.jetbrains.java.decompiler.main;

public class MemberNameUtil {
  public static String ensureClsNameIsSimple(String name) {
    if (!name.contains(".")) {
      return name;
    }

    if (name.contains("$")) {
      return name.replace('.', '_')
        .replace("$", "__");
    }

    var tokens = name.split("\\.");
    var simpleName = new StringBuilder();
    for (String t : tokens) {
      if (Character.isUpperCase(t.charAt(0))) {
        simpleName.append(t);
      }
    }

    if (simpleName.length() > 0) {
      return simpleName.toString();
    }

    return name.replace('.', '_')
      .replace("$", "__");
  }

  public static String toTitleCase(String type) {
    return Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }

  public static String toCamelCase(String type) {
    return Character.toLowerCase(type.charAt(0)) + type.substring(1);
  }
}
