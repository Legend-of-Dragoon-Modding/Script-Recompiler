package org.legendofdragoon.scripting.compiler;

public class FateLoop {
  public final String startLabel;
  public final String endLabel;

  public FateLoop(final String startLabel, final String endLabel) {
    this.startLabel = startLabel;
    this.endLabel = endLabel;
  }
}
