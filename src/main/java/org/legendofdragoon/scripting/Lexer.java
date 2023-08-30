package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

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
  private static final String NUMBER_SUBPATTERN = "0x[a-f\\d]{1,8}|\\d{1,10}";
  private static final Pattern LINE_PATTERN = Pattern.compile("^\\s*?(?:[a-f0-9]+\\s+)?([a-z]\\w*?)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBER_PATTERN = Pattern.compile("^(?:" + NUMBER_SUBPATTERN + ")$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_PATTERN = Pattern.compile("^(\\w+):$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_PARAM_PATTERN = Pattern.compile("^:(\\w+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CALL_PATTERN = Pattern.compile("^[a-z_]\\w*::[a-z_]\\w*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern STORAGE_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern OTHER_OTHER_STORAGE_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + "\\d)\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern OTHER_STORAGE_OFFSET_PATTERN = Pattern.compile("^stor\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?,\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GAMEVAR_1_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GAMEVAR_2_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GAMEVAR_ARRAY_1_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GAMEVAR_ARRAY_2_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?\\+\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern INLINE_1_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern INLINE_2_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern INLINE_3_MATCHER = Pattern.compile("^inl\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + "|:\\w+)\\s*?\\[\\s*?stor\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?]\\s*?]\\s*?]$", Pattern.CASE_INSENSITIVE);

  private static final Pattern GAMEVAR_ARRAY_3_PATTERN = Pattern.compile("^var\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]\\s*?\\[\\s*?(" + NUMBER_SUBPATTERN + ")\\s*?]$", Pattern.CASE_INSENSITIVE);

  private final ScriptMeta meta;

  public Lexer(ScriptMeta meta) {
    this.meta = meta;
  }

  public Script lex(final String source) {
    final List<String> lines = source.lines().map(this::removeComment).map(String::strip).filter(Predicate.not(String::isBlank)).toList();

    final List<Entry> entries = new ArrayList<>();
    final Map<String, Integer> labels = new HashMap<>();
    final Set<String> tables = new HashSet<>();

    for(final String line : lines) {
      final int address = entries.size() * 0x4;

      final Matcher labelMatcher = LABEL_PATTERN.matcher(line);
      if(labelMatcher.matches()) {
        labels.put(labelMatcher.group(1), address);
        continue;
      }

      final Entry entry = this.lexLine(address, line);
      entries.add(entry);

      if(entry instanceof final Op op) {
        if(op.type == OpType.GOSUB_TABLE) {
          tables.add(op.params[1].label);
        }

        for(final Param param : op.params) {
          for(int i = 0; i < param.type.width; i++) {
            entries.add(param);
          }
        }
      }
    }

    // Rebind inline parameters to their label's address
    for(final Entry entry : entries) {
      if(entry instanceof final Op op) {
        for(final Param param : op.params) {
          if(param.label != null) {
            final int address = labels.get(param.label);

            switch(param.type) {
              case INLINE_1, INLINE_2, INLINE_3 -> param.rawValues[0] |= (address - op.address) / 0x4 & 0xffff;
              case INLINE_4, INLINE_5, INLINE_6, INLINE_7 -> throw new RuntimeException("Need to implement label bindings for " + param.type);
            }
          }
        }
      }
    }

    final List<Integer> tableOffsets = tables.stream().map(key -> labels.get(key) / 0x4).sorted(Comparator.reverseOrder()).toList();
    int maxOffset = entries.size(); // Used to know when to end jump tables that run into each other

    // Fix jump table addresses (note: these addresses are sorted from last to first)
    for(final int tableOffset : tableOffsets) {
      final List<String> newLabels = new ArrayList<>();
      int offset = tableOffset;

      while(offset < maxOffset && entries.get(offset) instanceof final PointerTable table) {
        newLabels.add(table.labels[0]);
        offset++;
      }

      entries.set(tableOffset, new PointerTable(tableOffset * 0x4, newLabels.toArray(String[]::new)));

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
        } else if("data".equalsIgnoreCase(command)) {
          return new Data(address, this.parseInt(paramsStr));
        } else if("rel".equalsIgnoreCase(command)) {
          if(!LABEL_PARAM_PATTERN.matcher(paramsStr).matches()) {
            throw new RuntimeException("Invalid relative pointer label " + paramsStr);
          }

          return new PointerTable(address, new String[] { paramsStr.substring(1) });
        } else {
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
        }
      } catch(final NumberFormatException e) {
        System.err.println(e.getMessage());
      }
    }

    throw new RuntimeException("Invalid line \"" + line + '"');
  }

  private Param[] parseParams(int opAddress, int address, final OpType opType, final String paramsString) {
    final String[] paramStrings = this.splitParameters(paramsString);
    final Param[] params = new Param[paramStrings.length];

    for(int i = 0; i < params.length; i++) {
      final Param param = this.parseParam(opAddress, address, paramStrings[i]);
      params[i] = param;

      // If we have a header param, the first param returns will be a pseudo-param that doesn't advance the address since it's part of the header
      if(i != 0 || opType.headerParamName == null) {
        address += param.type.width * 0x4;
      }
    }

    return params;
  }

  private Param parseParam(final int opAddress, final int address, String paramString) {
    // Convert call function refs to ints
    if(CALL_PATTERN.matcher(paramString).matches()) {
      for(int i = 0; i < this.meta.methods.length; i++) {
        if(this.meta.methods[i].name.equalsIgnoreCase(paramString)) {
          paramString = Integer.toString(i);
          break;
        }
      }
    }

    // Immediates
    try {
      final int value = this.parseInt(paramString);

      if((value & 0xff00_0000) == 0) {
        return new Param(address, ParameterType.IMMEDIATE, new int[] { value }, OptionalInt.of(value), null);
      } else {
        return new Param(address, ParameterType.NEXT_IMMEDIATE, new int[] { this.packParam(ParameterType.NEXT_IMMEDIATE), value }, OptionalInt.of(value), null);
      }
    } catch(final NumberFormatException ignored) { }

    Matcher matcher;
    if((matcher = STORAGE_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.STORAGE, new int[] { this.packParam(ParameterType.STORAGE, p0) }, OptionalInt.empty(), null);
    }

    if((matcher = OTHER_OTHER_STORAGE_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.OTHER_OTHER_STORAGE, new int[] { this.packParam(ParameterType.OTHER_OTHER_STORAGE, p0, p1, p2) }, OptionalInt.empty(), null);
    }

    if((matcher = OTHER_STORAGE_OFFSET_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.OTHER_STORAGE_OFFSET, new int[] { this.packParam(ParameterType.OTHER_STORAGE_OFFSET, p0, p1, p2) }, OptionalInt.empty(), null);
    }

    if((matcher = GAMEVAR_1_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      return new Param(address, ParameterType.GAMEVAR_1, new int[] { this.packParam(ParameterType.GAMEVAR_1, p0) }, OptionalInt.empty(), null);
    }

    if((matcher = GAMEVAR_2_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_2, new int[] { this.packParam(ParameterType.GAMEVAR_1, p0, p1) }, OptionalInt.empty(), null);
    }

    if((matcher = GAMEVAR_ARRAY_1_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_1, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_1, p0, p1) }, OptionalInt.empty(), null);
    }

    if((matcher = GAMEVAR_ARRAY_2_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      final int p2 = this.parseInt(matcher.group(3));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_2, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_2, p0, p1, p2) }, OptionalInt.empty(), null);
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

      return new Param(address, ParameterType.INLINE_1, new int[] { inline }, OptionalInt.empty(), label);
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

      return new Param(address, ParameterType.INLINE_2, new int[] { inline }, OptionalInt.empty(), label);
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
        inline = this.packParam(ParameterType.INLINE_3, 0, 0, p2);
        label = val.substring(1);
      } else {
        final int value = this.parseInt(val);
        final int p0 = (value - opAddress) / 0x4;
        final int p2 = this.parseInt(matcher.group(3));
        inline = this.packParam(ParameterType.INLINE_3, 0, 0, p2) | p0 & 0xffff;
        label = null;
      }

      return new Param(address, ParameterType.INLINE_3, new int[] { inline }, OptionalInt.empty(), label);
    }

    // INLINE_4
    // OTHER_STORAGE

    if((matcher = GAMEVAR_ARRAY_3_PATTERN.matcher(paramString)).matches()) {
      final int p0 = this.parseInt(matcher.group(1));
      final int p1 = this.parseInt(matcher.group(2));
      return new Param(address, ParameterType.GAMEVAR_ARRAY_3, new int[] { this.packParam(ParameterType.GAMEVAR_ARRAY_3, p0, p1) }, OptionalInt.empty(), null);
    }

    // GAMEVAR_ARRAY_3
    // GAMEVAR_ARRAY_4
    // GAMEVAR_ARRAY_5
    // _12
    // INLINE_5
    // INLINE_6
    // _15
    // _16
    // INLINE_7

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
    return type.ordinal() << 24 | (p2 & 0xff) << 16 | (p1 & 0xff) << 8 | p0 & 0xff;
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