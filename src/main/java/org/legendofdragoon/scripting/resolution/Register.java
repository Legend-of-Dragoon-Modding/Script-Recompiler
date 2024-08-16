package org.legendofdragoon.scripting.resolution;

import java.util.OptionalInt;

public class Register {
  private OptionalInt value;
  private OptionalInt min;
  private OptionalInt max;

  public Register() {
    this.unknown();
  }

  public void copy(final Register other) {
    this.value = other.value;
    this.min = other.min;
    this.max = other.max;
  }

  public void unknown() {
    this.value = OptionalInt.empty();
    this.min = OptionalInt.empty();
    this.max = OptionalInt.empty();
  }

  public void known(final int value) {
    this.unknown();
    this.value = OptionalInt.of(value);
  }

  public OptionalInt known() {
    return this.value;
  }

  public boolean isKnown() {
    return this.value.isPresent();
  }

  public void range(final int min, final int max) {
    this.unknown();
    this.min = OptionalInt.of(min);
    this.max = OptionalInt.of(max);
  }

  public OptionalInt min() {
    return this.min;
  }

  public OptionalInt max() {
    return this.max;
  }

  public boolean isRange() {
    return this.max.isPresent();
  }
}
