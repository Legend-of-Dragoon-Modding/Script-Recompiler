package org.legendofdragoon.scripting.tokens;

import java.util.Arrays;

public class PointerTable extends Entry {
  /** Used to output a data entry if this table is empty */
  public final int originalValue;
  public String[] labels;

  public PointerTable(final int address, final int originalValue, final String[] labels) {
    super(address);
    this.originalValue = originalValue;
    this.labels = labels;
  }

  @Override
  public String toString() {
    return "rel %x :%s".formatted(this.address, Arrays.toString(this.labels));
  }
}
