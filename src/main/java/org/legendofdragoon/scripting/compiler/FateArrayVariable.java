package org.legendofdragoon.scripting.compiler;

public class FateArrayVariable extends FateValue {
  public final FateValue var;
  public final FateValue index;

  public FateArrayVariable(final FateValue var, final FateValue index) {
    this.var = var;
    this.index = index;
  }

  @Override
  public String toString() {
    if(this.var instanceof final FateVariable var && var.isRel) {
      return "inl[:" + var.name + "[:" + var.name + '[' + this.index + "]]]";
    }

    return this.var.toString() + '[' + this.index + ']' ;
  }
}
