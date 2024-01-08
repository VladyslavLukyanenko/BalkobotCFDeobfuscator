package me.deob;

import com.github.javaparser.Position;

public interface Scope {
//  void populateInitialMutations();
  void populateScopeMutations();

  VarDecl getVarDeclaration(String varName);

  VarMutation getLastMutation(String varName);
  VarMutation getLastMutation(String varName, Position before);

  void addDecl(VarDecl decl);

  void addMutation(VarMutation mutation);

  boolean containsVar(String name);
}
