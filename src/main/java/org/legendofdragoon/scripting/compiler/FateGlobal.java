package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

public class FateGlobal extends FateOp {
  private final String name;
  private final String[] values;

  public FateGlobal(final String name, final String... values) {
    super(OpType.NOOP);
    this.name = name;
    this.values = values;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(this.name).append(":\n");

    for(final String value : this.values) {
      builder.append("data ").append(value).append('\n');
    }

    return builder.toString();
  }
}
