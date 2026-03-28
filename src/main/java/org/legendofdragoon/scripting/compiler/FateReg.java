package org.legendofdragoon.scripting.compiler;

public class FateReg extends FateValue {
  public final FateValue index;

  public FateReg(final FateValue index) {
    this.index = index;
  }

  @Override
  public String toString() {
    return "reg[" + this.index + ']';
  }
}
