package org.legendofdragoon.scripting.compiler;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FateCompiler {
  static void main() {
    final List<String> errors = new ArrayList<>();
    final String compiled = new FateCompiler().compile(SOURCE, errors);

    if(!errors.isEmpty()) {
      System.err.println("There were errors during compilation:");

      for(final String error : errors) {
        System.err.println(error);
      }
    }

    System.out.println(compiled);
  }

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
    final FateCompilerVisitor visitor = new FateCompilerVisitor(fate, errors, functions);
    visitor.visit(tree);

    fate.updateVariableNames();
    return fate.compile();
  }

  private static final String SOURCE = """
    entrypoint main;
    
    def main() {
      testFunction(10);
      return;
    }
    
    def testFunction(someParam) {
      var variable = (someParam + 1) * 2;
      stor[10] = 100;
    
      if(variable >= returnParam(someParam)) {
        while(variable > 0) {
          variable--;
        }
      } else if(stor[10] == 100) {
        variable++;
      } else {
        stor[10] = var[8];
      }
    
      wait(10);
      var (a, b, c, d) = returnMultipleValues();
      (a, b, c, d) = returnMultipleValues();
      return;
    }
    
    def returnParam(p) {
      return p;
    }
    
    def returnMultipleValues() {
      return (1, 2, 3, 4);
    }
    """;
}
