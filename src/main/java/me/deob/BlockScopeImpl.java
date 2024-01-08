package me.deob;

import com.github.javaparser.Position;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.*;

public class BlockScopeImpl implements Scope {
  private final BlockStmt blockStmt;

  private BlockScopeImpl parentScope;
  private final Map<String, VarDecl> varDecls = new LinkedHashMap<>();
  private final Map<String, LinkedList<VarMutation>> varMutations = new LinkedHashMap<>();

  public BlockScopeImpl(BlockStmt blockStmt, final Map<String, BlockScopeImpl> scopesRegistry) {
    this.blockStmt = blockStmt;

    initializeParentScope(scopesRegistry);
    initializeRootScope();

    populateVarsFromParentInitBlocks(blockStmt);

    populateVarDecls(blockStmt.getStatements());
    scopesRegistry.put(blockStmt.getRange().get().toString(), this);
  }

  private void populateVarsFromParentInitBlocks(BlockStmt blockStmt) {
    if (blockStmt.getParentNode().map(n -> n instanceof ForStmt).orElse(false)) {
      var forStmt =  (ForStmt) blockStmt.getParentNode().get();
      for (Expression expression : forStmt.getInitialization()) {
        populateVarDeclsFromExpr(expression);
      }
    }
  }

  private void initializeRootScope() {
    if (parentScope == null) {

      initClassScope();
      initContainingMethodScope();
    }
  }

  private void initClassScope() {
    ClassOrInterfaceDeclaration cls = null;
    Node curr = blockStmt;
    while (curr != null) {
      if (curr instanceof ClassOrInterfaceDeclaration c) {
        cls = c;
        break;
      }

      if (curr instanceof EnumDeclaration ed) {
        final var entries = ed.getEntries();
        for (EnumConstantDeclaration entry : entries) {
          addDecl(VarDecl.field(entry));
        }

        return;
      }
      if (curr.getParentNode().isEmpty()) {
        break;
      }

      curr = curr.getParentNode().get();
    }

    if (cls == null) {
      throw new IllegalStateException("Can't find class/interface decl");
    }

    for (FieldDeclaration field : cls.getFields()) {
      for (VariableDeclarator variable : field.getVariables()) {
        addDecl(VarDecl.field(variable));
      }
    }
  }

  private void initContainingMethodScope() {
    CallableDeclaration<?> mdecl = null;
    Node curr = blockStmt;
    while (curr != null) {
      if (curr instanceof InitializerDeclaration) {
        return;
      }

      if (curr instanceof CallableDeclaration<?> mcur) {
        mdecl = mcur;
        break;
      }
      if (curr.getParentNode().isEmpty()) {
        break;
      }

      curr = curr.getParentNode().get();
    }

    if (mdecl == null) {
      throw new IllegalStateException("Can't find method/ctor decl");
    }

    for (Parameter parameter : mdecl.getParameters()) {
      addDecl(VarDecl.methodParam(parameter));
    }
  }

