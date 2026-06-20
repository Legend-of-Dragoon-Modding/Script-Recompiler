package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

public class FateInclude extends FateOp {
  public final String file;

  public FateInclude(final String file) {
    super(OpType.NOOP);
    this.file = file;
  }

  @Override
  public String toString() {
    return "#include " + this.file;
  }
}
