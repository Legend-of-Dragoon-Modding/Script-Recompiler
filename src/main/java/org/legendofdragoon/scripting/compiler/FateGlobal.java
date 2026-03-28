package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

public class FateGlobal extends FateOp {
  private final String name;
  private final String value;

  public FateGlobal(final String name, final String value) {
    super(OpType.NOOP);
    this.name = name;
    this.value = value;
  }

  @Override
  public String toString() {
    return this.name + ":\ndata " + this.value + '\n';
  }
}
