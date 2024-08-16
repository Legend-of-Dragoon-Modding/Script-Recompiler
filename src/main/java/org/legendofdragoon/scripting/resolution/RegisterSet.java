package org.legendofdragoon.scripting.resolution;

import java.util.Arrays;

public class RegisterSet {
  public final Register[] stor = new Register[33];

  public RegisterSet() {
    Arrays.setAll(this.stor, i -> new Register());
  }

  public RegisterSet copy(final RegisterSet other) {
    for(int i = 0; i < this.stor.length; i++) {
      this.stor[i].copy(other.stor[i]);
    }

    return this;
  }

  public void clear() {
    for(final Register register : this.stor) {
      register.unknown();
    }
  }
}
