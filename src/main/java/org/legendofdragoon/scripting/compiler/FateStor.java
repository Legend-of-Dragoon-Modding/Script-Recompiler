package org.legendofdragoon.scripting.compiler;

public class FateStor extends FateValue {
  public final int index;

  public FateStor(final int index) {
    this.index = index;
  }

  @Override
  public String toString() {
    return "stor[" + this.index + ']';
  }
}
