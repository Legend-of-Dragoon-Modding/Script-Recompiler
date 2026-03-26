package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FateFunction extends FateOp {
  public final FateScope scope;
  public final String name;
  private final List<FateVariable> variables = new ArrayList<>();
  private final Map<FateParser.ReturnContext, List<FateValue>> returns = new HashMap<>();
  private int returnCount;

  public FateFunction(final FateScope scope, final String name) {
    super(OpType.NOOP);
    this.scope = scope;
    this.name = name;
  }

  public FateVariable addParam(final String param) {
    return this.addVariable(this.scope.addVariable(param));
  }

  public FateVariable addVariable(final FateVariable var) {
    this.variables.add(var);
    return var;
  }

  public void updateVariableNames() {
    for(final FateVariable var : this.variables) {
      var.name = this.name + '_' + var.name;
    }
  }

  public void addReturn(final FateParser.ReturnContext ctx, final List<FateValue> values) {
    this.returns.put(ctx, values);
    this.returnCount = values.size();
  }

  public Set<FateParser.ReturnContext> getReturns() {
    return this.returns.keySet();
  }

  public List<FateValue> getReturnValues(final FateParser.ReturnContext ctx) {
    return this.returns.get(ctx);
  }

  public int getReturnCount() {
    return this.returnCount;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder("\n");

    for(final FateVariable var : this.variables) {
      builder
        .append(var.name).append(':').append('\n')
        .append("data 0\n");
    }

    builder.append(this.name).append(':');
    return builder.toString();
  }
}
