package org.legendofdragoon.scripting.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FateValueList extends FateValue {
  public final List<FateValue> values = new ArrayList<>();

  @Override
  public Iterator<FateValue> iterator() {
    return this.values.iterator();
  }

  @Override
  public int count() {
    return this.values.size();
  }

  @Override
  public String toString() {
    return this.values.toString();
  }
}