  public void populateScopeMutations() {
    varMutations.clear();
    populateInitialMutations();
    for (int i = 0; i < blockStmt.getStatements().size(); i++) {
      Statement s = blockStmt.getStatements().get(i);
      if (!s.isExpressionStmt()) {
        continue;
      }

      if (!(s.asExpressionStmt().getExpression() instanceof AssignExpr assign)) {
        continue;
      }

      final Expression target = assign.getTarget();
      if (!(target instanceof NameExpr nexpr)) {
        continue;
      }

      var decl = getVarDeclaration(nexpr.getNameAsString());
//      var list = varMutations.computeIfAbsent(nexpr.getNameAsString(), n -> new LinkedList<>());
      Object value;

      LiteralStringValueExpr literalExpr = null;
      final Expression assignValue = assign.getValue();

      try {
        if (assignValue instanceof LiteralStringValueExpr l) {
          value = l.getValue();
        } else if (assignValue instanceof BinaryExpr be) {
          try {
            literalExpr = Util.foldBinaryExprToLiteral(this, assign.getRange().get().begin, be);
          } catch (RuntimeException x) {
            addMutation(VarMutation.complex(decl, assignValue));
            continue;
          }

          assign.setValue(literalExpr);
          i--;
          continue;
        } else if (assignValue instanceof NameExpr be) {
          final var before = assign.getRange().get().begin;
          VarMutation usedInAssignVar = getLastMutation(be.getNameAsString(), before);
          if (usedInAssignVar == null) {
            try {
              var assignDecl = getVarDeclaration(be.getNameAsString());
              if (assignDecl == null) {
                throw new RuntimeException("For now will throw, can't find: " + be.getNameAsString() + ", line: " + be.getRange().get().begin);
              }

              addMutation(this.getLastMutation(decl.getName()) != null
                  ? VarMutation.of(decl, assign, assignDecl.getInitialValue())
                  : VarMutation.initial(decl, assign, assignDecl.getInitialValue()));
            } catch (IllegalStateException ignore) {
              addMutation(VarMutation.complex(decl, assignValue));
            }
            continue;
          }
          if (!usedInAssignVar.isSimpleInitialized()) {
            addMutation(usedInAssignVar.mutate(assignValue));
            continue;
          }

          literalExpr = Util.convertNameRefToLiteral(this, before, be);
          if (literalExpr == null) {
            throw new RuntimeException("Unsupported expr: " + assignValue);
          }

          assign.setValue(literalExpr);
          i--;
          continue;
        } else {
          if (assignValue instanceof MethodCallExpr call) {
            for (var arg : call.getArguments()) {
              if (arg instanceof MethodCallExpr argCall) {
                BalkoDeobfuscator.tryDeobfuscate(argCall, this);
              }
            }
            var r = BalkoDeobfuscator.tryDeobfuscate(call, this);
            if (r.succeeded()) {
              addMutation(VarMutation.of(decl, assignValue, new Value(r.result())));
              continue;
            }
          }
          addMutation(this.getLastMutation(decl.getName()) != null
              ? VarMutation.complex(decl, assignValue)
              : VarMutation.initialComplex(decl, assignValue));
          continue;
//        throw new RuntimeException("Unsupported expr: " + assignValue);
//        addMutation(decl, new VarMutation(null, assignValue));
//        continue;
        }
      } catch (MutationHasNoLiteralValueException e) {
        addMutation(VarMutation.complex(decl, assignValue));
        continue;
      }
      Object calculatedValue;
      final TokenRange assignValueTokenRange = assignValue.getTokenRange().get();
      if (assignValue.isIntegerLiteralExpr()) {
        if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
          calculatedValue = value;
        } else {
          var operand = IntUtil.safeParse(value.toString());
          var last = getLastMutation(decl.getName(), assign.getRange().get().begin);
          if (!last.isSimpleInitialized()) {
            addMutation(VarMutation.complex(decl, assignValue));
            continue;
          }
          var lastValue = IntUtil.safeParse(last.value().toString()); // todo: why here can be a String?
          calculatedValue = switch (assign.getOperator()) {
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

        assign.setValue(new IntegerLiteralExpr(assignValueTokenRange, calculatedValue.toString()));
      } else if (assignValue.isLongLiteralExpr()) {
        var operand = assignValue.asLongLiteralExpr().asNumber().longValue();
        if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
          calculatedValue = operand;
        } else {
          var last = getLastMutation(decl.getName(), assign.getRange().get().begin);
          if (!last.isSimpleInitialized()) {
            addMutation(VarMutation.complex(decl, assignValue));
            continue;
          }
          var lastValue = Long.parseLong(last.value().toString()); // todo: why here can be a String?
          calculatedValue = switch (assign.getOperator()) {
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

        assign.setValue(new IntegerLiteralExpr(assignValueTokenRange, calculatedValue.toString()));
      } else if (assignValue.isDoubleLiteralExpr()) {
        var operand = Double.parseDouble(value.toString());
        if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
          calculatedValue = operand;
        } else {
          var last = getLastMutation(decl.getName(), assign.getRange().get().begin);
          if (!last.isSimpleInitialized()) {
            addMutation(VarMutation.complex(decl, assignValue));
            continue;
          }
          var lastValue = Double.parseDouble(last.value().toString()); // todo: why here can be a String?
          calculatedValue = switch (assign.getOperator()) {
            case ASSIGN -> operand;
            case PLUS -> lastValue + operand;
            case MINUS -> lastValue - operand;
            case MULTIPLY -> lastValue * operand;
            case DIVIDE -> lastValue / operand;
            case BINARY_AND -> (long) lastValue & (long) operand;
            case BINARY_OR -> (long) lastValue | (long) operand;
            case XOR -> (long) lastValue ^ (long) operand;
            case REMAINDER -> lastValue % operand;
            case LEFT_SHIFT -> (long) lastValue << (long) operand;
            case SIGNED_RIGHT_SHIFT -> (long) lastValue >> (long) operand;
            case UNSIGNED_RIGHT_SHIFT -> (long) lastValue >>> (long) operand;
          };
        }

        assign.setValue(new DoubleLiteralExpr(assignValueTokenRange, calculatedValue.toString()));
      } else if (assignValue.isStringLiteralExpr()) {
        var operand = value.toString();
        if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
          calculatedValue = operand;
        } else {
          var last = getLastMutation(decl.getName(), assign.getRange().get().begin);
          // todo: eliminate duplicates
          if (!last.isSimpleInitialized()) {
            addMutation(VarMutation.complex(decl, assignValue));
            continue;
          }
          var lastValue = (String) last.value();
          calculatedValue = switch (assign.getOperator()) {
            case ASSIGN -> operand;
            case PLUS -> lastValue + operand;
            case MINUS -> throw new IllegalStateException();
            case MULTIPLY -> throw new IllegalStateException();
            case DIVIDE -> throw new IllegalStateException();
            case BINARY_AND -> throw new IllegalStateException();
            case BINARY_OR -> throw new IllegalStateException();
            case XOR -> throw new IllegalStateException();
            case REMAINDER -> throw new IllegalStateException();
            case LEFT_SHIFT -> throw new IllegalStateException();
            case SIGNED_RIGHT_SHIFT -> throw new IllegalStateException();
            case UNSIGNED_RIGHT_SHIFT -> throw new IllegalStateException();
          };
        }

        assign.setValue(new StringLiteralExpr(assignValueTokenRange, calculatedValue.toString()));
      }  else if (assignValue.isCharLiteralExpr()) {
        var operand = value.toString();
        if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
          calculatedValue = operand;
        } else {
          throw new RuntimeException("Operator for char not supported: " + assign.getOperator());
        }

        assign.setValue(new StringLiteralExpr(assignValueTokenRange, calculatedValue.toString()));
      } else {
        throw new IllegalStateException();
      }

      assign.setOperator(AssignExpr.Operator.ASSIGN);
//      if (calculatedValue instanceof String) {
//        calculatedValue = RawUtf8BytesParserUtil.parseFromExprOrDefault(assignValue, calculatedValue);
//      }

      addMutation(
          this.getLastMutation(decl.getName()) != null
              ? VarMutation.of(decl, assignValue, new Value(calculatedValue))
              : VarMutation.initial(decl, assignValue, new Value(calculatedValue)));
    }
  }

  @Override
  public VarDecl getVarDeclaration(String varName) {
    var decl = varDecls.get(varName);
    if (decl == null && parentScope != null) {
      return parentScope.getVarDeclaration(varName);
    }

    if (decl == null) {
      throw new IllegalStateException("Can't find var decl: " + varName);
    }

    return decl;
  }

  @Override
  public VarMutation getLastMutation(String varName) {
    return getLastMutation(varName, new Position(Integer.MAX_VALUE, Integer.MAX_VALUE));
  }

  @Override
  public VarMutation getLastMutation(String varName, Position before) {
    var mutations = varMutations.get(varName);
    VarMutation mutation = null;
    if (mutations != null) {
      final Iterator<VarMutation> descIter = mutations.descendingIterator();
      while (descIter.hasNext()) {
        var curr = descIter.next();
        if (curr.getBegin().isBefore(before)) {
          mutation = curr;
          break;
        }
      }
    }
    if (mutation == null && parentScope != null) {
      return parentScope.getLastMutation(varName, before);
    }

    return mutation;
  }

  @Override
  public void addDecl(VarDecl decl) {
    if (!tryAddDecl(decl)) {
      throw new IllegalStateException("Variable already exists: " + decl.getName());
    }
  }

  public boolean tryAddDecl(VarDecl decl) {
    if (varDecls.containsKey(decl.getName())) {
      return false;
    }

    varDecls.put(decl.getName(), decl);
    return true;
  }

  @Override
  public void addMutation(VarMutation mutation) {
    if (mutation == null) {
      throw new IllegalArgumentException("mutation is null");
    }

    var list = varMutations.computeIfAbsent(mutation.decl().getName(), x -> new LinkedList<>());
    if (getLastMutation(mutation.decl().getName()) == null && !mutation.isInitial()) {
      throw new RuntimeException("First mutation must be initial");
    }

    if (mutation.isInitial()) {
      for (var item : list) {
        if (item.isInitial()) {
          throw new RuntimeException("Initial mutation already exists");
        }
      }
    }/* else {
      final var last = getLastMutation(mutation.decl().getName());
      if (last == null || !last.isInitial()) {
        throw new RuntimeException("No initial mutation found");
      }
    }*/

    list.addLast(mutation);
  }

  @Override
  public boolean containsVar(String name) {
    return varDecls.containsKey(name) || parentScope != null && parentScope.containsVar(name);
  }


  private void initializeParentScope(final Map<String, BlockScopeImpl> scopesRegistry) {
    Node curr = blockStmt;
    while (curr.getParentNode().isPresent()) {
      final var n = curr.getParentNode().get();
      BlockStmt block;

      if (n instanceof BlockStmt b) {
        block = b;
      } else if (n instanceof LabeledStmt l && l.getStatement() instanceof BlockStmt b) {
        block = b;
      } else {
        curr = n;
        continue;
      }

      if (block == curr) {
        curr = n;
        continue;
      }

      final var key = block.getRange().get().toString();
      parentScope = scopesRegistry.get(key);
      if (parentScope != null) {
        break;
      }
    }
  }

  private void populateVarDecls(List<Statement> statements) {
    for (var s : statements) {
      populateVarDeclsFromStatement(s);
    }
//    varMutations.clear();
  }

  private void populateVarDeclsFromStatement(Statement s) {
    if (s.isExpressionStmt()) {
      var expr = (ExpressionStmt) s;
      /*if (expr.getExpression() instanceof AssignExpr assign && assign.getTarget() instanceof NameExpr n) {
        Expression assignValue = assign.getValue();

        if (assignValue instanceof BinaryExpr be) {
          varMutations.clear();
          populateInitialMutations(); // todo: think on better approach
          assignValue = Util.foldBinaryExprToLiteral(this, assign.getRange().get().begin, be);
        }

        if (assignValue instanceof LiteralExpr le) {
          Object value = Util.getValueFromLiteral(le);

          final String name = n.getNameAsString();
          final VarDecl decl = new VarDecl(name, null, assignValue);
          if (tryAddDecl(decl)) {
            decl.setInitialValue(value);
          }
        } else {
          if (assignValue instanceof ThisExpr thisExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, thisExpr));
            continue;
          }
          if (assignValue instanceof ObjectCreationExpr objectCreationExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, objectCreationExpr));
            continue;
          }
          if (assignValue instanceof MethodCallExpr methodCallExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, methodCallExpr));
            continue;
          }
          if (assignValue instanceof NameExpr nameExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, nameExpr));
            continue;
          }
          if (assignValue instanceof FieldAccessExpr fieldAccessExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, fieldAccessExpr));
            continue;
          }
          if (assignValue instanceof ConditionalExpr conditionalExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, conditionalExpr));
            continue;
          }
          if (assignValue instanceof ArrayCreationExpr arrayCreationExpr) {
            tryAddDecl(new VarDecl(n.getNameAsString(), null, arrayCreationExpr));
            continue;
          }
          throw new RuntimeException("not supported init on assignment: " + assign);
        }
      } else*/
      populateVarDeclsFromExpr(expr.getExpression());
    }
  }

  private void populateVarDeclsFromExpr(Expression expr) {
    if (expr instanceof VariableDeclarationExpr varDecl) {
      for (var v : varDecl.getVariables()) {
        Expression init = null;
        if (v.getInitializer().isPresent()) {
          init = v.getInitializer().get();
        }

        if (init instanceof LiteralExpr le) {
          Object value = Util.getValueFromLiteral(le);
          addDecl(VarDecl.literal(v, value));
        } else if (init == null) {
          addDecl(VarDecl.uninitialized(v));
        } else {
          addDecl(VarDecl.complex(v));
        }
      }
    }
  }

