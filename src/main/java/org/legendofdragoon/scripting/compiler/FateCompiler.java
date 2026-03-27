package org.legendofdragoon.scripting.compiler;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.legendofdragoon.scripting.meta.Meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FateCompiler {
  public FateCompiler(final Meta meta) {
    this.meta = meta;
  }

  private final Meta meta;

  public String compile(final String source, final List<String> errors) {
    final FateLexer lexer = new FateLexer(CharStreams.fromString(source));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final FateParser parser = new FateParser(tokens);
    final ParseTree tree = parser.body();

    // Preprocess to find functions
    final Map<String, FateFunctionDefinition> functions = new HashMap<>();
    final FatePreprocessor preprocessor = new FatePreprocessor(errors, functions);
    preprocessor.visit(tree);

    final FateContext fate = new FateContext();
    final FateCompilerVisitor visitor = new FateCompilerVisitor(this.meta, fate, errors, functions);
    visitor.visit(tree);

    fate.updateVariableNames();
    return fate.compile();
  }
}
