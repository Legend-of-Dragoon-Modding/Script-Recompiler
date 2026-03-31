package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
import org.legendofdragoon.scripting.tokens.Op;
import org.legendofdragoon.scripting.tokens.Param;
import org.legendofdragoon.scripting.tokens.PointerTable;
import org.legendofdragoon.scripting.tokens.Script;

public class Assembler {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Assembler.class);

  public int[] assemble(final Script script) {
    final int[] out = new int[script.entries.length];

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];

      if(entry instanceof final Entrypoint entrypoint) {
        out[entryIndex] = this.findEntrypointAddress(script, entrypoint);
      } else if(entry instanceof final Data data) {
        out[entryIndex] = data.value;
      } else if(entry instanceof final LodString data) {
        for(int i = 0; i < data.chars.length; i += 2) {
          out[entryIndex] = data.chars[i];

          if(i + 1 < data.chars.length) {
            out[entryIndex] |= data.chars[i + 1] << 16;
          }

          entryIndex++;
        }

        entryIndex--; // Loop will account for one increment
      } else if(entry instanceof final PointerTable rel) {
        if(rel.labels.length == 0) {
//          throw new RuntimeException("Empty pointer table at 0x%x".formatted(rel.address));
          LOGGER.warn("Empty pointer table at 0x%x", rel.address);
          continue;
        }

        for(final String label : rel.labels) {
          final int destAddress = script.findLabelAddress(label);
          out[entryIndex++] = (destAddress - rel.address) / 0x4;
        }

        entryIndex--; // Loop will account for one increment
      } else if(entry instanceof final Op op) {
        out[entryIndex] = op.type.opcode | op.params.length << 8 | op.headerParam << 16;

        for(final Param param : op.params) {
          param.resolveValues(script);

          for(int i = 0; i < param.type.getWidth(param); i++) {
            out[++entryIndex] = param.rawValues[i];
          }
        }
      } else {
        throw new RuntimeException("Unknown entry " + entry.getClass().getSimpleName() + " at index " + entryIndex);
      }
    }

    return out;
  }

  private int findEntrypointAddress(final Script script, final Entrypoint entrypoint) {
    return script.findLabelAddress(entrypoint.destination);
  }
}
