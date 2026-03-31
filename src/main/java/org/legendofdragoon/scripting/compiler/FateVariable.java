package org.legendofdragoon.scripting.compiler;

public class FateVariable extends FateValue {
  public String name;
  public final int length;

  public FateVariable(final String name, final int length) {
    this.name = name;
    this.length = length;
  }

  @Override
  public String toString() {
    return "inl[:" + this.name + ']';
  }
}
