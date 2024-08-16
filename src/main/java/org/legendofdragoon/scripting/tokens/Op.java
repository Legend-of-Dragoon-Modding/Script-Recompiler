package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.OpType;

public class Op extends Entry {
  public final OpType type;
  public final int headerParam;
  public final Param[] params;

  public Op(final int address, final OpType type, final int headerParam, final int paramCount) {
    super(address);
    this.type = type;
    this.headerParam = headerParam;
    this.params = new Param[type == OpType.CALL ? paramCount : type.params.length];
  }

  @Override
  public String toString() {
    return "op " + this.type.name;
  }
}
