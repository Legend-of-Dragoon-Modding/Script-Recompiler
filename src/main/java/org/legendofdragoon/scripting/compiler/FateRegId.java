package org.legendofdragoon.scripting.compiler;

public class FateRegId extends FateValue {
  public final String id;

  public FateRegId(final String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "id[" + this.id + ']';
  }
}
