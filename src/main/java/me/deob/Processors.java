package me.deob;

import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithCondition;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.VarType;

import java.util.*;

public class Processors {
  public static void processUnit(HashMap<String, Integer> cls0Fields, CompilationUnit cu) {
    preprocess(cls0Fields, cu);

    // todo: create blocks hierarchy with var defs + mutations and propagate to child blocks as loopback for vars
    // process blocks
//    if (1 != 1)
//for (var o = 0; o < 30; o++)
    cu.findAll(FieldDeclaration.class).forEach(fd -> {
      fd.findAll(MethodCallExpr.class).forEach(call -> {
        try {
          /*var deobfResult = */
          BalkoDeobfuscator.tryDeobfuscate(call, null);
          /*if (!deobfResult.succeeded()) {
            return;
          }

          call.replace(new StringLiteralExpr(call.getTokenRange().get(), deobfResult.result()));*/
        } catch (Exception exc) {
          System.err.println("Failed to process block. Cause: " + exc.getMessage());
        }
      });
    });
    cu.findAll(EnumConstantDeclaration.class).forEach(fd -> {
      fd.findAll(MethodCallExpr.class).forEach(call -> {
        try {
          /*var deobfResult = */
          BalkoDeobfuscator.tryDeobfuscate(call, null);
          /*if (!deobfResult.succeeded()) {
            return;
          }

          call.replace(new StringLiteralExpr(call.getTokenRange().get(), deobfResult.result()));*/
        } catch (Exception exc) {
          System.err.println("Failed to process block. Cause: " + exc.getMessage());
        }
      });
    });
    cu.findAll(MethodDeclaration.class).forEach(md -> {
      var registry = new HashMap<String, BlockScopeImpl>();
      try {
        if (md.getParentNode().map(p -> p instanceof ObjectCreationExpr).orElse(false)) {
          // anon obj expr
          return;
        }
        var methodsToProcess = md.findAll(MethodDeclaration.class);
        processSwitches(md, registry);
        processObfuscatedStrings(md, registry);
//        for (var method : methodsToProcess) {
//          processSwitches(method, registry);
//          processObfuscatedStrings(method, registry);
//        }

        for (var method : methodsToProcess) {
          eliminateUnusedVars(method);
          toSimplifiedSSAForm(md);
          eliminateUnusedAssignments(method);
          eliminateUnusedVars(md);
        }
      } catch (Exception exc) {
        System.err.println("Can't process: " + md.getNameAsString() + ", cause: " + exc.getMessage());
      }
    });

    cu.findAll(ConstructorDeclaration.class).forEach(md -> {
      var registry = new HashMap<String, BlockScopeImpl>();
      try {
        processSwitches(md, registry);
        processObfuscatedStrings(md, registry);
      } catch (Exception exc) {
        System.err.println("Can't process: " + md.getNameAsString());
      }
    });

    cu.findAll(InitializerDeclaration.class).forEach(md -> {
      var registry = new HashMap<String, BlockScopeImpl>();
      try {
        processSwitches(md, registry);
        processObfuscatedStrings(md, registry);
      } catch (Exception exc) {
        System.err.println("Can't process: <" + (md.isStatic() ? "cl" : "") + "init>");
      }
    });

    cu.findAll(TryStmt.class).forEach(tryStmt -> {
      if (tryStmt.getTryBlock().getStatements().isEmpty()) {
        tryStmt.remove();
      }
    });
    cu.findAll(LabeledStmt.class).forEach(labeledStmt -> {
      if (!(labeledStmt.getStatement() instanceof TryStmt t)) {
        return;
      }

      if (t.getTryBlock().getStatements().isEmpty()) {
        labeledStmt.remove();
      }
    });

    final var whileStmts = cu.findAll(WhileStmt.class, Node.TreeTraversal.POSTORDER);
    for (int j = 0; j < whileStmts.size(); j++) {
      WhileStmt whileStmt = whileStmts.get(j);
      if (
          whileStmt.getCondition().isBooleanLiteralExpr()
              && whileStmt.getCondition().asBooleanLiteralExpr().getValue()
              && whileStmt.getBody() instanceof BlockStmt whileBody
      ) {
        if (
            whileBody.findAll(ContinueStmt.class).isEmpty()
                && whileBody.getStatement(whileBody.getStatements().size() - 1) instanceof ReturnStmt
                && whileStmt.getParentNode().get() instanceof BlockStmt parent
        ) {
          var ix = parent.getStatements().indexOf(whileStmt);
          NodeList<Statement> statements = whileBody.getStatements();
          for (int i = 0; i < statements.size(); i++) {
            Statement s = statements.get(i);
            parent.addStatement(ix + i, s);
          }

          whileStmt.remove();
        }
      }
    }
  }

