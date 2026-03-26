package org.legendofdragoon.scripting.compiler;

import org.apache.commons.collections.iterators.SingletonIterator;

import java.util.Iterator;

public class FateValue implements Iterable<FateValue> {
  @Override
  public Iterator<FateValue> iterator() {
    return new SingletonIterator(this);
  }

  public int count() {
    return 1;
  }
}
