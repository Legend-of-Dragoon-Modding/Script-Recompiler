package org.legendofdragoon.scripting.compiler;

public class FateFunctionRef extends FateValue {
  public final String name;

  public FateFunctionRef(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "inl[:" + this.name + ']';
  }
}