  private static void preprocess(HashMap<String, Integer> cls0Fields, CompilationUnit cu) {
    // preprocess file globally

    // replace bool casts
    cu.findAll(CastExpr.class).forEach(c -> {
      if (c.getTypeAsString().equals("boolean") && c.getExpression() instanceof EnclosedExpr e && e.getInner() instanceof IntegerLiteralExpr ie) {
        c.replace(new BooleanLiteralExpr(ie.asNumber().intValue() != 0));
      }
    });

    // replace unary with literal
    cu.findAll(UnaryExpr.class).forEach(unary -> {
      final Expression valueExpr = unary.getExpression();
      if (valueExpr instanceof IntegerLiteralExpr intLiter) {
        final UnaryExpr.Operator operator = unary.getOperator();
        final TokenRange tokenRange = unary.getTokenRange().get();
        final IntegerLiteralExpr result = Util.calculateIntUnaryExpr(intLiter, operator, tokenRange);
        unary.replace(result);
      }
    });

    // inline `class_0` fields usages
    cu.findAll(FieldAccessExpr.class).forEach(fae -> {
      final Expression scope = fae.getScope();
      if (scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals("class_0")) {
        final String value = cls0Fields.get(fae.getName().asString()).toString();
        fae.replace(new IntegerLiteralExpr(fae.getTokenRange().get(), value));
      }
    });

    // unwrap `if`
    cu.findAll(IfStmt.class).forEach(ifs -> {
      if (ifs.getCondition() instanceof BinaryExpr binaryExpr && binaryExpr.getLeft().isIntegerLiteralExpr() && binaryExpr.getRight().isIntegerLiteralExpr()) {
        final int l = binaryExpr.getLeft().asIntegerLiteralExpr().asNumber().intValue();
        final int r = binaryExpr.getRight().asIntegerLiteralExpr().asNumber().intValue();
        var ok = switch (binaryExpr.getOperator()) {
          case EQUALS -> l == r;
          case NOT_EQUALS -> l != r;
          case LESS -> l < r;
          case GREATER -> l > r;
          case LESS_EQUALS -> l <= r;
          case GREATER_EQUALS -> l >= r;
          case OR -> throw new RuntimeException();
          case AND -> throw new RuntimeException();
          case BINARY_OR -> throw new RuntimeException();
          case BINARY_AND -> throw new RuntimeException();
          case XOR -> throw new RuntimeException();
          case LEFT_SHIFT -> throw new RuntimeException();
          case SIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case UNSIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case PLUS -> throw new RuntimeException();
          case MINUS -> throw new RuntimeException();
          case MULTIPLY -> throw new RuntimeException();
          case DIVIDE -> throw new RuntimeException();
          case REMAINDER -> throw new RuntimeException();
        };

        if (ok) {
          if (ifs.hasElseBranch()) {
            ifs.removeElseStmt();
          } else {
            ifs.remove();
            return;
          }
        } else {
          if (!ifs.hasElseBranch()) {
            ifs.remove();
            return;
          } else {
            var els = ifs.getElseStmt().get();
            ifs.setThenStmt(els);
            ifs.removeElseStmt();
          }
        }

        var then = ifs.getThenStmt();
        var parent = (BlockStmt) ifs.getParentNode().get();
        var ix = parent.getStatements().indexOf(ifs);
        if (then instanceof BlockStmt blockStmt) {
          NodeList<Statement> statements = blockStmt.getStatements();
          for (int i = 0; i < statements.size(); i++) {
            Statement s = statements.get(i);
            if (s instanceof BreakStmt || s instanceof ContinueStmt) {
              break;
            }

            parent.addStatement(i + ix, s);
          }
        } else {
          parent.addStatement(ix, then);
        }

        ifs.remove();
      }
    });

    // fold assigning
    cu.findAll(AssignExpr.class).forEach(assignExpr -> {
      if (!(assignExpr.getValue() instanceof ConditionalExpr cond)) {
        return;
      }

      if (cond.getCondition() instanceof BinaryExpr binaryExpr && binaryExpr.getLeft().isIntegerLiteralExpr() && binaryExpr.getRight().isIntegerLiteralExpr()) {
        final int l = binaryExpr.getLeft().asIntegerLiteralExpr().asNumber().intValue();
        final int r = binaryExpr.getRight().asIntegerLiteralExpr().asNumber().intValue();
        var ok = switch (binaryExpr.getOperator()) {
          case EQUALS -> l == r;
          case NOT_EQUALS -> l != r;
          case LESS -> l < r;
          case GREATER -> l > r;
          case LESS_EQUALS -> l <= r;
          case GREATER_EQUALS -> l >= r;
          case OR -> throw new RuntimeException();
          case AND -> throw new RuntimeException();
          case BINARY_OR -> throw new RuntimeException();
          case BINARY_AND -> throw new RuntimeException();
          case XOR -> throw new RuntimeException();
          case LEFT_SHIFT -> throw new RuntimeException();
          case SIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case UNSIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case PLUS -> throw new RuntimeException();
          case MINUS -> throw new RuntimeException();
          case MULTIPLY -> throw new RuntimeException();
          case DIVIDE -> throw new RuntimeException();
          case REMAINDER -> throw new RuntimeException();
        };

        Expression expr = ok ? cond.getThenExpr() : cond.getElseExpr();
        assignExpr.setValue(expr);
      }
    });

    // fold var decl
    cu.findAll(VariableDeclarator.class).forEach(decl -> {
      if (!(decl.getInitializer().isPresent() && decl.getInitializer().get() instanceof ConditionalExpr cond)) {
        return;
      }

      if (cond.getCondition() instanceof BinaryExpr binaryExpr && binaryExpr.getLeft().isIntegerLiteralExpr() && binaryExpr.getRight().isIntegerLiteralExpr()) {
        final int l = binaryExpr.getLeft().asIntegerLiteralExpr().asNumber().intValue();
        final int r = binaryExpr.getRight().asIntegerLiteralExpr().asNumber().intValue();
        var ok = switch (binaryExpr.getOperator()) {
          case EQUALS -> l == r;
          case NOT_EQUALS -> l != r;
          case LESS -> l < r;
          case GREATER -> l > r;
          case LESS_EQUALS -> l <= r;
          case GREATER_EQUALS -> l >= r;
          case OR -> throw new RuntimeException();
          case AND -> throw new RuntimeException();
          case BINARY_OR -> throw new RuntimeException();
          case BINARY_AND -> throw new RuntimeException();
          case XOR -> throw new RuntimeException();
          case LEFT_SHIFT -> throw new RuntimeException();
          case SIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case UNSIGNED_RIGHT_SHIFT -> throw new RuntimeException();
          case PLUS -> throw new RuntimeException();
          case MINUS -> throw new RuntimeException();
          case MULTIPLY -> throw new RuntimeException();
          case DIVIDE -> throw new RuntimeException();
          case REMAINDER -> throw new RuntimeException();
        };

        Expression expr = ok ? cond.getThenExpr() : cond.getElseExpr();
        decl.setInitializer(expr);
      }
    });

    cu.findAll(SwitchEntry.class).forEach(switchEntry -> {
      if (switchEntry.getStatements().size() >= 1 && !(switchEntry.getStatements().get(0) instanceof BlockStmt)) {
        var blockExpr = new BlockStmt(switchEntry.getTokenRange().get(), switchEntry.getStatements());
        blockExpr.setParentNode(switchEntry);
//        switchEntry.setStatements(new NodeList<>(blockExpr));

      }
    });
  }

