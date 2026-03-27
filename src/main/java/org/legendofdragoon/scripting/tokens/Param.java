package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.ParameterType;
import org.legendofdragoon.scripting.resolution.ResolvedValue;

import java.util.Arrays;

public class Param extends Entry {
  public final ParameterType type;
  public final int[] rawValues;
  public final ResolvedValue resolvedValue;
  public final String label;
  private final String[] paramLabels;

  public Param(final int address, final ParameterType type, final int[] rawValues, final ResolvedValue resolvedValue, final String label, final String[] paramLabels) {
    super(address);
    this.type = type;
    this.rawValues = rawValues;
    this.resolvedValue = resolvedValue;
    this.label = label;
    this.paramLabels = paramLabels;
  }

  public Param(final int address, final ParameterType type, final int[] rawValues, final ResolvedValue resolvedValue, final String label) {
    this(address, type, rawValues, resolvedValue, label, null);
  }

  /**
   * For params bound to labels, resolves the labels and sets rawValues[i + 1] to the label address
   */
  public void resolveLabels(final Script script) {
    if(this.paramLabels != null) {
      for(int i = 0; i < this.paramLabels.length; i++) {
        this.rawValues[i + 1] = script.findLabelAddress(this.paramLabels[i]) / 4;
      }
    }
  }

  @Override
  public String toString() {
    return "param " + Arrays.toString(this.rawValues) + ' ' + this.label;
  }
}
