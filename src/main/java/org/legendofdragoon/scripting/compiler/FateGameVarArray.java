package org.legendofdragoon.scripting.compiler;

public class FateGameVarArray extends FateValue {
  public final int index1;
  public final int index2;

  public FateGameVarArray(final int index1, final int index2) {
    this.index1 = index1;
    this.index2 = index2;
  }

  @Override
  public String toString() {
    return "var[" + this.index1 + "][" + this.index2 + ']';
  }
}