  public static void processSwitches(Node cu, final Map<String, BlockScopeImpl> scopesRegistry) {
    cu.findAll(BlockStmt.class).forEach(bs -> {
//      if (cu instanceof MethodDeclaration md && !isContainedInMethod(md, bs)) {
//        return;
//      }

      boolean wasParentRemoved = true;
      Node curr = bs;
      while (curr != null) {
        if (curr instanceof ClassOrInterfaceDeclaration || curr instanceof EnumDeclaration) {
          wasParentRemoved = false;
          break;
        }

        if (curr.getParentNode().isEmpty()) {
          break;
        }

        curr = curr.getParentNode().get();
      }

      if (wasParentRemoved) {
        return;
      }
//      var context = new VarsContext(declaredVars, varMutations);
      boolean eliminated;
      do {
        Scope scope = new BlockScopeImpl(bs, scopesRegistry);
        eliminated = false;
//        try {
        scope.populateScopeMutations();
//        } catch (RuntimeException x) {
//          continue;
//        }

        /*bs.findAll(SwitchStmt.class).forEach(sw -> {

        });*/

        NodeList<Statement> statements = bs.getStatements();
        // switch processing
        for (int stIx = 0; stIx < statements.size(); stIx++) {
          Statement s = statements.get(stIx);
          if (s instanceof LabeledStmt ls && ls.getStatement() instanceof SwitchStmt ss) {
            s.replace(ss);
            s = ss;
          }

          if (s instanceof SwitchStmt swtch) {
            if (swtch.getSelector() instanceof BinaryExpr be) {
              final var clone = be.clone();
              try {
                final var folded = Util.foldBinaryExprToLiteral(scope, swtch.getRange().get().begin, be);
                swtch.setSelector(folded);
              } catch (RuntimeException | MutationHasNoLiteralValueException exc) {
                swtch.setSelector(clone);
                continue;
              }
            }

            final Expression selector = swtch.getSelector();
            Object usedVarValue;
            if (selector instanceof LiteralStringValueExpr sve) {
              usedVarValue = sve.getValue();
            } else {
              String caseEntry = null;
              if (selector instanceof MethodCallExpr mcall) {
                if (mcall.getScope().isPresent()
                    && mcall.getScope().get() instanceof NameExpr ce
                    && ce.getNameAsString().equals("Integer")
                    && mcall.getArguments().size() == 1
                    && mcall.getArguments().get(0) instanceof NameExpr ne
                    && scope.containsVar(ne.getNameAsString())
                ) {
//                  System.out.println("Integer.parse " + ne.getNameAsString());
                  caseEntry = ne.getNameAsString();
                }
              } else if (selector instanceof NameExpr nexpr) {
                caseEntry = nexpr.getNameAsString();
              }

              if (caseEntry == null) {
                continue;
              }

              VarMutation usedInAssignVar = scope.getLastMutation(caseEntry, swtch.getRange().get().begin);
              if (usedInAssignVar == null || !usedInAssignVar.isSimpleInitialized()) {
                continue;
//                throw new IllegalStateException("No mutation found: " + caseEntry);
              }

              usedVarValue = usedInAssignVar.value();
            }

            final SwitchEntry replacementEntry;
            try {
              replacementEntry = swtch.getEntries()
                  .stream()
                  .filter(e -> e.getLabels()
                      .stream()
                      .anyMatch(l ->
                          l instanceof LiteralExpr le
                              && Objects.equals(((LiteralStringValueExpr) le).getValue(), usedVarValue)
                      )
                  )
                  .findFirst()
                  .orElseGet(() -> swtch.getEntries()
                      .stream()
                      .filter(e -> e.getLabels().size() == 0)
                      .findFirst()
                      .get()
                  );
            } catch (Exception exc) {
              continue;
            }
            for (var insertSmIx = 0; insertSmIx < replacementEntry.getStatements().size(); insertSmIx++) {
              final Statement targetToInsert = replacementEntry.getStatement(insertSmIx);
              if (targetToInsert instanceof BreakStmt b && b.getLabel().isEmpty()) {
                break;
              }

              bs.addStatement(stIx + insertSmIx, targetToInsert);
            }

            bs.remove(swtch);
//            stIx--;
            eliminated = true;
            break;
          }
        }
      } while (eliminated);
/*
      NodeList<Statement> statements = bs.getStatements();
      for (int stIx = 0; stIx < statements.size(); stIx++) {
        processSwitches(statements.get(stIx));
      }*/
    });
  }

