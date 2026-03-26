package org.legendofdragoon.scripting.compiler;

public class FateVariable extends FateValue {
  public String name;

  public FateVariable(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "inl[:" + this.name + ']';
  }
}
