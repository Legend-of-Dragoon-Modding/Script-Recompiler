package org.legendofdragoon.scripting.compiler;

import java.util.List;

public class FateFunctionDefinition {
  public final String name;
  public final List<String> params;
  public final int returns;

  public FateFunctionDefinition(final String name, final List<String> params, final int returns) {
    this.name = name;
    this.params = params;
    this.returns = returns;
  }
}