  public static void eliminateUnusedVars(MethodDeclaration md) {
/*    if (1 == 1)
    return;*/

    while (true) {

      var unusedVars = new ArrayList<String>();
      var usedVars = new HashSet<String>();

      md.findAll(VariableDeclarator.class).forEach(decl -> {
        if (!isContainedInMethod(md, decl)) {
          return;
        }

        unusedVars.add(decl.getNameAsString());
      });

      final var nodes = md.findAll(Node.class);
      for (int nodeIx = 0; nodeIx < nodes.size(); nodeIx++) {
        Node node = nodes.get(nodeIx);
        if (!isContainedInMethod(md, node)) {
          nodeIx++;
          continue;
        }

        for (int varIx = 0; varIx < unusedVars.size(); ) {
          String varName = unusedVars.get(varIx);
          if (isVarUsedBy(node, varName)) {
            usedVars.add(varName);
            unusedVars.remove(varName);
            nodeIx--;
            break;
          } else {
            varIx++;
          }
        }
      }

      var initialCount = unusedVars.size();
      md.findAll(AssignExpr.class).forEach(a -> {
        if (a.getTarget() instanceof NameExpr n && unusedVars.contains(n.getNameAsString())) {
          if (!isContainedInMethod(md, a)) {
            return;
          }

          var stmt = getParentOfType(a, ExpressionStmt.class);
          stmt.remove();
//        a.getParentNode().get().removeComment().remove(a);
        }
      });
      md.findAll(VariableDeclarator.class).forEach(a -> {
        if (!isContainedInMethod(md, a)) {
          return;
        }

        if (unusedVars.contains(a.getNameAsString())) {
          var p = a.getParentNode().orElse(null);
          a.remove();
          if (p instanceof VariableDeclarationExpr e && e.getVariables().isEmpty() && e.getParentNode().get() instanceof ExpressionStmt es) {
            es.removeComment().remove();
          }
        }
      });
//
//      if (initialCount != unusedVars.size()) {
//        continue;
//      }

      printDebug("UNUSED: " + String.join(", ", unusedVars));
      printDebug("used: " + String.join(", ", usedVars));
      break;
    }

  }