//  private Set<VarDecl> getKnownVars() {
//    var set = new HashSet<>(varDecls.values());
//    if (parentScope != null) {
//      for (var d : parentScope.getKnownVars()) {
//        if (d.getRange().isBefore(blockStmt.getRange().get().begin)) {
//          set.add(d);
//        }
//      }
//    }
//
//    return set;
//  }

  private void populateInitialMutations() {
    Object[] varKeys = varDecls.values().toArray();
    for (int i = 0; i < varKeys.length; i++) {
      var decl = (VarDecl) varKeys[i];
      Expression init = decl.init();
      try {
        if (init instanceof BinaryExpr be) {
          var folded = Util.foldBinaryExprToLiteral(this, be.getRange().get().begin, be);
          decl.setInitializer(folded);
          decl.setInitialValue(folded.getValue());
          i--;
          continue;
        } else if (init instanceof UnaryExpr unary) {
          final Expression valueExpr = unary.getExpression();
          final TokenRange tokenRange = unary.getTokenRange().get();
          var folded = Util.foldUnaryExpr(this, unary, tokenRange);
          decl.setInitializer(folded);
          decl.setInitialValue(folded.getValue());
          i--;
          continue;
        } else if (init instanceof CastExpr castExpr) {
          if (castExpr.getExpression() instanceof NullLiteralExpr nl) {
            decl.setInitializer(nl);
            decl.setInitialValue(null);
            i--;
            continue;
          } else if (castExpr.getExpression() instanceof LiteralStringValueExpr sl) {
            decl.setInitializer(sl);
            decl.setInitialValue(sl.getValue());
            i--;
            continue;
          } else {
            addMutation(VarMutation.initialComplex(decl, init));
            continue;
          }
        }
      } catch (RuntimeException exc) {
        var mutation = decl.hasInitialValue()
            ? VarMutation.complex(decl, init)
            : VarMutation.initialComplex(decl, init);
        addMutation(mutation);
        continue;
      } catch (MutationHasNoLiteralValueException exc) {
        addMutation(exc.getMutation().mutate(init));
        continue;
      }

      final var initialMutation = decl.hasInitialValue()
          ? VarMutation.initial(decl, init, decl.getInitialValue())
          : VarMutation.initialComplex(decl, init);

      addMutation(initialMutation);
    }
  }
}
