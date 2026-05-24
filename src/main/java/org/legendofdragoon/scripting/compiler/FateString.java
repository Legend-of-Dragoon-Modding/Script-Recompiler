package org.legendofdragoon.scripting.compiler;

public class FateString extends FateValue {
  public final String value;

  public FateString(final String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "str[" + this.value + ']';
  }
}
