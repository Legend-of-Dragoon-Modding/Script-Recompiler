package org.legendofdragoon.scripting.compiler;

public class FateImmediate extends FateValue {
  public final String value;

  public FateImmediate(final String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
