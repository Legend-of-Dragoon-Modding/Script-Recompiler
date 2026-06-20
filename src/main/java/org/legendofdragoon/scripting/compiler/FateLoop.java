package org.legendofdragoon.scripting.compiler;

public class FateLoop {
  public final FateLabel startLabel;
  public final FateLabel endLabel;

  public FateLoop(final FateLabel startLabel, final FateLabel endLabel) {
    this.startLabel = startLabel;
    this.endLabel = endLabel;
  }
}