  private static class VarCounter {
    private Map<String, Integer> counters = new HashMap<>();

    public String generateNextName(String srcName) {
      var baseName = new StringBuilder();
      for (var ix = 0; ix < srcName.length(); ix++) {
        final var ch = srcName.charAt(ix);
        if (Character.isDigit(ch) || ch == '_') {
          break;
        }

        baseName.append(ch);
      }

      final var varName = baseName.toString();
      var counter = counters.computeIfAbsent(varName, k -> 0);
      counters.put(varName, ++counter);
      return varName + "_g" + counter;
    }
  }

  private static class TmpVarDecl {
    public final String name;
    public final boolean initialized;

    public TmpVarDecl(String name, boolean initialized) {
      this.name = name;
      this.initialized = initialized;
    }


  }

  public static void eliminateUnusedAssignments(MethodDeclaration md) {
//    if (1 == 1)
//      return;

    md.findAll(BlockStmt.class).forEach(blockStmt -> {
      HashMap<String, TmpVarDecl> detectedVarDecls = detectVarDeclsInBlock(blockStmt);

      var stmts = blockStmt.getStatements();
      for (var ix = 0; ix < stmts.size(); ix++) {
        var curr = stmts.get(ix);
        if (!(curr instanceof ExpressionStmt currStmt)) {
          continue;
        }

        if (currStmt.getExpression() instanceof AssignExpr currAssign
            && currAssign.getTarget() instanceof NameExpr ne
            && isSimpleAssignment(currAssign.getValue())
          /*&& detectedVarDecls.containsKey(ne.getNameAsString())*/) {

          final var assignname = ne.getNameAsString();
          var nextAssign = -1;
          for (var nextix = ix + 1; nextix < stmts.size(); nextix++) {
            var nextNode = stmts.get(nextix);

            if (nextNode instanceof ExpressionStmt nextExpr
                && nextExpr.getExpression() instanceof AssignExpr a
                && a.getTarget() instanceof NameExpr nextNodeNe
                && nextNodeNe.getNameAsString().equals(assignname)) {
              nextAssign = nextix + 1;
              break;
            }
          }

          boolean used = false;
          boolean isLastAssignment = false;
          if (nextAssign == -1) {
            isLastAssignment = !detectedVarDecls.containsKey(assignname);
            nextAssign = stmts.size();
          }
          for (var nextix = ix + 1; nextix < nextAssign; nextix++) {
            var nextNode = stmts.get(nextix);
            for (var node : nextNode.findAll(Node.class)) {
              if (isVarUsedBy(node, assignname)) {
                used = true;
                break;
              }
            }
          }

          if (!isLastAssignment && !used) {
            curr.remove();
            ix--;
          }
        }
      }
    });

  }

