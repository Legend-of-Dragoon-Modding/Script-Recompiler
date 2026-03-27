package org.legendofdragoon.scripting.compiler;

public class FateStor extends FateValue {
  public final FateValue scriptIndex;
  public final FateValue storIndex;

  public FateStor(final FateValue scriptIndex, final FateValue storIndex) {
    this.scriptIndex = scriptIndex;
    this.storIndex = storIndex;
  }

  @Override
  public String toString() {
    if(this.scriptIndex == null) {
      return "stor[" + this.storIndex + ']';
    }

    return "stor[" + this.scriptIndex + ", " + this.storIndex + ']';
  }
}
