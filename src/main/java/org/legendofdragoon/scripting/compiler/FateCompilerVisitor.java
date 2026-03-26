package org.legendofdragoon.scripting.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.legendofdragoon.scripting.OpType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FateCompilerVisitor extends AbstractParseTreeVisitor<FateValue> implements FateVisitor<FateValue> {
  private final List<String> errors;
  private final Map<String, FateFunctionDefinition> functions;

  private final FateContext fate;
  private int labelIndex;
  private int exprVarIndex;

  public FateCompilerVisitor(final FateContext fate, final List<String> errors, final Map<String, FateFunctionDefinition> functions) {
    this.fate = fate;
    this.errors = errors;
    this.functions = functions;
  }

  private FateVariable getVar(final ParserRuleContext ctx, final TerminalNode identifier) {
    final String var = identifier.getText();

    if(!this.fate.isVariableInScope(var)) {
      this.errors.add(ctx.getStart().getLine() + ": variable \"" + var + "\" is not defined in the current scope");
    }

    return this.fate.getVariable(var);
  }

  private String getLabel() {
    final String var = "LABEL_" + this.labelIndex;
    this.labelIndex++;
    return var;
  }

  private FateVariable getExprVar() {
    final String var = "_expr_" + this.exprVarIndex;
    this.exprVarIndex++;
    return this.fate.addVariable(var);
  }

  @Override
  public FateValue visitBody(final FateParser.BodyContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitEntrypoint(final FateParser.EntrypointContext ctx) {
    final String entrypoint = ctx.IDENTIFIER().getText();

    if(!this.functions.containsKey(entrypoint)) {
      this.errors.add(ctx.getStart().getLine() + ": entrypoint function " + entrypoint + " not found");
    }

    this.fate.addEntrypoint(entrypoint);
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitFunction(final FateParser.FunctionContext ctx) {
    final String name = ctx.IDENTIFIER().toString();

    final FateFunction function = this.fate.startFunction(name);
    this.fate.addOp(function);

    for(int i = 0; i < ctx.identifier_list().IDENTIFIER().size(); i++) {
      final String param = ctx.identifier_list().IDENTIFIER(i).getText();
      final FateVariable var = this.fate.getCurrentFunction().addParam(param);
      this.fate.addOp(new FateOp(OpType.POP, var));
    }

    this.visitBlock(ctx.block());

    // Make sure all paths return the same number of values
    final long distinctReturnSizes = this.fate.getCurrentFunction().getReturns().stream()
      .map(this.fate.getCurrentFunction()::getReturnValues)
      .map(List::size)
      .distinct()
      .count();

    if(distinctReturnSizes != 1) {
      this.errors.add(ctx.getStart().getLine() + ": function \"" + name + "\" return lists are not the same size");
    }

    this.fate.endFunction();
    return null;
  }

  @Override
  public FateValue visitIf(final FateParser.IfContext ctx) {
    final String label = this.getLabel();

    final FateValue expr = this.visitExpression(ctx.expression());
    this.fate.addOp(new FateOp(OpType.JMP_CMP, new FateImmediate("=="), new FateImmediate("0"), expr, new FateLabelRef(label)));
    this.visitBlock(ctx.block());
    this.fate.addOp(new FateLabel(label));
    return null;
  }

  @Override
  public FateValue visitWhile(final FateParser.WhileContext ctx) {
    final String label1 = this.getLabel();
    final String label2 = this.getLabel();

    this.fate.addOp(new FateLabel(label1));
    final FateValue expr = this.visitExpression(ctx.expression());
    this.fate.addOp(new FateOp(OpType.JMP_CMP, new FateImmediate("=="), new FateImmediate("0"), expr, new FateLabelRef(label2)));
    this.visitBlock(ctx.block());
    this.fate.addOp(new FateOp(OpType.JMP, new FateLabelRef(label1)));
    this.fate.addOp(new FateLabel(label2));
    return null;
  }

  @Override
  public FateValue visitControl(final FateParser.ControlContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitBlock(final FateParser.BlockContext ctx) {
    this.fate.pushScope();
    this.visitChildren(ctx);
    this.fate.popScope();
    return null;
  }

  @Override
  public FateValue visitStatement(final FateParser.StatementContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitPostfix(final FateParser.PostfixContext ctx) {
    final FateVariable var = this.getVar(ctx, ctx.IDENTIFIER());

    if(ctx.postfix_op().INCR() != null) {
      this.fate.addOp(new FateOp(OpType.INCR, var));
    } else if(ctx.postfix_op().DECR() != null) {
      this.fate.addOp(new FateOp(OpType.DECR, var));
    }

    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitAssignment(final FateParser.AssignmentContext ctx) {
    final List<TerminalNode> identifiers = new ArrayList<>();

    if(ctx.IDENTIFIER() != null) {
      identifiers.add(ctx.IDENTIFIER());
    } else if(ctx.identifier_list() != null) {
      identifiers.addAll(ctx.identifier_list().IDENTIFIER());
    }

    final List<FateVariable> vars = new ArrayList<>();

    for(final TerminalNode identifier : identifiers) {
      vars.add(this.getVar(ctx, identifier));
    }

    this.emitAssignment(ctx.expression(), vars);
    return null;
  }

  @Override
  public FateValue visitDeclaration(final FateParser.DeclarationContext ctx) {
    final List<TerminalNode> identifiers = new ArrayList<>();

    if(ctx.IDENTIFIER() != null) {
      identifiers.add(ctx.IDENTIFIER());
    } else if(ctx.identifier_list() != null) {
      identifiers.addAll(ctx.identifier_list().IDENTIFIER());
    }

    final List<FateVariable> vars = new ArrayList<>();

    for(final TerminalNode identifier : identifiers) {
      final String name = identifier.getText();

      if(this.fate.isVariableInScope(name)) {
        this.errors.add(ctx.getStart().getLine() + ": variable " + name + " already defined in scope");
      }

      vars.add(this.fate.addVariable(name));
    }

    this.emitAssignment(ctx.expression(), vars);
    return null;
  }

  private void emitAssignment(final FateParser.ExpressionContext ctx, final List<FateVariable> vars) {
    final FateValue expr = this.visitExpression(ctx);

    for(int i = 0; i < vars.size(); i++) {
      final FateVariable var = vars.get(i);

      final FateValue src;
      if(expr instanceof final FateValueList list) {
        src = list.values.get(i);
      } else {
        src = expr;
      }

      this.fate.addOp(new FateOp(OpType.MOV, src, var));
    }
  }

  @Override
  public FateFunctionRef visitCall(final FateParser.CallContext ctx) {
    if(ctx.SCOPE() != null) {
      //TODO
      System.out.println("TODO IMPLEMENT ENGINE CALL " + ctx.getText());
      return null;
    }

    // Push params
    for(final FateParser.ExpressionContext exprCtx : ctx.expression_list().expression()) {
      final FateValue value = this.visitExpression(exprCtx);
      this.fate.addOp(new FateOp(OpType.PUSH, value));
    }

    final String name = ctx.IDENTIFIER(0).getText();
    final FateFunctionRef ret = new FateFunctionRef(name);
    this.fate.addOp(new FateOp(OpType.GOSUB, ret));
    return ret;
  }

  @Override
  public FateValue visitReturn(final FateParser.ReturnContext ctx) {
    final List<FateValue> returns = new ArrayList<>();

    if(ctx.expression_list() != null) {
      for(final FateParser.ExpressionContext expressionCtx : ctx.expression_list().expression()) {
        for(final FateValue ret : this.visitExpression(expressionCtx)) {
          returns.add(ret);
        }
      }
    } else if(ctx.expression() != null) {
      for(final FateValue ret : this.visitExpression(ctx.expression())) {
        returns.add(ret);
      }
    }

    final FateFunctionDefinition def = this.functions.get(this.fate.getCurrentFunction().name);

    if(def.returns != returns.size()) {
      this.errors.add(ctx.getStart().getLine() + ": return expected " + def.returns + " parameter(s), got " + returns.size() + " instead");
    }

    this.fate.getCurrentFunction().addReturn(ctx, returns);

    for(final FateValue value : returns) {
      this.fate.addOp(new FateOp(OpType.PUSH, value));
    }

    this.fate.addOp(new FateOp(OpType.RETURN));
    return null;
  }

  @Override
  public FateValue visitIdentifier_list(final FateParser.Identifier_listContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitExpression_list(final FateParser.Expression_listContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitValue_list(final FateParser.Value_listContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitExpression(FateParser.ExpressionContext ctx) {
    // Parenthesized
    while(ctx.LPAREN() != null) {
      ctx = ctx.expression(0);
    }

    // Values
    if(ctx.value() != null) {
      if(ctx.value().IDENTIFIER() != null) {
        return this.getVar(ctx, ctx.value().IDENTIFIER());
      }

      if(ctx.value().NUMBER() != null) {
        return new FateImmediate(ctx.value().NUMBER().getText());
      }

      if(ctx.value().call() != null) {
        final FateFunctionRef ret = this.visitCall(ctx.value().call());
        final FateFunctionDefinition def = this.functions.get(ret.name);
        final FateValueList returns = new FateValueList();

        for(int i = 0; i < def.returns; i++) {
          final FateVariable out = this.getExprVar();
          returns.values.add(out);
          this.fate.addOp(new FateOp(OpType.POP, out));
        }

        return returns.values.isEmpty() ? null : returns.values.size() == 1 ? returns.values.getFirst() : returns;
      }
    }

    // Simple unary
    if(ctx.ADD() != null) {
      final FateVariable out = this.getExprVar();
      final FateValue expr = this.visitExpression(ctx.expression(0));
      this.fate.addOp(new FateOp(OpType.MOV, expr, out));
      return out;
    }

    if(ctx.SUB() != null) {
      final FateVariable out = this.getExprVar();
      final FateValue expr = this.visitExpression(ctx.expression(0));
      this.fate.addOp(new FateOp(OpType.MOV, expr, out));
      this.fate.addOp(new FateOp(OpType.NEG, out));
      return out;
    }

    if(ctx.NOTC() != null) {
      final FateVariable out = this.getExprVar();
      final FateValue expr = this.visitExpression(ctx.expression(0));

      // logical not bithack
      this.fate.addOp(new FateOp(OpType.MOV, expr, out));
      this.fate.addOp(new FateOp(OpType.NOT, out));
      this.fate.addOp(new FateOp(OpType.INCR, out));
      this.fate.addOp(new FateOp(OpType.OR, expr, out));
      this.fate.addOp(new FateOp(OpType.SHR, out));
      this.fate.addOp(new FateOp(OpType.INCR, out));
    }

    if(ctx.NOTA() != null) {
      final FateVariable out = this.getExprVar();
      final FateValue expr = this.visitExpression(ctx.expression(0));
      this.fate.addOp(new FateOp(OpType.MOV, expr, out));
      this.fate.addOp(new FateOp(OpType.NOT, out));
      return out;
    }

    // Comparisons
    if(ctx.comp_op() != null) {
      if(ctx.comp_op().EQ() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("=="), a, b, out));
        return out;
      }

      if(ctx.comp_op().NEQ() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("!="), a, b, out));
        return out;
      }

      if(ctx.comp_op().GT() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate(">"), a, b, out));
        return out;
      }

      if(ctx.comp_op().LT() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("<"), a, b, out));
        return out;
      }

      if(ctx.comp_op().GTE() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate(">="), a, b, out));
        return out;
      }

      if(ctx.comp_op().LTE() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("<="), a, b, out));
        return out;
      }

      if(ctx.comp_op().ANDC() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("&&"), a, b, out));
        return out;
      }

      if(ctx.comp_op().ORC() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.CMP, new FateImmediate("||"), a, b, out));
        return out;
      }

      this.errors.add(ctx.getStart().getLine() + ": unimplemented op " + ctx.getText());
    }

    // Multiplicative
    if(ctx.mult_op() != null) {
      if(ctx.mult_op().MUL() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.MUL, b, out));
        return out;
      }

      if(ctx.mult_op().DIV() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.DIV, b, out));
        return out;
      }

      if(ctx.mult_op().MOD() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.MOD, b, out));
        return out;
      }

      this.errors.add(ctx.getStart().getLine() + ": unimplemented op " + ctx.getText());
    }

    // Additive
    if(ctx.add_op() != null) {
      if(ctx.add_op().ADD() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.ADD, b, out));
        return out;
      }

      if(ctx.add_op().SUB() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.SUB, b, out));
        return out;
      }

      this.errors.add(ctx.getStart().getLine() + ": unimplemented op " + ctx.getText());
    }

    // Shifts
    if(ctx.shift_op() != null) {
      if(ctx.shift_op().SHR() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.SHR, b, out));
        return out;
      }

      if(ctx.shift_op().SHL() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.SHL, b, out));
        return out;
      }

      this.errors.add(ctx.getStart().getLine() + ": unimplemented op " + ctx.getText());
    }

    // Bitwise
    if(ctx.bit_op() != null) {
      if(ctx.bit_op().ANDA() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.AND, b, out));
        return out;
      }

      if(ctx.bit_op().ORA() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.OR, b, out));
        return out;
      }

      if(ctx.bit_op().XORA() != null) {
        final FateVariable out = this.getExprVar();
        final FateValue a = this.visitExpression(ctx.expression(0));
        final FateValue b = this.visitExpression(ctx.expression(1));
        this.fate.addOp(new FateOp(OpType.MOV, a, out));
        this.fate.addOp(new FateOp(OpType.XOR, b, out));
        return out;
      }

      this.errors.add(ctx.getStart().getLine() + ": unimplemented op " + ctx.getText());
    }

    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitValue(final FateParser.ValueContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitPostfix_op(final FateParser.Postfix_opContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitComp_op(final FateParser.Comp_opContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitMult_op(final FateParser.Mult_opContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitAdd_op(final FateParser.Add_opContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitShift_op(final FateParser.Shift_opContext ctx) {
    return this.visitChildren(ctx);
  }

  @Override
  public FateValue visitBit_op(final FateParser.Bit_opContext ctx) {
    return this.visitChildren(ctx);
  }
}
