// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.java.decompiler.main.MemberNameUtil;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.UNDEFINED_TYPE_STRING;

public class VarNamesCollector {

  private final Set<String> usedNames = new HashSet<>();

  public VarNamesCollector() { }

  public VarNamesCollector(Collection<String> setNames) {
    usedNames.addAll(setNames);
  }

  public void addName(String value) {
    usedNames.add(value);
  }

  public String getFreeName(int index, VarType type) {
    if (type == null) {
      return getFreeName("var" + index);
    }

    var typeName = MemberNameUtil.ensureClsNameIsSimple(ExprProcessor.getTypeName(type, true));
    if (typeName.equals(UNDEFINED_TYPE_STRING)) {
      return getFreeName("var" + index);
    }
    var camelCase = Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
    return getFreeName(camelCase + index);
  }

  public String getFreeName(String proposition) {
    while (usedNames.contains(proposition)) {
      proposition += "x";
    }
    usedNames.add(proposition);
    return proposition;
  }
}
