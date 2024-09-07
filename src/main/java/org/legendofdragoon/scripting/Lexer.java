package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.resolution.ResolvedValue;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lexer {
  public static final Pattern INCLUDE_PATTERN = Pattern.compile("^#include\\s+([^;]+)\\s*;?.*$", Pattern.CASE_INSENSITIVE);

  public static final String NUMBER_SUBPATTERN = "0x[a-f\\d]{1,8}|\\d{1,10}";
  public static final Pattern LINE_PATTERN = Pattern.compile("^\\s*?(?:[a-f0-9]+\\s+)?([a-z]\\w*?)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  public static final Pattern NUMBER_PATTERN = Pattern.compile("^-?(?:" + NUMBER_SUBPATTERN + ")$", Pattern.CASE_INSENSITIVE);
  public static final Pattern LABEL_PATTERN = Pattern.compile("^(\\w+):$", Pattern.CASE_INSENSITIVE);
  public static final Pattern LABEL_PARAM_PATTERN = Pattern.compile("^:(\\w+)$", Pattern.CASE_INSENSITIVE);
  public static final Pattern CALL_PATTERN = Pattern.compile("^[a-z_]\\w*::[a-z_]\\w*$", Pattern.CASE_INSENSITIVE);
  public static final Pattern STRING_PATTERN = Pattern.compile("^str\\[(.*?)]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern OPERATOR_PATTERN = Pattern.compile("^(<=|<|==|!=|>|>=|&|!&)$", Pattern.CASE_INSENSITIVE);

  public static final Pattern STORAGE_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern OTHER_OTHER_STORAGE_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern OTHER_STORAGE_OFFSET_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern GAMEVAR_1_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern GAMEVAR_2_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern GAMEVAR_ARRAY_1_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern GAMEVAR_ARRAY_2_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern INLINE_1_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern INLINE_2_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);

  public static final Pattern GAMEVAR_3_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$");

  public static final Pattern INLINE_3_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern CONTROL_PATTERN = Pattern.compile("^<\\s*?([a-z]+)(?:\\s*?=\\s*?(" + NUMBER_SUBPATTERN + "))?\\s*?>$", Pattern.CASE_INSENSITIVE);

  public static final Pattern GAMEVAR_ARRAY_3_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern GAMEVAR_ARRAY_4_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);

  public static final Pattern INLINE_6_PATTERN = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\+\\s*?inl\\s*?\\[(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\+\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);

  public static final Pattern REG_PATTERN = Pattern.compile("^reg\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern REG_VAR_PATTERN = Pattern.compile("^reg\\s*?\\[\\s*?stor\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  public static final Pattern ID_PATTERN = Pattern.compile("^id\\s*?\\[\\s*?(.*?:.*?)\\s*?]$", Pattern.CASE_INSENSITIVE);

  private final Meta meta;

  public Lexer(final Meta meta) {
    this.meta = meta;
  }

  public Script lex(final Path path, final String source) {
    List<String> lines = this.splitSource(source);

    final List<Entry> entries = new ArrayList<>();
    final Map<String, Integer> labels = new HashMap<>();
    final Set<String> tables = new HashSet<>();

    for(int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex);
      final int address = entries.size() * 0x4;

      final Matcher includeMatcher = INCLUDE_PATTERN.matcher(line);
      if(includeMatcher.matches()) {
        Path includePath = Path.of(includeMatcher.group(1));

        if(!includePath.isAbsolute()) {
          includePath = path.getParent().resolve(includePath);
        }

        final List<String> newLines = new ArrayList<>(lines.subList(0, lineIndex));
        try {
          newLines.addAll(this.splitSource(Files.readString(includePath)));
        } catch(final IOException e) {
          throw new IncludeFailedException("Include for " + includePath + " failed", e);
        }

        newLines.addAll(lines.subList(lineIndex + 1, lines.size()));
        lines = newLines;
        line = lines.get(lineIndex);
      }

      final Matcher labelMatcher = LABEL_PATTERN.matcher(line);
      if(labelMatcher.matches()) {
        final String label = labelMatcher.group(1);

        if(labels.containsKey(label)) {
          throw new DuplicateLabelException("Label %s already defined at address 0x%x".formatted(label, labels.get(label)));
        }

        labels.put(label, address);
        continue;
      }

      final Entry entry = this.lexLine(address, line);
      entries.add(entry);

      if(entry instanceof final Op op) {
        if(op.type == OpType.GOSUB_TABLE || op.type == OpType.JMP_TABLE && op.params[1].label != null) {
          tables.add(op.params[1].label);
        }

        for(final Param param : op.params) {
          if(param.type.isInlineTable() && param.label != null) {
            tables.add(param.label);
          }

          for(int i = 0; i < param.type.getWidth(param); i++) {
            entries.add(param);
          }
        }
      } else if(entry instanceof final LodString string) {
        for(int i = 1; i < (string.chars.length + 1) / 2; i++) {
          entries.add(entry);
        }
      }
    }

    // Rebind inline parameters to their label's address
    for(final Entry entry : entries) {
      if(entry instanceof final Op op) {
        for(final Param param : op.params) {
          if(param.label != null) {
            if(!labels.containsKey(param.label)) {
              throw new RuntimeException("Missing label " + param.label);
            }

            final int address = labels.get(param.label);

            switch(param.type) {
              case INLINE_1, INLINE_2, INLINE_TABLE_1, INLINE_TABLE_3 -> param.rawValues[0] |= (address - op.address) / 0x4 & 0xffff;
              case INLINE_TABLE_2, INLINE_3, INLINE_TABLE_4 -> throw new RuntimeException("Need to implement label bindings for " + param.type);
            }

            if((op.type == OpType.GOSUB_TABLE || op.type == OpType.JMP_TABLE) && param.type.isInlineTable()) {
              final int tableOffset = labels.get(param.label) / 4;

              for(int entryOffset = tableOffset; entryOffset < entries.size(); entryOffset++) {
                final int finalEntryOffset = entryOffset;
                if(entryOffset != tableOffset && labels.entrySet().stream().filter(e -> e.getValue() == finalEntryOffset * 0x4).map(Map.Entry::getKey).anyMatch(tables::contains)) {
                  break;
                }

                if(entries.get(entryOffset) instanceof final PointerTable table) {
                  tables.add(table.labels[0]);
                } else {
                  break;
                }
              }
            }
          }
        }
      }
    }

    final List<Integer> tableOffsets = tables.stream().map(key -> labels.get(key) / 0x4).distinct().sorted(Comparator.reverseOrder()).toList();
    int maxOffset = entries.size(); // Used to know when to end jump tables that run into each other

    // Fix jump table addresses (note: these addresses are sorted from last to first)
    for(final int tableOffset : tableOffsets) {
      final List<String> newLabels = new ArrayList<>();
      int offset = tableOffset;

      while(offset < maxOffset && entries.get(offset) instanceof final PointerTable table) {
        newLabels.add(table.labels[0]);
        offset++;
      }

      entries.set(tableOffset, new PointerTable(tableOffset * 0x4, 0, newLabels.toArray(String[]::new)));

      for(int i = 1; i < newLabels.size(); i++) {
        entries.set(tableOffset + i, new Data((tableOffset + i) * 0x4, 0));
      }

      maxOffset = tableOffset;
    }

    final Script script = new Script(entries.size());
    entries.toArray(script.entries);

    for(final Map.Entry<String, Integer> entry : labels.entrySet()) {
      script.labels.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
    }

    return script;
  }

  private List<String> splitSource(final String source) {
    return source.lines().map(this::removeComment).map(String::strip).filter(Predicate.not(String::isBlank)).toList();
  }

  private String removeComment(final String line) {
    final int pos = line.indexOf(';');

    if(pos == -1) {
      return line;
    }

    return line.substring(0, pos);
  }

  private Entry lexLine(final int address, final String line) {
    final Matcher lineMatcher = LINE_PATTERN.matcher(line);
    if(lineMatcher.matches()) {
      final String command = lineMatcher.group(1);
      final String paramsStr = lineMatcher.group(2);

      try {
        if("entrypoint".equalsIgnoreCase(command)) {
          if(!LABEL_PARAM_PATTERN.matcher(paramsStr).matches()) {
            throw new RuntimeException("Invalid entrypoint label " + paramsStr);
          }

          return new Entrypoint(address, paramsStr.substring(1));
        }
        if("data".equalsIgnoreCase(command)) {
          final Matcher stringMatcher = STRING_PATTERN.matcher(paramsStr);
          if(stringMatcher.matches()) {
            return LodString.fromString(address, stringMatcher.group(1));
          }

          return new Data(address, this.parseInt(paramsStr));
        }
        if("rel".equalsIgnoreCase(command)) {
          if(!LABEL_PARAM_PATTERN.matcher(paramsStr).matches()) {
            throw new RuntimeException("Invalid relative pointer label " + paramsStr);
          }

          return new PointerTable(address, 0, new String[] { paramsStr.substring(1) });
        }
        final OpType opType = OpType.byName(command);

        if(opType != null) {
          final int headerParam;
          Param[] params;

          if(paramsStr != null) {
            params = this.parseParams(address, address + 0x4, opType, paramsStr);

            if(opType.headerParamName == null) {
              headerParam = 0;
            } else {
              headerParam = params[0].rawValues[0];
              params = Arrays.copyOfRange(params, 1, params.length);
            }
          } else {
            headerParam = 0;
            params = new Param[0];
          }

          final Op op = new Op(address, opType, headerParam, params.length);
          System.arraycopy(params, 0, op.params, 0, params.length);
          return op;
        }
      } catch(final NumberFormatException e) {
        System.err.println(e.getMessage());
      }
    }

    throw new RuntimeException("Invalid line \"" + line + '"');
  }

  private Param[] parseParams(final int opAddress, int address, final OpType opType, final String paramsString) {
    final String[] paramStrings = this.splitParameters(paramsString);
    final Param[] params = new Param[paramStrings.length];
    int headerParam = 0;

    for(int i = 0; i < params.length; i++) {
      final Param param = this.parseParam(opAddress, address, opType, headerParam, i - (opType.headerParamName != null ? 1 : 0), paramStrings[i]);
      params[i] = param;

      // If we have a header param, the first param returns will be a pseudo-param that doesn't advance the address since it's part of the header
      if(i != 0 || opType.headerParamName == null) {
        address += param.type.getWidth(paramStrings[i]) * 0x4;
      } else {
        headerParam = param.rawValues[0];
      }
    }

    return params;
  }

  private Param parseParam(final int opAddress, final int address, final OpType opType, final int headerParam, final int paramIndex, String paramString) {
    // Convert call function refs to ints
    if(CALL_PATTERN.matcher(paramString).matches()) {
      boolean found = false;
      for(int i = 0; i < this.meta.methods.length; i++) {
        if(this.meta.methods[i].name.equalsIgnoreCase(paramString)) {
          paramString = Integer.toString(i);
          found = true;
          break;
        }
      }

      if(!found) {
        throw new UnknownCallException("Unknown call " + paramString);
      }
    }

    // Immediates
    try {
      final int value = this.parseInt(paramString);

      if((value & 0xff00_0000) == 0) {
        return new Param(address, ParameterType.IMMEDIATE, new int[] { value }, ResolvedValue.of(value), null);
      }
      return new Param(address, ParameterType.NEXT_IMMEDIATE, new int[] { this.packParam(ParameterType.NEXT_IMMEDIATE), value }, ResolvedValue.of(value), null);
    } catch(final NumberFormatException ignored) { }

    Matcher matcher;
    if(paramIndex == -1 && (opType == OpType.WAIT_CMP || opType == OpType.WAIT_CMP_0 || opType == OpType.JMP_CMP || opType == OpType.JMP_CMP_0) && (matcher = OPERATOR_PATTERN.matcher(paramString)).matches()) {
      final int operatorIndex = switch(matcher.group(1)) {
        case "<=" -> 0;
        case "<" -> 1;
        case "==" -> 2;
        case "!=" -> 3;
        case ">" -> 4;
        case ">=" -> 5;
        case "&" -> 6;
        case "!&" -> 7;
        default -> throw new RuntimeException("Unknown operator " + matcher.group(1));
      };

      return new Param(address, ParameterType.IMMEDIATE, new int[] { operatorIndex }, ResolvedValue.of(operatorIndex), null);
    }

    if(paramIndex != -1 && paramIndex < this.meta.methods[headerParam].params.length && opType == OpType.CALL) {
      final String enumClass = this.meta.methods[headerParam].params[paramIndex].type;
      if(this.meta.enums.containsKey(enumClass)) {
        final String[] enumValues = this.meta.enums.get(enumClass);

        for(int i = 0; i < enumValues.length; i++) {
          if(enumValues[i].equalsIgnoreCase(paramString)) {
            return new Param(address, ParameterType.IMMEDIATE, new int[] { i }, ResolvedValue.of(i), null);
          }
        }

        throw new RuntimeException("Unknown " + enumClass + " value " + paramString);
      }
    }

    if((matcher = STORAGE_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.STORAGE, new int[] { this.packParam(ParameterType.STORAGE, p0) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = OTHER_OTHER_STORAGE_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.OTHER_OTHER_STORAGE, new int[] { this.packParam(ParameterType.OTHER_OTHER_STORAGE, p0, p1, p2) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = OTHER_STORAGE_OFFSET_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.OTHER_STORAGE_OFFSET, new int[] { this.packParam(ParameterType.OTHER_STORAGE_OFFSET, p0, p1, p2) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = GAMEVAR_1_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.GAMEVAR_1, new int[] { this.packParam(ParameterType.GAMEVAR_1, p0) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = GAMEVAR_2_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_2, new int[] { this.packParam(ParameterType.GAMEVAR_1, p0, p1) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = GAMEVAR_ARRAY_1_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_1, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_1, p0, p1) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = GAMEVAR_ARRAY_2_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_2, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_2, p0, p1, p2) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = INLINE_1_MATCHER.matcher(paramString)).matches()) {
      final String val = matcher.group(1);

      final int inline;
      final String label;
      if(LABEL_PARAM_PATTERN.matcher(val).matches()) {
        inline = this.packParam(ParameterType.INLINE_1);
        label = val.substring(1);
      } else {
        final int value = this.parseInt(val);
        final int p0 = (value - opAddress) / 0x4;
        inline = this.packParam(ParameterType.INLINE_1) | p0 & 0xffff;
        label = null;
      }

      return new Param(address, ParameterType.INLINE_1, new int[] { inline }, ResolvedValue.unresolved(), label);
    }

    if((matcher = INLINE_2_MATCHER.matcher(paramString)).matches()) {
      final String val = matcher.group(1);

      final int inline;
      final String label;
      if(LABEL_PARAM_PATTERN.matcher(val).matches()) {
        final int p2 = this.parseInt(matcher.group(2));
        inline = this.packParam(ParameterType.INLINE_2, 0, 0, p2);
        label = val.substring(1);
      } else {
        final int value = this.parseInt(val);
        final int p0 = (value - opAddress) / 0x4;
        final int p2 = this.parseInt(matcher.group(2));
        inline = this.packParam(ParameterType.INLINE_2, 0, 0, p2) | p0 & 0xffff;
        label = null;
      }

      return new Param(address, ParameterType.INLINE_2, new int[] { inline }, ResolvedValue.unresolved(), label);
    }

    if((matcher = GAMEVAR_3_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_3, new int[] { this.packParam(ParameterType.GAMEVAR_3, p0, p1) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = INLINE_3_MATCHER.matcher(paramString)).matches()) {
      if(!matcher.group(1).equalsIgnoreCase(matcher.group(2))) {
        throw new RuntimeException("Invalid INLINE_3 def, addresses must match (" + matcher.group(1) + '/' + matcher.group(2) + ')');
      }

      final String val = matcher.group(1);

      final int inline;
      final String label;
      if(LABEL_PARAM_PATTERN.matcher(val).matches()) {
        final int p2 = this.parseInt(matcher.group(3));
        inline = this.packParam(ParameterType.INLINE_TABLE_1, 0, 0, p2);
        label = val.substring(1);
      } else {
        final int value = this.parseInt(val);
        final int p0 = (value - opAddress) / 0x4;
        final int p2 = this.parseInt(matcher.group(3));
        inline = this.packParam(ParameterType.INLINE_TABLE_1, 0, 0, p2) | p0 & 0xffff;
        label = null;
      }

      return new Param(address, ParameterType.INLINE_TABLE_1, new int[] { inline }, ResolvedValue.unresolved(), label);
    }

    // INLINE_4
    // OTHER_STORAGE

    if((matcher = GAMEVAR_ARRAY_3_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_3, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_3, p0, p1) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = GAMEVAR_ARRAY_4_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_4, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_4, p0, p1, p2) }, ResolvedValue.unresolved(), null);
    }

    // GAMEVAR_ARRAY_5
    // _12
    // INLINE_5

    if((matcher = INLINE_6_PATTERN.matcher(paramString)).matches()) {
      if(!matcher.group(1).equalsIgnoreCase(matcher.group(2))) {
        throw new RuntimeException("Invalid INLINE_6 def, addresses must match (" + matcher.group(1) + '/' + matcher.group(2) + ')');
      }

      final String val = matcher.group(1);

      final int inline;
      final String label;
      if(LABEL_PARAM_PATTERN.matcher(val).matches()) {
        final int p2 = this.parseInt(matcher.group(3));
        inline = this.packParam(ParameterType.INLINE_TABLE_3, 0, 0, p2);
        label = val.substring(1);
      } else {
        final int value = this.parseInt(val);
        final int p0 = (value - opAddress) / 0x4;
        final int p2 = this.parseInt(matcher.group(3));
        inline = this.packParam(ParameterType.INLINE_TABLE_3, 0, 0, p2) | p0 & 0xffff;
        label = null;
      }

      return new Param(address, ParameterType.INLINE_TABLE_3, new int[] { inline }, ResolvedValue.unresolved(), label);
    }

    // _15
    // _16
    // INLINE_7

    if((matcher = ID_PATTERN.matcher(paramString)).matches()) {
      final String id = matcher.group(1);
      final int[] packed = new int[ParameterType.ID.getWidth(id)];
      packed[0] = ParameterType.ID.opcode << 24 | id.length() << 16;

      for(int i = 0; i < id.length(); i++) {
        packed[1 + i / 4] |= (id.charAt(i) & 0xff) << i % 4 * 8;
      }

      return new Param(address, ParameterType.ID, packed, ResolvedValue.unresolved(), null);
    }

    if((matcher = REG_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.REG, new int[] { this.packParam(ParameterType.REG, p0) }, ResolvedValue.unresolved(), null);
    }

    if((matcher = REG_VAR_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.REG_VAR, new int[] { this.packParam(ParameterType.REG_VAR, p0) }, ResolvedValue.unresolved(), null);
    }

    if("null".equalsIgnoreCase(paramString)) {
      return new Param(address, ParameterType.REG_NULL, new int[] { this.packParam(ParameterType.REG_NULL) }, ResolvedValue.of(0), null);
    }

    throw new RuntimeException("Unknown param " + paramString);
  }

  private String[] splitParameters(final String parametersString) {
    final List<Integer> commas = new ArrayList<>();
    int bracketCount = 0;

    for(int i = 0; i < parametersString.length(); i++) {
      switch(parametersString.substring(i, i + 1)) {
        case "[" -> bracketCount++;

        case "]" -> {
          bracketCount--;

          if(bracketCount < 0) {
            throw new RuntimeException("Invalid parameters " + parametersString);
          }
        }

        case "," -> {
          if(bracketCount == 0) {
            commas.add(i);
          }
        }
      }
    }

    commas.add(parametersString.length());

    final String[] params = new String[commas.size()];
    int start = 0;

    for(int i = 0; i < params.length; i++) {
      params[i] = parametersString.substring(start, commas.get(i)).strip();
      start = commas.get(i) + 1;
    }

    return params;
  }

  private int parseInt(final String val) {
    if(NUMBER_PATTERN.matcher(val).matches()) {
      if(val.startsWith("0x")) {
        return Integer.parseUnsignedInt(val.substring(2), 16);
      }

      return Integer.parseInt(val);
    }

    throw new NumberFormatException("Invalid number " + val);
  }

  private int packParam(final ParameterType type, final int p0, final int p1, final int p2) {
    return type.opcode << 24 | (p2 & 0xff) << 16 | (p1 & 0xff) << 8 | p0 & 0xff;
  }

  private int packParam(final ParameterType type, final int p0, final int p1) {
    return this.packParam(type, p0, p1, 0);
  }

  private int packParam(final ParameterType type, final int p0) {
    return this.packParam(type, p0, 0, 0);
  }

  private int packParam(final ParameterType type) {
    return this.packParam(type, 0, 0, 0);
  }
}
