// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.MemberNameUtil;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class InvalidNamesIdentifierRenamer implements IIdentifierRenamer {
  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
    "abstract", "do", "if", "package", "synchronized", "boolean", "double", "implements", "private", "this", "break", "else", "import",
    "protected", "throw", "byte", "extends", "instanceof", "public", "throws", "case", "false", "int", "return", "transient", "catch",
    "final", "interface", "short", "true", "char", "finally", "long", "static", "try", "class", "float", "native", "strictfp", "void",
    "const", "for", "new", "super", "volatile", "continue", "goto", "null", "switch", "while", "default", "assert", "enum"));
  private static final Set<String> RESERVED_WINDOWS_NAMESPACE = new HashSet<>(Arrays.asList(
    "con", "prn", "aux", "nul",
    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

  private int classCounter = 0;
  private int fieldCounter = 0;
  private int methodCounter = 0;
  private final Set<String> setNonStandardClassNames = new HashSet<>();

  @Override
  public boolean toBeRenamed(Type elementType, String className, String element, String descriptor) {
    if (elementType != Type.ELEMENT_FIELD
      && !DecompilerContext.getStructContext().getClasses().containsKey(className)
      && !(elementType == Type.ELEMENT_METHOD && Pattern.matches("^\\d+$", element))
    ) { // todo: better to check in known classes list because we could define such package
      return false;
    }

    String value = elementType == Type.ELEMENT_CLASS ? className : element;
    return value == null ||
           value.length() <= 2 ||
           !isValidIdentifier(elementType == Type.ELEMENT_METHOD, value) ||
           KEYWORDS.contains(value) ||
           elementType == Type.ELEMENT_CLASS && (
//             value.contains("$") ||
             RESERVED_WINDOWS_NAMESPACE.contains(value.toLowerCase(Locale.ENGLISH)) ||
             value.length() > 255 - ".class".length());
  }

  /**
   * Return {@code true} if, and only if identifier passed is compliant to JLS9 section 3.8 AND DOES NOT CONTAINS so-called "ignorable" characters.
   * Ignorable characters are removed by javac silently during compilation and thus may appear only in specially crafted obfuscated classes.
   * For more information about "ignorable" characters see <a href="https://bugs.openjdk.java.net/browse/JDK-7144981">JDK-7144981</a>.
   *
   * @param identifier Identifier to be checked
   * @return {@code true} in case {@code identifier} passed can be used as an identifier; {@code false} otherwise.
   */
  private static boolean isValidIdentifier(boolean isMethod, String identifier) {

    assert identifier != null : "Null identifier passed to the isValidIdentifier() method.";
    assert !identifier.isEmpty() : "Empty identifier passed to the isValidIdentifier() method.";

    if (isMethod && (identifier.equals(CodeConstants.INIT_NAME) || identifier.equals(CodeConstants.CLINIT_NAME))) {
      return true;
    }

    if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
      return false;
    }

    char[] chars = identifier.toCharArray();

    for(int i = 1; i < chars.length; i++) {
      char ch = chars[i];

      if ((!Character.isJavaIdentifierPart(ch)) || Character.isIdentifierIgnorable(ch)) {
        return false;
      }
    }

    return true;

  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  @Override
  public String getNextClassName(String fullName, String shortName) {
    if (shortName == null) {
      return "class_" + (classCounter++);
    }

    int index = 0;
    while (index < shortName.length() && Character.isDigit(shortName.charAt(index))) {
      index++;
    }

    var normalizedShortName = shortName.replace("$", "__");
    if (index == 0 || index == shortName.length()) {
      return "class_" + normalizedShortName;
    }
    else {
      String name = shortName.substring(index);
      if (setNonStandardClassNames.contains(name)) {
        return "Inner" + name + "_" + (classCounter++);
      }
      else {
        setNonStandardClassNames.add(name);
        return "Inner" + name;
      }
    }
  }

  @Override
  public String getNextFieldName(String className, String field, String descriptor) {
    var parsedDescriptor = FieldDescriptor.parseDescriptor(descriptor);
    var type = ExprProcessor.getTypeName(parsedDescriptor.type, true);

//    final int maxSymbolsCount = 10;
//    var fieldType = type.substring(0, Math.min(type.length(), maxSymbolsCount));
    if (descriptor.startsWith("[")) {
      type += "Arr";
    }

    return "f_" + type + "_" + /*(fieldCounter++)*/field;
  }

  @Override
  public String getNextMethodName(String className, String method, String descriptor) {
    var parsedDescriptor = MethodDescriptor.parseDescriptor(descriptor);
    var retType = ExprProcessor.getTypeName(parsedDescriptor.ret, true);
    if (descriptor.startsWith("[")) {
      retType += "Arr";
    }
    if (parsedDescriptor.params.length == 0 && !parsedDescriptor.ret.equals(VarType.VARTYPE_VOID)) {
      return "get" + MemberNameUtil.toTitleCase(retType) + method;
    }
    if (parsedDescriptor.params.length == 1 && parsedDescriptor.ret.equals(VarType.VARTYPE_VOID)) {
      return "handle" + MemberNameUtil.toTitleCase(ExprProcessor.getTypeName(parsedDescriptor.params[0], true)) + method;
    }

    var builder = new StringBuilder();
    builder.append(MemberNameUtil.toCamelCase(retType));
    for (var p : parsedDescriptor.params) {
      var pname = ExprProcessor.getTypeName(p, true);
      pname = MemberNameUtil.ensureClsNameIsSimple(pname);
      if (p.arrayDim > 0) {
        pname += "Arr";
      }

      builder.append(MemberNameUtil.toTitleCase(pname));
    }

    builder.append(method);
    return MemberNameUtil.toCamelCase(builder.toString());
//
//    if (Arrays.stream(parsedDescriptor.params).anyMatch(v -> ExprProcessor.getTypeName(v, true).equals("HttpResponse"))) {
//      return "processResponse_" + method;
//    }
//
//    if (Arrays.stream(parsedDescriptor.params).anyMatch(v -> ExprProcessor.getTypeName(v, true).equals("HttpRequest"))) {
//      return "processRequest_" + method;
//    }
//
//    if (ExprProcessor.getTypeName(parsedDescriptor.ret, true).equals("CompletableFuture")) {
//      return "prepareCompletableFuture" + method;
//    }
//
//
//    // fixme: fallback to name based on return type
//    if (parsedDescriptor.params.length == 0 && !parsedDescriptor.ret.equals(VarType.VARTYPE_VOID)) {
//      return "get" + toTitleCase(retType) + method;
//    }
//
//    return "method_" + /*(methodCounter++)*/method;
  }

  // *****************************************************************************
  // static methods
  // *****************************************************************************

  public static String getSimpleClassName(String fullName) {
    return fullName.substring(fullName.lastIndexOf('/') + 1);
  }

  public static String replaceSimpleClassName(String fullName, String newName) {
    return fullName.substring(0, fullName.lastIndexOf('/') + 1) + newName;
  }
}