package org.legendofdragoon.scripting;

public class OpParam {
  public final String name;
  public final Direction direction;

  public static OpParam in(final String name) {
    return new OpParam(name, Direction.IN);
  }

  public static OpParam out(final String name) {
    return new OpParam(name, Direction.OUT);
  }

  public static OpParam both(final String name) {
    return new OpParam(name, Direction.BOTH);
  }

  public OpParam(final String name, final Direction direction) {
    this.name = name;
    this.direction = direction;
  }

  public boolean modifiesOutput() {
    return this.direction.modifiesParam();
  }
}
