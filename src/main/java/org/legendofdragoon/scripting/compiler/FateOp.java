package org.legendofdragoon.scripting.compiler;

import org.legendofdragoon.scripting.OpType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FateOp {
  public final OpType opType;
  public final List<FateValue> params = new ArrayList<>();

  public FateOp(final OpType opType, final FateValue... params) {
    this.opType = opType;
    this.params.addAll(List.of(params));

    for(final FateValue value : this.params) {
      if(value.count() != 1) {
        throw new RuntimeException("Op params must have count of 1");
      }
    }
  }

  @Override
  public String toString() {
    return this.opType.name + ' ' + this.params.stream().map(Objects::toString).collect(Collectors.joining(", "));
  }
}
