package org.legendofdragoon.scripting.compiler;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.legendofdragoon.scripting.OpType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FatePreprocessor extends FateBaseVisitor<Void> {
  private final List<String> errors;
  private final Map<String, FateFunctionDefinition> functions;
  private int returnCount;

  public FatePreprocessor(final List<String> errors, final Map<String, FateFunctionDefinition> functions) {
    this.errors = errors;
    this.functions = functions;
  }

  @Override
  public Void visitFunction(final FateParser.FunctionContext ctx) {
    final String name = ctx.IDENTIFIER().getText();

    if(ctx.block().statement().getLast().return_() == null) {
      this.errors.add(ctx.getStart().getLine() + ": function \"" + name + "\" missing return");
    }

    if(OpType.byName(name) != null) {
      this.errors.add(ctx.getStart().getLine() + ": function \"" + name + "\" is not a legal name");
    }

    final List<String> params = new ArrayList<>();

    for(final TerminalNode identifier : ctx.identifier_list().IDENTIFIER()) {
      params.add(identifier.getText());
    }

    this.returnCount = 0;
    super.visitFunction(ctx);

    this.functions.put(name, new FateFunctionDefinition(name, params, this.returnCount));
    return null;
  }

  @Override
  public Void visitReturn_(final FateParser.Return_Context ctx) {
    this.returnCount = ctx.expression() != null ? 1 : ctx.expression_list() != null ? ctx.expression_list().expression().size() : 0;
    return super.visitChildren(ctx);
  }
}
