package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.ParameterType;
import org.legendofdragoon.scripting.resolution.ResolvedValue;

import java.util.Arrays;
import java.util.function.BiConsumer;

public class Param extends Entry {
  public final ParameterType type;
  public final int[] rawValues;
  public final ResolvedValue resolvedValue;
  public final String label;
  private final BiConsumer<Script, int[]> resolver;

  public Param(final int address, final ParameterType type, final int[] rawValues, final ResolvedValue resolvedValue, final String label, final BiConsumer<Script, int[]> resolver) {
    super(address);
    this.type = type;
    this.rawValues = rawValues;
    this.resolvedValue = resolvedValue;
    this.label = label;
    this.resolver = resolver;
  }

  public Param(final int address, final ParameterType type, final int[] rawValues, final ResolvedValue resolvedValue, final String label) {
    this(address, type, rawValues, resolvedValue, label, null);
  }

  /**
   * For params bound to labels, resolves the labels and sets rawValues[i + 1] to the label address
   */
  public void resolveValues(final Script script) {
    if(this.resolver != null) {
      this.resolver.accept(script, this.rawValues);
    }
  }

  @Override
  public String toString() {
    return "param " + Arrays.toString(this.rawValues) + ' ' + this.label;
  }
}
