package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.ParameterType;
import org.legendofdragoon.scripting.resolution.ResolvedValue;

import java.util.Arrays;
import java.util.OptionalInt;

public class Param extends Entry {
  public final ParameterType type;
  public final int[] rawValues;
  public final ResolvedValue resolvedValue;
  public final String label;

  public Param(final int address, final ParameterType type, final int[] rawValues, final ResolvedValue resolvedValue, final String label) {
    super(address);
    this.type = type;
    this.rawValues = rawValues;
    this.resolvedValue = resolvedValue;
    this.label = label;
  }

  @Override
  public String toString() {
    return "param " + Arrays.toString(this.rawValues) + ' ' + this.label;
  }
}
