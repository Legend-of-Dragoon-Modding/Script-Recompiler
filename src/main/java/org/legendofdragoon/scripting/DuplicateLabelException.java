package org.legendofdragoon.scripting;

public class DuplicateLabelException extends RuntimeException {
  public DuplicateLabelException() {
    super();
  }

  public DuplicateLabelException(final String message) {
    super(message);
  }

  public DuplicateLabelException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public DuplicateLabelException(final Throwable cause) {
    super(cause);
  }

  protected DuplicateLabelException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
