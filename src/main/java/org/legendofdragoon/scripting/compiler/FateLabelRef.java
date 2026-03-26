package org.legendofdragoon.scripting.compiler;

public class FateLabelRef extends FateValue {
  public final String label;

  public FateLabelRef(final String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return "inl[:" + this.label + ']';
  }
}
