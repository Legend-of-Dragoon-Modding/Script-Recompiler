package org.legendofdragoon.scripting.resolution;

public enum RegisterValueType {
  /** The absolute value is known */
  IMMEDIATE,
  /** The possible range of the value is known */
  RANGE,
}
