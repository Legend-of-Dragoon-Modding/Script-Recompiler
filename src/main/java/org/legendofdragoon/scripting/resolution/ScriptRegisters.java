package org.legendofdragoon.scripting.resolution;

import java.util.HashMap;
import java.util.Map;

public class ScriptRegisters {
  private final Map<Integer, RegisterSet> scriptRegisters = new HashMap<>();

  public ScriptRegisters copy(final ScriptRegisters other) {
    this.scriptRegisters.clear();

    for(final var entry : other.scriptRegisters.entrySet()) {
      this.scriptRegisters.put(entry.getKey(), new RegisterSet().copy(entry.getValue()));
    }

    return this;
  }

  /** Allocates a special state for the script we're decompiling since we don't know what state it'll be loaded into */
  public RegisterSet allocateDecompState() {
    return this.allocate(-1);
  }

  public RegisterSet allocate(final int stateIndex) {
    final RegisterSet registerSet = new RegisterSet();
    this.scriptRegisters.put(stateIndex, registerSet);
    return registerSet;
  }

  public boolean isStateAllocated(final int stateIndex) {
    return this.scriptRegisters.containsKey(stateIndex);
  }

  public RegisterSet getState(final int stateIndex) {
    return this.scriptRegisters.get(stateIndex);
  }

  /** Gets the special state for the script we're decompiling */
  public RegisterSet getDecompState() {
    return this.getState(-1);
  }
}
