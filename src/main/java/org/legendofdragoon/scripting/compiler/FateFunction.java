package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FateFunction extends FateOp {
  public final FateScope scope;
  public final String name;
  private final List<FateVariable> params = new ArrayList<>();
  private final List<FateVariable> variables = new ArrayList<>();
  private final Map<FateParser.Return_Context, List<FateValue>> returns = new HashMap<>();
  private int returnCount;

  public FateFunction(final FateScope scope, final String name) {
    super(OpType.NOOP);
    this.scope = scope;
    this.name = name;
  }

  public FateVariable addParam(final String param) {
    final FateVariable var = this.addVariable(this.scope.addVariable(param));
    this.params.add(var);
    return var;
  }

  public FateVariable getParam(final int index) {
    return this.params.get(index);
  }

  public int getParamCount() {
    return this.params.size();
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

  public void addReturn(final FateParser.Return_Context ctx, final List<FateValue> values) {
    this.returns.put(ctx, values);
    this.returnCount = values.size();
  }

  public Set<FateParser.Return_Context> getReturns() {
    return this.returns.keySet();
  }

  public List<FateValue> getReturnValues(final FateParser.Return_Context ctx) {
    return this.returns.get(ctx);
  }

  public int getReturnCount() {
    return this.returnCount;
  }

  @Override
  public String toString() {
    final Set<String> seenVarNames = new HashSet<>();

    final StringBuilder builder = new StringBuilder("\n");

    for(final FateVariable var : this.variables) {
      // Variables in different scopes can have the same name. We only want to define each variable name once.
      if(!seenVarNames.contains(var.name)) {
        builder.append(var.name).append(':').append('\n');
        builder.repeat("data " + var.value + '\n', var.length);
      }

      seenVarNames.add(var.name);
    }

    builder.append(this.name).append(':');
    return builder.toString();
  }
}
