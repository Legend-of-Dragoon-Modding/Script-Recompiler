package org.legendofdragoon.scripting.compiler;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.legendofdragoon.scripting.Include;
import org.legendofdragoon.scripting.meta.Meta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FateCompiler {
  public FateCompiler(final Meta meta) {
    this.meta = meta;
  }

  private final Meta meta;

  public String compile(final List<Path> includePaths, final String source, final List<String> errors) {
    return this.compile(includePaths, source, "", errors);
  }

  public String compile(final List<Path> includePaths, final String source, final String labelPrefix, final List<String> errors) {
    final ParseTree tree = this.parse(source);

    // Preprocess to find functions
    final Map<String, FateFunctionDefinition> functions = new HashMap<>();
    final Deque<String> includes = new LinkedList<>();
    final Set<String> processedIncludes = new HashSet<>();
    final FatePreprocessor preprocessor = new FatePreprocessor(errors, includes, functions);
    preprocessor.visit(tree);

    this.getFunctionsFromIncludes(includePaths, errors, includes, processedIncludes, functions);

    final FateContext fate = new FateContext();
    final FateCompilerVisitor visitor = new FateCompilerVisitor(this.meta, fate, errors, functions);
    visitor.visit(tree);

    fate.updateVariableNames();
    fate.updateLabelNames(labelPrefix);
    return fate.compile();
  }

  private ParseTree parse(final String source) {
    final FateLexer lexer = new FateLexer(CharStreams.fromString(source));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final FateParser parser = new FateParser(tokens);
    return parser.body();
  }

  private void getFunctionsFromIncludes(final List<Path> includePaths, final List<String> errors, final Deque<String> includes, final Set<String> processedIncludes, final Map<String, FateFunctionDefinition> functions) {
    while(!includes.isEmpty()) {
      final String include = includes.removeFirst();

      if(processedIncludes.contains(include)) {
        continue;
      }

      processedIncludes.add(include);

      if(include.endsWith(".fate")) {
        final Path path = Include.resolve(includePaths, Path.of(include));
        final ParseTree tree;
        try {
          tree = this.parse(Files.readString(path));
        } catch(final IOException e) {
          errors.add("Failed to load include " + e);
          continue;
        }

        final FatePreprocessor preprocessor = new FatePreprocessor(errors, includes, functions);
        preprocessor.visit(tree);
      }
    }
  }
}