  private static boolean isSimpleAssignment(Expression currAssign) {
    if (currAssign instanceof LiteralExpr || currAssign instanceof NameExpr) {
      return true;
    }

    if (currAssign instanceof CastExpr cast) {
      return isSimpleAssignment(cast.getExpression());
    }

    return false;
  }

  private static HashMap<String, TmpVarDecl> detectVarDeclsInBlock(BlockStmt blockStmt) {
    var stmts = blockStmt.getStatements();
    var detectedVarDecls = new HashMap<String, TmpVarDecl>();
    for (Statement curr : stmts) {
      if (!(curr instanceof ExpressionStmt expr && expr.getExpression() instanceof VariableDeclarationExpr declarationExpr)) {
        continue;
      }

      for (var decl : declarationExpr.getVariables()) {
//          if (!isContainedInMethod(md, decl)) {
//            return;
//          }

        final var name = decl.getNameAsString();
        detectedVarDecls.put(name, new TmpVarDecl(name, decl.getInitializer().isPresent()));
      }
    }
    return detectedVarDecls;
  }

  private static void toSimplifiedSSAForm(MethodDeclaration md) {
    var methodDetectedVarDecls = new HashMap<String, TmpVarDecl>();
    md.findAll(VariableDeclarator.class).forEach(decl -> {
      if (!isContainedInMethod(md, decl)) {
        return;
      }

      final var name = decl.getNameAsString();
      methodDetectedVarDecls.put(name, new TmpVarDecl(name, decl.getInitializer().isPresent()));
    });

    var varsCounter = new VarCounter();
    md.findAll(BlockStmt.class).forEach(blockStmt -> {
      var stmts = blockStmt.getStatements();
      HashMap<String, TmpVarDecl> detectedVarDecls = detectVarDeclsInBlock(blockStmt);

      HashMap<String, Integer> assignmentIndexes = grabLastAssignmentIndexMap(methodDetectedVarDecls, stmts);
      var renames = new HashSet<String>();

      for (var ix = 0; ix < stmts.size(); ix++) {
        var curr = stmts.get(ix);
        if (!(curr instanceof ExpressionStmt currStmt)) {
          continue;
        }

        if (currStmt.getExpression() instanceof AssignExpr currAssign
            && currAssign.getTarget() instanceof NameExpr ne
            && (methodDetectedVarDecls.containsKey(ne.getNameAsString()) || renames.contains(ne.getNameAsString()))
            /*&& detectedVarDecls.containsKey(ne.getNameAsString())*/) {

          final var assignname = ne.getNameAsString();
          if (!detectedVarDecls.containsKey(assignname) && assignmentIndexes.containsKey(assignname) && assignmentIndexes.get(assignname) == ix) {
            continue;
          }

          var nameToUse = varsCounter.generateNextName(assignname);
//          var decl = detectedVarDecls.get(assignname);
//          if (!decl.initialized && !laterInitializedVarDecls.contains(assignname)) {
//            nameToUse = decl.name;
//            laterInitializedVarDecls.add(nameToUse);
//            continue;
//          }

          final String newName = nameToUse;
          renames.add(newName);

          final var tokenRange = currAssign.getTokenRange().get();
          var varReplacement = new VariableDeclarator(new VarType(tokenRange), newName);
          varReplacement.setInitializer(currAssign.getValue());
          currStmt.setExpression(new VariableDeclarationExpr(tokenRange, NodeList.nodeList(), NodeList.nodeList(),
              NodeList.nodeList(varReplacement)));

          for (var nextix = ix + 1; nextix < stmts.size(); nextix++) {
            var nextNode = stmts.get(nextix);
            if (!detectedVarDecls.containsKey(assignname) && assignmentIndexes.containsKey(assignname) && assignmentIndexes.get(assignname) == nextix) {
              break;
            }

            nextNode.findAll(NameExpr.class).forEach(n -> {
              if (!isContainedInMethod(md, n) || !n.getNameAsString().equals(assignname)) {
                return;
              }

              n.setName(newName);
            });
          }
        }
      }
    });
  }

