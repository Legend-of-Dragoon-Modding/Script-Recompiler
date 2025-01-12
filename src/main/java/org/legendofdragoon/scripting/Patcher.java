package org.legendofdragoon.scripting;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import org.legendofdragoon.scripting.meta.Meta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Patcher {
  private Patcher() { }

  private static final Pattern CALL_MATCHER = Pattern.compile("^call\\s+(.+?)\\s*(?:,.*|$)", Pattern.CASE_INSENSITIVE);

  public static String generatePatch(final Meta meta, final Path originalFile, final Path modifiedFile) throws IOException {
    final List<String> originalLines = strip(meta, Files.readAllLines(originalFile));
    final List<String> modifiedLines = strip(meta, Files.readAllLines(modifiedFile));
    return generatePatch(originalLines, modifiedLines);
  }

  public static String generatePatch(final List<String> originalLines, final List<String> modifiedLines) {
    final Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);
    final List<String> diff = UnifiedDiffUtils.generateUnifiedDiff("original", "modified", originalLines, patch, 3);
    final StringBuilder output = new StringBuilder();

    for(final String line : diff) {
      output.append(line).append('\n');
    }

    return output.toString();
  }

  public static String applyPatch(final Path originalFile, final Path patchFile) throws IOException, PatchFailedException {
    final List<String> originalLines = Files.readAllLines(originalFile);
    final List<String> patchLines = Files.readAllLines(patchFile);
    return applyPatch(originalLines, patchLines);
  }

  public static String applyPatch(final List<String> originalLines, final List<String> patchLines) throws PatchFailedException {
    final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
    final List<String> patched = DiffUtils.patch(originalLines, patch);
    final StringBuilder output = new StringBuilder();

    for(final String line : patched) {
      output.append(line).append('\n');
    }

    return output.toString();
  }

  public static String undoPatch(final Path patchedFile, final Path patchFile) throws IOException {
    final List<String> patchedLines = Files.readAllLines(patchedFile);
    final List<String> patchLines = Files.readAllLines(patchFile);
    return undoPatch(patchedLines, patchLines);
  }

  public static String undoPatch(final List<String> patchedLines, final List<String> patchLines) {
    final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
    final List<String> unpatched = DiffUtils.unpatch(patchedLines, patch);
    final StringBuilder output = new StringBuilder();

    for(final String line : unpatched) {
      output.append(line).append('\n');
    }

    return output.toString();
  }

  public static String strip(final Meta meta, final Path file, final boolean stripCalls, final boolean stripEndOfLineComments, final boolean stripFullLineComments, final boolean stripBlankLines) throws IOException {
    return String.join("\n", strip(meta, Files.readAllLines(file), stripCalls, stripEndOfLineComments, stripFullLineComments, stripBlankLines));
  }

  private static List<String> strip(final Meta meta, final List<String> input) {
    return strip(meta, input, true, true, true, true);
  }

  private static List<String> strip(final Meta meta, final List<String> input, final boolean stripCalls, final boolean stripEndOfLineComments, final boolean stripFullLineComments, final boolean stripBlankLines) {
    Stream<String> stream = input.stream();

    if(stripEndOfLineComments) {
      stream = stream.map(line -> line.indexOf(';') > 0 ? line.substring(0, line.indexOf(';')) : line);
    }

    if(stripFullLineComments) {
      stream = stream.map(line -> line.indexOf(';') == 0 ? "" : line);
    }

    if(stripCalls) {
      stream = stream.map(Patcher.replaceCalls(meta));
    }

    stream = stream.map(String::strip);

    if(stripBlankLines) {
      stream = stream.filter(Predicate.not(String::isBlank));
    }

    return stream.toList();
  }

  private static Function<String, String> replaceCalls(final Meta meta) {
    return line -> {
      final Matcher matcher = CALL_MATCHER.matcher(line);

      if(matcher.matches()) {
        final String call = matcher.group(1).strip();

        for(int i = 0; i < meta.methods.length; i++) {
          if(meta.methods[i].name.equals(call)) {
            return line.replace(call, Integer.toString(i));
          }
        }
      }

      return line;
    };
  }
}
