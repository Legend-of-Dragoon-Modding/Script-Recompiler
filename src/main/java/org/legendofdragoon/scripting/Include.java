package org.legendofdragoon.scripting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Include {
  public static Path resolve(final List<Path> includePaths, final Path originalIncludeFile) {
    Path includeFile = originalIncludeFile;
    Path resolvedIncludeFile = null;

    if(!includeFile.isAbsolute()) {
      for(final Path includePath : includePaths) {
        final Path resolved = includePath.resolve(includeFile).normalize();
        if(Files.exists(resolved)) {
          resolvedIncludeFile = resolved;
          break;
        }
      }

      includeFile = resolvedIncludeFile;
    }

    if(includeFile == null || !Files.exists(includeFile)) {
      throw new IncludeFailedException("Could not resolve include " + originalIncludeFile + " in include paths " + includePaths);
    }

    return includeFile.toAbsolutePath().normalize();
  }
}
