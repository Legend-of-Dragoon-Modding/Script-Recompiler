package org.legendofdragoon.scripting.compiler;

public class FateGameVar extends FateValue {
  public final int index;

  public FateGameVar(final int index) {
    this.index = index;
  }

  @Override
  public String toString() {
    return "var[" + this.index + ']';
  }
}
