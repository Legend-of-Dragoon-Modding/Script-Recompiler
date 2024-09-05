package org.legendofdragoon.scripting;

public class IncludeFailedException extends RuntimeException {
  public IncludeFailedException(final String message) {
    super(message);
  }

  public IncludeFailedException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public IncludeFailedException(final Throwable cause) {
    super(cause);
  }

  protected IncludeFailedException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
