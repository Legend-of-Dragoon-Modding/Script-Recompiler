package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

public class FateLabel extends FateOp {
  public final String label;

  public FateLabel(final String label) {
    super(OpType.NOOP);
    this.label = label;
  }

  @Override
  public String toString() {
    return this.label + ':';
  }
}
