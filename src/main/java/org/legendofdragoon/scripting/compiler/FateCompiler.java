package org.legendofdragoon.scripting.compiler;

import com.opencsv.exceptions.CsvException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.legendofdragoon.scripting.Compiler;
import org.legendofdragoon.scripting.Tokenizer;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.meta.MetaManager;
import org.legendofdragoon.scripting.meta.NoSuchVersionException;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.legendofdragoon.scripting.Shell.intsToBytes;

public class FateCompiler {
  static void main() throws NoSuchVersionException, URISyntaxException, IOException, CsvException {
    new FateCompiler().compile(SOURCE);
  }

  public void compile(final String source) throws URISyntaxException, NoSuchVersionException, IOException, CsvException {
    final FateLexer lexer = new FateLexer(CharStreams.fromString(source));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final FateParser parser = new FateParser(tokens);
    final ParseTree tree = parser.body();

    final List<String> errors = new ArrayList<>();

    // Preprocess to find functions
    final Map<String, FateFunctionDefinition> functions = new HashMap<>();
    final FatePreprocessor preprocessor = new FatePreprocessor(errors, functions);
    preprocessor.visit(tree);

    final FateContext fate = new FateContext();
    final FateCompilerVisitor visitor = new FateCompilerVisitor(fate, errors, functions);
    visitor.visit(tree);

    if(!errors.isEmpty()) {
      System.err.println("There were errors during compilation:");

      for(final String error : errors) {
        System.err.println(error);
      }
    }

    System.out.println();

    fate.updateVariableNames();
    final String assembled = fate.compile();
    System.out.println(assembled);

    final Path cacheDir = Path.of("./cache");
    final MetaManager metaManager = new MetaManager(new URI("https://legendofdragoon.org/scmeta/"), cacheDir);
    final Meta meta = metaManager.loadMeta("snapshot");
    final Tokenizer tokenizer = new Tokenizer(meta);
    final Script script = tokenizer.tokenize("compiled", List.of(), assembled);

    final Compiler compiler = new Compiler();
    final int[] compiled = compiler.compile(script);
    Files.write(Path.of("test"), intsToBytes(compiled), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