  private static HashMap<String, Integer> grabLastAssignmentIndexMap(HashMap<String, TmpVarDecl> methodDetectedVarDecls, NodeList<Statement> stmts) {
    var assignmentIndexes = new HashMap<String, Integer>();
    for (var ix = 0; ix < stmts.size(); ix++) {
      var curr = stmts.get(ix);
      if (!(curr instanceof ExpressionStmt currStmt)) {
        continue;
      }

      if (currStmt.getExpression() instanceof AssignExpr currAssign
          && currAssign.getTarget() instanceof NameExpr ne
          && methodDetectedVarDecls.containsKey(ne.getNameAsString())) {
        final var name = ne.getNameAsString();
        assignmentIndexes.put(name, ix);
      }
    }
    return assignmentIndexes;
  }

  private static void printDebug(String x) {
//    System.out.println(x);
  }

  private static boolean isContainedInMethod(MethodDeclaration md, Node node) {
    Node parent = node.getParentNode().orElse(null);
    if (parent == null) {
      return false;
    }

    boolean declaredInCurrentMethod = false;
    while (parent != null) {
      if (parent instanceof MethodDeclaration currMd) {
        declaredInCurrentMethod = currMd == md;
        break;
      }

      parent = parent.getParentNode().orElse(null);
    }
    return declaredInCurrentMethod;
  }

  private static boolean isVarUsedBy(Node node, String varName) {
    // - field set == assigned on right side
    // - assigned on right side
    if (node instanceof VariableDeclarator declarator) {
      if (declarator.getInitializer().map(initializer -> isVarUsedByName(initializer, varName)).orElse(false)) {
        printDebug("VariableDeclarator " + varName);
        return true;
      }
    }
    if (node instanceof AssignExpr assignExpr) {
      if (!(assignExpr.getTarget() instanceof NameExpr an && an.getNameAsString().equals(varName)) && isVarUsedByName(assignExpr.getValue(), varName)) {
        printDebug("AssignExpr " + varName);
        return true;
      }
    }

    // - new obj: ObjectCreationExpr, arguments: NodeList[] [ix]: NameExpr
    // - method call: MethodCallExpr, scope: NameExpr
    // - pass as arg: MethodCallExpr, arguments: NodeList[] [ix]: NameExpr
    if (node instanceof MethodCallExpr methodCallExpr) {
      if (methodCallExpr.getScope().map(scope -> isNotObjectCreation(scope) && isVarUsedByName(scope, varName)).orElse(false)) {
        printDebug("MethodCallExpr " + varName);
        return true;
      } else if (methodCallExpr.getArguments().stream().anyMatch(a -> isVarUsedByName(a, varName))) {
        printDebug("MethodCallExpr " + varName);
        return true;
      }
    }
    if (node instanceof ObjectCreationExpr objectCreationExpr) {
      if (objectCreationExpr.getScope().map(scope -> scope.isNameExpr() && isVarUsedByName(scope, varName)).orElse(false)) {
        printDebug("ObjectCreationExpr " + varName);
        return true;
      } else if (objectCreationExpr.getArguments().stream().anyMatch(a -> !a.isObjectCreationExpr() && isVarUsedByName(a, varName))) {
        printDebug("ObjectCreationExpr " + varName);
        return true;
      }
    }

    // - binary operand: if contains NameExpr
    // - logical operand: contains NameExpr
    if (node instanceof BinaryExpr b && isVarUsedByName(b, varName)) {
      var assign = getParentOfType(b, AssignExpr.class);
      if (assign == null || !(assign.getTarget() instanceof NameExpr an && an.getNameAsString().equals(varName))) {
        printDebug("BinaryExpr " + varName);
        return true;
      }
    }
    // - loop argument: WhileStmt/NodeWithCondition, condition: NameExpr
    // - if: IfStmt, condition: NameExpr
    if (node instanceof NodeWithCondition c && isVarUsedByName(c.getCondition(), varName)) {
      printDebug("NodeWithCondition " + varName);
      return true;
    }
    if (node instanceof ForStmt c && (
        c.getCompare().map(co -> isVarUsedByName(co, varName)).orElse(false)
            /*|| c.getInitialization().stream().anyMatch(in -> varUsed(in, varName))*/
            || c.getUpdate().stream().anyMatch(u -> isVarUsedByName(u, varName)))
    ) {
      printDebug("ForStmt " + varName);
      return true;
    }

    // - switch argument: SwitchStmt, selector: NameExpr
    if (node instanceof SwitchStmt sw && isVarUsedByName(sw.getSelector(), varName)) {
      printDebug("SwitchStmt " + varName);
      return true;
    }


    // - array initializer: (ArrayInitiailizerExpr, values: NodeList { NameExpr }
    if (node instanceof ArrayInitializerExpr init && isVarUsedByName(init, varName)) {
      printDebug("ArrayInitializerExpr " + varName);
      return true;
    }

    // - array indexer param: ArrayAccessExpr, index: NameExpr
    if (node instanceof ArrayAccessExpr arrAcc && (isVarUsedByName(arrAcc.getIndex(), varName) || isVarUsedByName(arrAcc.getName(), varName))) {
      printDebug("ArrayAccessExpr " + varName);
      return true;
    }

    // - return stmt value: ReturnStmt, expression contains NameExpr
    if (node instanceof ReturnStmt r && r.getExpression().map(e -> isVarUsedByName(e, varName)).orElse(false)) {
      printDebug("ReturnStmt " + varName);
      return true;
    }

    return false;
  }

