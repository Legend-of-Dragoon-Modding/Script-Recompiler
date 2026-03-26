package org.legendofdragoon.scripting.compiler;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FateContext {
  private final List<String> entrypoints = new ArrayList<>();

  private final Map<String, FateFunction> functions = new HashMap<>();
  private FateFunction currentFunction;

  private final Deque<FateScope> scopeStack = new LinkedList<>();

  private final List<FateOp> ops = new ArrayList<>();

  public void addEntrypoint(final String name) {
    this.entrypoints.add(name);
  }

  public FateFunction startFunction(final String name) {
    this.pushScope();
    final FateFunction function = new FateFunction(this.getCurrentScope(), name);
    this.functions.put(name, function);
    this.currentFunction = function;
    return function;
  }

  public void endFunction() {
    this.currentFunction = null;
    this.popScope();
  }

  public FateFunction getCurrentFunction() {
    return this.currentFunction;
  }

  public FateFunction getFunction(final String name) {
    return this.functions.get(name);
  }

  public void pushScope() {
    this.scopeStack.push(new FateScope(this.scopeStack.peek()));
  }

  public void popScope() {
    this.scopeStack.pop();
  }

  public FateScope getCurrentScope() {
    return this.scopeStack.peek();
  }

  public FateVariable addVariable(final String name) {
    final FateVariable var = this.getCurrentScope().addVariable(name);

    if(this.getCurrentFunction() != null) {
      this.getCurrentFunction().addVariable(var);
    }

    return var;
  }

  public FateVariable getVariable(final String name) {
    return this.getCurrentScope().getVariable(name);
  }

  public boolean isVariableInScope(final String name) {
    return this.getCurrentScope().hasVariable(name);
  }

  public void updateVariableNames() {
    for(final FateFunction function : this.functions.values()) {
      function.updateVariableNames();
    }
  }

  public void addOp(final FateOp op) {
    this.ops.add(op);
  }

  public String compile() {
    final StringBuilder builder = new StringBuilder();

    for(final String entrypoint : this.entrypoints) {
      builder.append("entrypoint :").append(entrypoint).append('\n');
    }

    builder.append('\n');

    for(final FateOp op : this.ops) {
      builder.append(op).append('\n');
    }

    return builder.toString();
  }
}
