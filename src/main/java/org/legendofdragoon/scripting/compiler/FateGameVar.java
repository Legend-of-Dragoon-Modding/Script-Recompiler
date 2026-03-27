package org.legendofdragoon.scripting.compiler;

public class FateGameVar extends FateValue {
  public final FateValue index;

  public FateGameVar(final FateValue index) {
    this.index = index;
  }

  @Override
  public String toString() {
    return "var[" + this.index + ']';
  }
}
