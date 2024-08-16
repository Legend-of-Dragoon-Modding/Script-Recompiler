package org.legendofdragoon.scripting;

public enum Direction {
  IN,
  OUT,
  BOTH,
  ;

  public boolean modifiesParam() {
    return this == OUT || this == BOTH;
  }
}
