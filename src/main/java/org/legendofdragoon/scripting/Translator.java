package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Translator {
  private static final Logger LOGGER = LogManager.getFormatterLogger();

  private final Map<String, String> reindexedLabels = new HashMap<>();

  public boolean stripNames;
  public boolean stripComments;
  public boolean lineNumbers;

  public String translate(final Script script, final Meta meta) {
    final StringBuilder builder = new StringBuilder();

    // Sort LABEL_ labels in the order of their destinations
    final List<String> sortedLabels = script.labels.entrySet().stream()
      .sorted(Comparator.comparingInt(Map.Entry::getKey))
      .flatMap(e -> e.getValue().stream())
      .filter(label -> label.startsWith("LABEL_"))
      .toList();

    for(int i = 0; i < sortedLabels.size(); i++) {
      this.reindexedLabels.put(sortedLabels.get(i), "LABEL_" + i);
    }

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];
      if(!this.stripComments) {
        if(script.subs.contains(entry.address)) {
          builder.append("\n; SUBROUTINE\n");
        }

        if(script.subTables.contains(entry.address)) {
          builder.append("\n; SUBROUTINE TABLE\n");
        }

        if(script.forkJumps.contains(entry.address)) {
          builder.append("\n; FORK JMP\n");
        }

        if(script.warnings.containsKey(entry.address)) {
          builder.append("\n; WARNING: ").append(script.warnings.get(entry.address)).append('\n');
        }
      }

      if(script.labels.containsKey(entry.address)) {
        for(final String label : script.labels.get(entry.address)) {
          builder.append(this.getReindexedLabel(label)).append(":\n");
        }
      }

      if(entry instanceof final Entrypoint entrypoint) {
        builder.append("entrypoint :").append(entrypoint.destination).append('\n');
      } else if(entry instanceof final Data data) {
        if(this.lineNumbers) {
          builder.append(Integer.toHexString(data.address)).append(": ");
        }

        builder.append("data 0x%x".formatted(data.value)).append('\n');
      } else if(entry instanceof final PointerTable rel) {
        if(rel.labels.length == 0) {
          throw new RuntimeException("Empty jump table %x".formatted(rel.address));
        }

        for(int i = 0; i < rel.labels.length; i++) {
          builder.append("rel :").append(this.getReindexedLabel(rel.labels[i])).append('\n');
          entryIndex++;
        }

        entryIndex--;
      } else if(entry instanceof final LodString string) {
        final List<Map.Entry<Integer, List<String>>> overlappingLabels = script.labels.entrySet().stream()
          .filter(e -> e.getKey() > string.address && e.getKey() < string.address + (string.chars.length + 2) / 0x2 * 0x4) // +1 for terminator, +1 to round up
          .sorted(Comparator.comparingInt(Map.Entry::getKey))
          .toList();

        builder.append("data str[");

        if(overlappingLabels.isEmpty()) {
          builder.append(string);
        } else {
          // An unholy algorithm to split strings based on intersecting labels, since that's apparently a thing they did

          int currentIndex = 0;
          for(final Map.Entry<Integer, List<String>> overlappingLabel : overlappingLabels) {
            final int nextLabelIndex = (overlappingLabel.getKey() - string.address) / 0x2;

            builder.append(new LodString(0, Arrays.copyOfRange(string.chars, currentIndex, nextLabelIndex))).append("<noterm>]\n");

            for(final String label : overlappingLabel.getValue()) {
              builder.append(this.getReindexedLabel(label)).append(":\n");
            }

            builder.append("data str[");

            currentIndex = nextLabelIndex;
          }

          builder.append(new LodString(string.address + currentIndex * 0x2, Arrays.copyOfRange(string.chars, currentIndex, string.chars.length)));
        }

        builder.append("]\n");
        entryIndex += string.chars.length / 2;
      } else if(entry instanceof final Op op) {
        if(this.lineNumbers) {
          builder.append(Integer.toHexString(op.address)).append(": ");
        }

        builder.append(op.type.name);

        if(op.type == OpType.CALL) {
          if(!this.stripNames) {
            builder.append(' ').append(meta.methods[op.headerParam].name);
          } else {
            builder.append(' ').append(op.headerParam);
          }
        } else if(op.type.headerParamName != null) {
          builder.append(' ').append(this.buildHeaderParam(op));
        }

        if(op.type == OpType.WAIT_CMP_0 || op.type == OpType.JMP_CMP_0) {
          builder.append(", 0x0");
        }

        if(op.type == OpType.MOV_0) {
          builder.append(" 0x0,");
        }

        for(int paramIndex = 0; paramIndex < op.params.length; paramIndex++) {
          if(paramIndex != 0 || op.type.headerParamName != null) {
            builder.append(',');
          }

          builder.append(' ').append(this.buildParam(meta, op, op.params[paramIndex], paramIndex));
        }

        if(!this.stripComments) {
          if(op.type == OpType.CALL && meta.methods[op.headerParam].params.length != 0) {
            builder.append(" ; ").append(Arrays.stream(meta.methods[op.headerParam].params).map(Object::toString).collect(Collectors.joining(", ")));
          } else if (op.params.length != 0 || op.type.headerParamName != null) {
            builder.append(" ; ");

            if(op.type.headerParamName != null) {
              builder.append(op.type.headerParamName);

              if (op.params.length != 0) {
                builder.append(", ");
              }
            }

            builder.append(String.join(", ", op.type.getCommentParamNames()));
          }
        }

        builder.append('\n');
      } else if(!(entry instanceof Param)) {
        throw new RuntimeException("Unknown entry " + entry.getClass().getSimpleName());
      }
    }

    return builder.toString();
  }

  private String getReindexedLabel(final String label) {
    return this.reindexedLabels.getOrDefault(label, label);
  }

  private String buildHeaderParam(final Op op) {
    if(op.type == OpType.WAIT_CMP || op.type == OpType.WAIT_CMP_0 || op.type == OpType.JMP_CMP || op.type == OpType.JMP_CMP_0) {
      return switch(op.headerParam) {
        case 0 -> "<=";
        case 1 -> "<";
        case 2 -> "==";
        case 3 -> "!=";
        case 4 -> ">";
        case 5 -> ">=";
        case 6 -> "&";
        case 7 -> "!&";
        default -> "Unknown CMP operator " + op.headerParam;
      };
    }

    return "0x%x".formatted(op.headerParam);
  }

  private String buildParam(final Meta meta, final Op op, final Param param, final int paramIndex) {
    if(param.label != null) {
      final String label = ':' + this.getReindexedLabel(param.label);

      return switch(param.type) {
        case INLINE_2 -> "inl[%s[stor[%d]]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case INLINE_TABLE_1 -> "inl[%1$s[%1$s[stor[%2$d]]]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case INLINE_TABLE_2 -> "inl[%1$s[%1$s[stor[%2$d]] + stor[%3$d]]]".formatted(label, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
        case INLINE_TABLE_3 -> "inl[%1$s + inl[%1$s + 0x%2$x]]".formatted(label, param.rawValues[0] >> 16 & 0xff);
        case _12 -> throw new RuntimeException("Param type 0x12 not yet supported");
        case _15 -> throw new RuntimeException("Param type 0x15 not yet supported");
        case _16 -> throw new RuntimeException("Param type 0x16 not yet supported");
        case INLINE_TABLE_4 -> "inl[%1$s[%1$s[%2$d] + %3$d]]".formatted(label, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
        default -> "inl[" + label + ']';
      };
    }

    return switch(param.type) {
      case IMMEDIATE -> this.getImmediateParam(meta, op, paramIndex, param.rawValues[0]);
      case NEXT_IMMEDIATE -> this.getImmediateParam(meta, op, paramIndex, param.rawValues[1]);
      case STORAGE -> "stor[%d]".formatted(param.rawValues[0] & 0xff);
      case OTHER_OTHER_STORAGE -> "stor[stor[stor[%d], %d], %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case OTHER_STORAGE_OFFSET -> "stor[stor[%d], %d + stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_1 -> "var[%d]".formatted(param.rawValues[0] & 0xff);
      case GAMEVAR_2 -> "var[%d + stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_1 -> "var[%d][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_2 -> "var[%d + stor[%d]][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case INLINE_1 -> "inl[0x%x]".formatted(op.address + (short)param.rawValues[0] * 4);
      case INLINE_2 -> "inl[0x%x[stor[%d]]]".formatted(op.address + (short)param.rawValues[0] * 4, param.rawValues[0] >> 16 & 0xff);
      case INLINE_TABLE_1 -> "inl[0x%1$x[0x%1$x[stor[%2$d]]]]".formatted(op.address + (short)param.rawValues[0] * 4, param.rawValues[0] >> 16 & 0xff);
      case INLINE_TABLE_2 -> "inl[0x%1$x[0x%1$x[stor[%2$d]] + stor[%3$d]]]".formatted(op.address, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);
      case OTHER_STORAGE -> "stor[stor[%d], %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff + param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_3 -> "var[%d + %d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_3 -> "var[%d][%d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff);
      case GAMEVAR_ARRAY_4 -> "var[%d + stor[%d]][%d]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case GAMEVAR_ARRAY_5 -> "var[%d + %d][stor[%d]]".formatted(param.rawValues[0] & 0xff, param.rawValues[0] >> 8 & 0xff, param.rawValues[0] >> 16 & 0xff);
      case _12 -> throw new RuntimeException("Param type 0x12 not yet supported");
      case INLINE_3 -> "inl[0x%x]".formatted(op.address + ((short)param.rawValues[0] + param.rawValues[0] >> 16 & 0xff) * 4);
      case INLINE_TABLE_3 -> "inl[0x%1$x[inl[0x%1$x + 0x%2$x]]]".formatted(op.address + (short)param.rawValues[0] * 4, (param.rawValues[0] >> 16 & 0xff) * 4);
      case _15 -> throw new RuntimeException("Param type 0x15 not yet supported");
      case _16 -> throw new RuntimeException("Param type 0x16 not yet supported");
      case INLINE_TABLE_4 -> "inl[0x%1$x[0x%1$x[%2$d] + %3$d]]".formatted(op.address, param.rawValues[1] & 0xff, param.rawValues[1] >> 8 & 0xff);

      case REG -> "reg[%d]".formatted(param.rawValues[0] & 0xff);
      case ID -> {
        final char[] chars = new char[param.rawValues[0] >>> 16 & 0xff];
        for(int i = 0; i < chars.length; i++) {
          chars[i] = (char)(param.rawValues[1 + i / 4] >>> i % 4 * 8 & 0xff);
        }

        final String id = new String(chars);

        yield "id[" + id + ']';
      }
    };
  }

  private String getImmediateParam(final Meta meta, final Op op, final int paramIndex, final int value) {
    if(op.type == OpType.CALL && meta.enums.containsKey(meta.methods[op.headerParam].params[paramIndex].type)) {
      return meta.enums.get(meta.methods[op.headerParam].params[paramIndex].type)[value];
    }

    return "0x%x".formatted(value);
  }
}
