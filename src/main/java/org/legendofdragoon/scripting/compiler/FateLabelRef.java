package org.legendofdragoon.scripting.compiler;

public class FateLabelRef extends FateValue {
  public final FateLabel label;

  public FateLabelRef(final FateLabel label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return "inl[:" + this.label.label + ']';
  }
}
