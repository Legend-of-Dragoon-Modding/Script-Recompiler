package org.legendofdragoon.scripting.compiler;

public class FateEnum extends FateValue {
  public final String value;

  public FateEnum(final String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
