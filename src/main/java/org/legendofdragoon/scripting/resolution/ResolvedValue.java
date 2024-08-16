package org.legendofdragoon.scripting.resolution;

import java.util.function.IntConsumer;

public interface ResolvedValue {
  boolean isPresent();
  int get();

  default boolean isRange() { return false; }
  default int min() {
    throw new IllegalStateException("No value is present");
  }
  default int max() {
    throw new IllegalStateException("No value is present");
  }

  default void ifPresent(final IntConsumer ifPresent) {
    if(this.isPresent()) {
      ifPresent.accept(this.get());
    }
  }

  default void ifPresentOrElse(final IntConsumer ifPresent, final Runnable orElse) {
    if(this.isPresent()) {
      ifPresent.accept(this.get());
    } else {
      orElse.run();
    }
  }

  default int orElse(final int orElse) {
    if(!this.isPresent()) {
      return orElse;
    }

    return this.get();
  }

  static ResolvedValue unresolved() {
    return new ResolvedValue() {
      @Override
      public boolean isPresent() {
        return false;
      }

      @Override
      public int get() {
        throw new IllegalStateException("No value is present");
      }
    };
  }

  static ResolvedValue of(final int value) {
    return new ResolvedValue() {
      @Override
      public boolean isPresent() {
        return true;
      }

      @Override
      public int get() {
        return value;
      }
    };
  }

  static ResolvedValue register(final Register register) {
    return new ResolvedValue() {
      @Override
      public boolean isPresent() {
        return register.isKnown();
      }

      @Override
      public int get() {
        return register.known().getAsInt();
      }

      @Override
      public boolean isRange() {
        return register.isRange();
      }

      @Override
      public int min() {
        return register.min().getAsInt();
      }

      @Override
      public int max() {
        return register.max().getAsInt();
      }
    };
  }
}
