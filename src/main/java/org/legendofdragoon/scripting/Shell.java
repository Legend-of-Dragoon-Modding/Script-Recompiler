package org.legendofdragoon.scripting;

import com.github.difflib.patch.PatchFailedException;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.meta.MetaManager;
import org.legendofdragoon.scripting.meta.NoSuchVersionException;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class Shell {
  private Shell() { }

  static {
    System.setProperty("log4j.skipJansi", "false");
    PluginManager.addPackage("org.legendofdragoon");
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger();

  public static void main(final String[] args) throws IOException, URISyntaxException, CsvException, NoSuchVersionException, PatchFailedException {
    if(args.length == 0) {
      LOGGER.info("Commands: [v]ersions, [d]ecompile, [c]ompile, [g]enpatch, [a]pplypatch, [u]ndopatch, [s]trip");
      System.exit(1);
      return;
    }

    final Path cacheDir = Path.of("./cache");
    final MetaManager metaManager = new MetaManager(new URI("https://legendofdragoon.org/scmeta/"), cacheDir);

    if("v".equals(args[0]) || "versions".equals(args[0])) {
      LOGGER.info("Fetching...");
      final String[] versions = metaManager.getVersions();

      LOGGER.info("Versions:");
      for(final String version : versions) {
        LOGGER.info(version);
      }

      System.exit(0);
      return;
    }

    if("g".equals(args[0]) || "genpatch".equals(args[0])) {
      generateDiff(metaManager, args);
      System.exit(0);
      return;
    }

    if("a".equals(args[0]) || "applypatch".equals(args[0])) {
      applyDiff(args);
      System.exit(0);
      return;
    }

    if("u".equals(args[0]) || "undopatch".equals(args[0])) {
      undoDiff(args);
      System.exit(0);
      return;
    }

    if("s".equals(args[0]) || "strip".equals(args[0])) {
      strip(metaManager, args);
      System.exit(0);
      return;
    }

    final Options options = new Options();
    options.addOption("v", "version", true, "The meta version to use");
    options.addRequiredOption("i", "in", true, "The input file");
    options.addRequiredOption("o", "out", true, "The output file");

    if("d".equals(args[0]) || "decompile".equals(args[0])) {
      options.addOption("b", "branch", true, "Force the decompiler to decompile this branch");
      options.addOption("t", "table-length", true, "Gives the table at the given address a specific length (e.g. 124c=5)");
      options.addOption("C", "no-comments", false, "Do not add comments to decompiled scripts");
      options.addOption("N", "no-names", false, "Do not use friendly names for engine calls");
      options.addOption("l", "line-numbers", false, "Prepend lines of decompiler output with addresses");
    }

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final String version = cmd.getOptionValue("version", "snapshot");

    LOGGER.info("Loading meta %s...", version);
    final Meta meta = metaManager.loadMeta(version);

    final Path inputFile = Paths.get(cmd.getOptionValue("in")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(inputFile)) {
      LOGGER.error("Error: input file does not exist");
      System.exit(1);
      return;
    }

    switch(args[0]) {
      case "d", "decompile" -> {
        final Disassembler disassembler = new Disassembler(meta);

        final String[] branchesIn = cmd.getOptionValues("branch");
        final String[] tableLengthsIn = cmd.getOptionValues("table-length");
        final boolean stripComments = cmd.hasOption("no-comments");
        final boolean stripNames = cmd.hasOption("no-names");
        final boolean lineNumbers = cmd.hasOption("line-numbers");

        if(branchesIn != null) {
          for(final String s : branchesIn) {
            disassembler.extraBranches.add(Integer.parseInt(s, 16));
          }
        }

        if(tableLengthsIn != null) {
          for(final String s : tableLengthsIn) {
            final String[] parts = s.split("=");

            if(parts.length != 2) {
              helper.printHelp("Usage:", options);
              System.exit(1);
            }

            try {
              final int address = Integer.parseInt(parts[0], 16);
              final int count = Integer.parseInt(parts[1]);
              LOGGER.info("Using table length %#x=%d", address, count);
              disassembler.tableLengths.put(address, count);
            } catch(final NumberFormatException e) {
              helper.printHelp("Usage:", options);
              System.exit(1);
            }
          }
        }

        final Translator translator = new Translator();
        translator.stripNames = stripNames;
        translator.stripComments = stripComments;
        translator.lineNumbers = lineNumbers;

        LOGGER.info("Decompiling %s...", inputFile);

        final byte[] bytes = Files.readAllBytes(inputFile);
        final Script script = disassembler.disassemble(bytes);
        final String decompiledOutput = translator.translate(script, meta);

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, decompiledOutput, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      }

      case "c", "compile" -> {
        LOGGER.info("Compiling... %s", inputFile);

        final Compiler compiler = new Compiler();
        final Tokenizer tokenizer = new Tokenizer(meta);

        final String input = Files.readString(inputFile);
        final Script tokenizedDecompiledSource = tokenizer.tokenize(inputFile, input);
        final int[] recompiledSource = compiler.compile(tokenizedDecompiledSource);

        Files.createDirectories(outputFile.getParent());
        Files.write(outputFile, intsToBytes(recompiledSource), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      }

      default -> {
        LOGGER.info("Commands: [v]ersions, [d]ecompile, [c]ompile, [g]enpatch, [a]pplypatch, [u]ndopatch");
        System.exit(1);
      }
    }
  }

  private static void generateDiff(final MetaManager metaManager, final String[] args) throws IOException, NoSuchVersionException, CsvException {
    final Options options = new Options();
    options.addOption("v", "version", true, "The meta version to use");
    options.addRequiredOption("a", "original", true, "The original file");
    options.addRequiredOption("b", "modified", true, "The modified file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final String version = cmd.getOptionValue("version", "snapshot");

    LOGGER.info("Loading meta %s...", version);
    final Meta meta = metaManager.loadMeta(version);

    final Path originalFile = Paths.get(cmd.getOptionValue("original")).toAbsolutePath();
    final Path modifiedFile = Paths.get(cmd.getOptionValue("modified")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(originalFile) || !Files.exists(modifiedFile)) {
      LOGGER.error("Error: one or both input files do not exist");
      System.exit(1);
      return;
    }

    LOGGER.info("Generating diff...");
    LOGGER.info("Original: %s", originalFile);
    LOGGER.info("Modified: %s", modifiedFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.generatePatch(meta, originalFile, modifiedFile);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void applyDiff(final String[] args) throws IOException, PatchFailedException {
    final Options options = new Options();
    options.addRequiredOption("a", "original", true, "The original file");
    options.addRequiredOption("b", "patch", true, "The patch file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final Path originalFile = Paths.get(cmd.getOptionValue("original")).toAbsolutePath();
    final Path patchFile = Paths.get(cmd.getOptionValue("patch")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(originalFile) || !Files.exists(patchFile)) {
      LOGGER.error("Error: one or both input files do not exist");
      System.exit(1);
      return;
    }

    LOGGER.info("Applying diff...");
    LOGGER.info("Original: %s", originalFile);
    LOGGER.info("Patch: %s", patchFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.applyPatch(originalFile, patchFile);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void undoDiff(final String[] args) throws IOException {
    final Options options = new Options();
    options.addRequiredOption("a", "patched", true, "The patched file");
    options.addRequiredOption("b", "patch", true, "The patch file");
    options.addRequiredOption("o", "out", true, "The output file");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final Path patchedFile = Paths.get(cmd.getOptionValue("patched")).toAbsolutePath();
    final Path patchFile = Paths.get(cmd.getOptionValue("patch")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(patchedFile) || !Files.exists(patchFile)) {
      LOGGER.error("Error: one or both input files do not exist");
      System.exit(1);
      return;
    }

    LOGGER.info("Applying diff...");
    LOGGER.info("Patched: %s", patchedFile);
    LOGGER.info("Patch: %s", patchFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.undoPatch(patchedFile, patchFile);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void strip(final MetaManager metaManager, final String[] args) throws IOException, NoSuchVersionException, CsvException {
    final Options options = new Options();
    options.addOption("v", "version", true, "The meta version to use");
    options.addRequiredOption("i", "in", true, "The original file");
    options.addRequiredOption("o", "out", true, "The output file");
    options.addOption("C", "ignore-calls", false, "Do not strip calls");
    options.addOption("E", "ignore-end-of-line-comments", false, "Do not strip end-of-line comments");
    options.addOption("F", "ignore-full-line-comments", false, "Do not strip full-line comments");
    options.addOption("B", "ignore-blank-lines", false, "Do not strip blank lines");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      LOGGER.error(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final String version = cmd.getOptionValue("version", "snapshot");

    LOGGER.info("Loading meta %s...", version);
    final Meta meta = metaManager.loadMeta(version);

    final Path inputFile = Paths.get(cmd.getOptionValue("in")).toAbsolutePath();
    final Path outputFile = Paths.get(cmd.getOptionValue("out")).toAbsolutePath();

    if(!Files.exists(inputFile)) {
      LOGGER.error("Error: input file does not exist");
      System.exit(1);
      return;
    }

    final boolean stripCalls = !cmd.hasOption("ignore-calls");
    final boolean stripEndOfLineComments = !cmd.hasOption("ignore-end-of-line-comments");
    final boolean stripFullLineComments = !cmd.hasOption("ignore-full-line-comments");
    final boolean stripBlankLines = !cmd.hasOption("ignore-blank-lines");

    LOGGER.info("Stripping script...");
    LOGGER.info("Input: %s", inputFile);
    LOGGER.info("Output: %s", outputFile);

    final String output = Patcher.strip(meta, inputFile, stripCalls, stripEndOfLineComments, stripFullLineComments, stripBlankLines);
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static byte[] intsToBytes(final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.allocate(ints.length * 0x4).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(ints);
    return buffer.array();
  }
}