  private static boolean isNotObjectCreation(Expression scope) {
    return scope.findAll(ObjectCreationExpr.class).size() == 0;
  }

  private static boolean isVarUsedByName(Node node, String varName) {
    if (node instanceof NameExpr ne) {
      return ne.getNameAsString().equals(varName);
    }

    return node.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(varName));
  }

  private static <T extends Node> T getParentOfType(Node node, Class<T> expectedParent) {
    Node parent = node.getParentNode().orElse(null);
    if (parent == null) {
      return null;
    }

    while (parent != null) {
      if (parent.getClass().equals(expectedParent)) {
        return (T) parent;
      }

      parent = parent.getParentNode().orElse(null);
    }

    return null;
  }

  public static void processObfuscatedStrings(Node cu, final Map<String, BlockScopeImpl> scopesRegistry) {
    cu.findAll(BlockStmt.class).forEach(bs -> {
      boolean wasParentRemoved = true;
      Node curr = bs;
      while (curr != null) {
        if (curr instanceof ClassOrInterfaceDeclaration || curr instanceof EnumDeclaration) {
          wasParentRemoved = false;
          break;
        }

        if (curr.getParentNode().isEmpty()) {
          break;
        }

        curr = curr.getParentNode().get();
      }

      if (wasParentRemoved) {
        return;
      }

      Scope scope = scopesRegistry.get(bs.getRange().get().toString());

      Node root = bs;
      if (root.getParentNode().map(n -> n instanceof DoStmt).orElse(false)) {
        root = root.getParentNode().get();
      }

      final var processingRoot = root;
      processingRoot.findAll(MethodCallExpr.class).forEach(call -> {
        try {
          Node parentBlock = getParentOfType(call, BlockStmt.class);
          if (parentBlock != bs) {

            var callParent = getParentOfType(call, /*BlockStmt.class*/processingRoot.getClass());
            if (!(!(processingRoot instanceof BlockStmt) && callParent == processingRoot && callParent instanceof DoStmt nwb && nwb.getBody() == bs)) {
              return;
            }
//            if (!parentBlock.getParentNode().map(n -> n instanceof NodeWithBody nwb && nwb == processingRoot).orElse(false)) {
//              return;
//            }
          }

          /*var deobfResult = */
          BalkoDeobfuscator.tryDeobfuscate(call, scope);
          /*if (!deobfResult.succeeded()) {
            return;
          }*/
        } catch (Exception exc) {
          System.err.println("Failed to process block. Cause: " + exc.getMessage());
        }
      });
    });
  }
}

