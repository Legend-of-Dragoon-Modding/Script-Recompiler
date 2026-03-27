package org.legendofdragoon.scripting.compiler;

public class FateGameVarArray extends FateValue {
  public final FateValue index1;
  public final FateValue index2;

  public FateGameVarArray(final FateValue index1, final FateValue index2) {
    this.index1 = index1;
    this.index2 = index2;
  }

  @Override
  public String toString() {
    return "var[" + this.index1 + "][" + this.index2 + ']';
  }
}
