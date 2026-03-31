package org.legendofdragoon.scripting.compiler;

import java.util.HashMap;
import java.util.Map;

public class FateScope {
  private final Map<String, FateVariable> variables = new HashMap<>();

  public FateScope(final FateScope current) {
    if(current != null) {
      this.variables.putAll(current.variables);
    }
  }

  public FateVariable addVariable(final String name) {
    return this.addVariable(name, 1);
  }

  public FateVariable addVariable(final String name, final int length) {
    return this.variables.computeIfAbsent(name, key -> new FateVariable(key, length));
  }

  public FateVariable getVariable(final String name) {
    return this.variables.get(name);
  }

  public boolean hasVariable(final String name) {
    return this.variables.containsKey(name);
  }
}
