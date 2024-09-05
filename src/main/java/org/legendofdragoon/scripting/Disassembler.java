package org.legendofdragoon.scripting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.legendofdragoon.scripting.meta.Meta;
import org.legendofdragoon.scripting.resolution.Register;
import org.legendofdragoon.scripting.resolution.RegisterSet;
import org.legendofdragoon.scripting.resolution.ResolvedValue;
import org.legendofdragoon.scripting.resolution.ScriptRegisters;
import org.legendofdragoon.scripting.tokens.Data;
import org.legendofdragoon.scripting.tokens.Entry;
import org.legendofdragoon.scripting.tokens.Entrypoint;
import org.legendofdragoon.scripting.tokens.LodString;
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
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

public class Disassembler {
  private static final Logger LOGGER = LogManager.getFormatterLogger();
  private static final Marker DISASSEMBLY = MarkerManager.getMarker("DISASSEMBLY");

  private final Meta meta;
  private State state;

  public final List<Integer> extraBranches = new ArrayList<>();
  public final Map<Integer, Integer> tableLengths = new HashMap<>();

  public Disassembler(final Meta meta) {
    this.meta = meta;
  }

  public Script disassemble(final byte[] bytes) {
    this.state = new State(bytes);

    final Script script = new Script(this.state.length() / 4);

    this.getEntrypoints(script);

    for(final int entrypoint : script.entrypoints) {
      this.probeBranch(script, entrypoint);
    }

    for(int entryIndex = 0; entryIndex < script.entries.length; entryIndex++) {
      final Entry entry = script.entries[entryIndex];

      if(entry instanceof final PointerTable rel) {
        entryIndex++;

        for(int labelIndex = 1; labelIndex < rel.labels.length; labelIndex++) {
          // If this table overruns something else, bail out
          if(
            script.entries[entryIndex] != null && !(script.entries[entryIndex] instanceof Data) ||
            script.labels.containsKey(entryIndex * 4) // If something else points to data here, the table must have ended
          ) {
            LOGGER.warn("Jump table overrun at %x", entry.address);

            for(int toRemove = labelIndex; toRemove < rel.labels.length; toRemove++) {
              // If this is the last usage of the label, remove it
              if(script.labelUsageCount.get(rel.labels[toRemove]) <= 1) {
                for(final List<String> labels : script.labels.values()) {
                  labels.remove(rel.labels[toRemove]);
                }
              }
            }

            rel.labels = Arrays.copyOfRange(rel.labels, 0, labelIndex);
            entryIndex--; // Backtrack so we can process the data we collided with
            break;
          }

          entryIndex++;
        }

        entryIndex--;
      }
    }

    for(final int extraBranch : this.extraBranches) {
      this.probeBranch(script, extraBranch);
    }

    script.buildStrings.forEach(Runnable::run);

    this.fillStrings(script);
    this.fillData(script);

    LOGGER.info(DISASSEMBLY, "Probing complete");

    return script;
  }

  private void probeBranch(final Script script, final int offset) {
    // Made our way into another branch, no need to parse again
    if(script.branches.contains(offset)) {
      return;
    }

    LOGGER.info(DISASSEMBLY, "Probing branch %x", offset);
    script.branches.add(offset);

    final ScriptRegisters registers = script.pushRegisters();

    final int oldHeaderOffset = this.state.headerOffset();
    final int oldCurrentOffset = this.state.currentOffset();

    this.state.jump(offset);

    outer:
    while(this.state.hasMore()) {
      this.state.step();

      final Op op = this.parseHeader(this.state.currentOffset());

      if(op == null) { // Invalid op or invalid param count
        //TODO ran into invalid code
        break;
      }

      this.state.advance();

      int entryOffset = this.state.headerOffset() / 4;
      script.entries[entryOffset++] = op;

      // Parse params
      for(int i = 0; i < op.params.length; i++) {
        final ParameterType paramType = ParameterType.byOpcode(this.state.paramType());

        // Read raw values for param
        final int[] rawValues = new int[paramType.getWidth(this.state)];
        for(int n = 0; n < paramType.getWidth(this.state); n++) {
          rawValues[n] = this.state.wordAt(this.state.currentOffset() + n * 0x4);
        }

        // Try to resolve param values and build param
        final int paramOffset = this.state.currentOffset();
        final ResolvedValue resolved = this.parseParamValue(registers, this.state, paramType);
        final Param param = new Param(paramOffset, paramType, rawValues, resolved, paramType.isInline() && resolved.isPresent() ? script.addLabel(resolved.get(), "LABEL_" + script.getLabelCount()) : null);

        // Fill entries for param
        for(int n = 0; n < paramType.getWidth(param); n++) {
          script.entries[entryOffset++] = param;
        }

        // Look for inline pointers that are out of range
        if(paramType.isInline() && resolved.orElse(0) >= script.entries.length * 4) {
          script.addWarning(op.address, "Pointer at 0x%x destination is past the end of the script, replacing with 0".formatted(paramOffset));
          op.params[i] = new Param(paramOffset, ParameterType.IMMEDIATE, new int[] {ParameterType.IMMEDIATE.opcode << 24}, ResolvedValue.of(0), null);
          continue;
        }

        op.params[i] = param;

        // Handle jump table params
        if(paramType.isInlineTable() && op.type != OpType.GOSUB_TABLE && op.type != OpType.JMP_TABLE) {
          if(op.type == OpType.CALL && !"none".equalsIgnoreCase(this.meta.methods[op.headerParam].params[i].branch)) {
            final Set<Integer> tableDestinations = switch(this.meta.methods[op.headerParam].params[i].branch.toLowerCase()) {
              case "jump" -> script.jumpTableDests;
              case "subroutine" -> script.subs;
              case "fork_jump" -> script.forkJumps;
              default -> {
                LOGGER.warn("Unknown branch type %s", this.meta.methods[op.headerParam].params[i].branch);
                yield new HashSet<>();
              }
            };

            param.resolvedValue.ifPresent(tableAddress -> this.probeTableOfBranches(script, tableDestinations, tableAddress, op.params[0].resolvedValue));
          } else {
            final int finalI = i;
            param.resolvedValue.ifPresent(tableAddress -> this.handlePointerTable(script, op, finalI, tableAddress, script.buildStrings, op.params[0].resolvedValue));
          }
        } else if(op.type == OpType.CALL && "string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[i].type)) {
          // Resolve strings that are pointed to by a non-table inline
          param.resolvedValue.ifPresent(stringAddress ->
            script.buildStrings.add(() ->
              script.strings.add(new StringInfo(stringAddress, -1)) // We don't know the length
            )
          );
        }
      }

      // Fill in known values for registers
      switch(op.type) {
        case MOV, SWAP_BROKEN -> this.copyRegister(registers, op, 1, 0);
        case MOV_0 -> this.setRegister(registers, op, 0, 0);
        case AND -> this.mergeRegister(registers, op, 1, 0, (operand, and) -> operand & and);
        case OR -> this.mergeRegister(registers, op, 1, 0, (operand, or) -> operand | or);
        case XOR -> this.mergeRegister(registers, op, 1, 0, (operand, xor) -> operand ^ xor);
        case ANDOR -> {
          this.mergeRegister(registers, op, 2, 0, (operand, and) -> operand & and);
          this.mergeRegister(registers, op, 2, 1, (operand, or) -> operand | or);
        }
        case NOT -> this.modifyRegister(registers, op, 0, value -> ~value);
        case SHL -> this.mergeRegister(registers, op, 1, 0, (val, shift) -> val << shift);
        case SHR -> this.mergeRegister(registers, op, 1, 0, (val, shift) -> val >> shift);
        case ADD -> this.mergeRegister(registers, op, 1, 0, Integer::sum);
        case SUB -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> operand - amount);
        case SUB_REV -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> amount - operand);
        case INCR -> this.modifyRegister(registers, op, 0, operand -> operand + 1);
        case DECR -> this.modifyRegister(registers, op, 0, operand -> operand - 1);
        case NEG -> this.modifyRegister(registers, op, 0, operand -> -operand);
        case ABS -> this.modifyRegister(registers, op, 0, Math::abs);
        case MUL -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> operand * amount);
        case DIV -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> MathHelper.safeDiv(operand, amount));
        case DIV_REV -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> MathHelper.safeDiv(amount, operand));
        case MOD, MOD43 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> amount != 0 ? operand % amount : 0);
        case MOD_REV, MOD_REV44 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> operand != 0 ? amount % operand : 0);
        case MUL_12 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (operand >> 4) * (amount >> 4) >> 4);
        case DIV_12 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (operand << 4) / amount << 8);
        case DIV_12_REV -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (amount << 4) / operand << 8);
        case SQRT -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (int)Math.sqrt(amount));
        case RAND -> this.setRegister(registers, op, 1, register -> op.params[0].resolvedValue.ifPresentOrElse(value -> register.range(0, value), register::unknown));
        case SIN_12 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (short)(Math.sin(MathHelper.psxDegToRad(amount)) * 0x1000));
        case COS_12 -> this.mergeRegister(registers, op, 1, 0, (operand, amount) -> (short)(Math.cos(MathHelper.psxDegToRad(amount)) * 0x1000));
        case ATAN2_12 -> this.setRegister(registers, op, 2, dest ->
          op.params[0].resolvedValue.ifPresentOrElse(reg0 ->
            op.params[1].resolvedValue.ifPresentOrElse(reg1 ->
              dest.known(MathHelper.radToPsxDeg(MathHelper.atan2(reg0, reg1))),
              dest::unknown
            ),
            dest::unknown
          )
        );

        case CALL -> {
          final Meta.ScriptMethod method = this.meta.methods[op.headerParam];

          if(this.meta.methods[op.headerParam].params.length != op.params.length) {
//            throw new RuntimeException("CALL " + op.headerParam + " (" + this.meta.methods[op.headerParam] + ") has wrong number of args! " + method.params.length + '/' + op.params.length);
          }

          for(int i = 0; i < this.meta.methods[op.headerParam].params.length; i++) {
            final Meta.ScriptParam param = method.params[i];

            if(!"none".equalsIgnoreCase(param.branch)) {
              op.params[i].resolvedValue.ifPresentOrElse(offset1 -> {
                if("gosub".equalsIgnoreCase(param.branch)) {
                  script.subs.add(offset1);
                } else if("fork_jump".equalsIgnoreCase(param.branch)) {
                  script.forkJumps.add(offset1);
                }

                this.probeBranch(script, offset1);
              }, () -> LOGGER.warn("Skipping CALL at %x due to unknowable parameter", this.state.headerOffset()));
            }
          }
        }

        case JMP -> {
          op.params[0].resolvedValue.ifPresentOrElse(offset1 -> this.probeBranch(script, offset1), () -> LOGGER.warn("Skipping JUMP at %x due to unknowable parameter", this.state.headerOffset()));

          if(op.params[0].resolvedValue.isPresent()) {
            break outer;
          }
        }

        case JMP_CMP, JMP_CMP_0 -> {
          op.params[op.params.length - 1].resolvedValue.ifPresentOrElse(addr -> {
            this.probeBranch(script, this.state.currentOffset());
            this.probeBranch(script, addr);
          }, () ->
            LOGGER.warn("Skipping %s at %x due to unknowable parameter", op.type, this.state.headerOffset())
          );

          // Jumps are terminal
          break outer;
        }

        case JMP_TABLE -> {
          op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> {
            if(tableOffset != 0) { // Table out of bounds gets replaced with 0 above
              if(op.params[1].type.isInlineTable()) {
                this.probeTableOfTables(script, script.jumpTableDests, tableOffset, op.params[0].resolvedValue);
              } else {
                this.probeTableOfBranches(script, script.jumpTableDests, tableOffset, op.params[0].resolvedValue);
              }
            }
          }, () -> LOGGER.warn("Skipping JMP_TABLE at %x due to unknowable parameter", this.state.headerOffset()));

          // Jumps are terminal
          break outer;
        }

        case GOSUB -> {
          op.params[0].resolvedValue.ifPresentOrElse(offset1 -> {
            script.subs.add(offset1);
            this.probeBranch(script, offset1);
          }, () -> LOGGER.warn("Skipping GOSUB at %x due to unknowable parameter", this.state.headerOffset()));

          //TODO for now, we can't track what was modified by the subroutine, so we have to revert everything to unknown
          registers.getDecompState().clear();
        }

        case GOSUB_TABLE -> {
          op.params[1].resolvedValue.ifPresentOrElse(tableOffset -> {
            if(tableOffset != 0) { // Table out of bounds gets replaced with 0 above
              if(op.params[1].type.isInlineTable()) {
                this.probeTableOfTables(script, script.subs, tableOffset, op.params[0].resolvedValue);
              } else {
                this.probeTableOfBranches(script, script.subs, tableOffset, op.params[0].resolvedValue);
              }
            }
          }, () -> LOGGER.warn("Skipping GOSUB_TABLE at %x due to unknowable parameter", this.state.headerOffset()));

          //TODO for now, we can't track what was modified by the subroutine, so we have to revert everything to unknown
          registers.getDecompState().clear();
        }

        case REWIND, RETURN, DEALLOCATE, DEALLOCATE82, CONSUME -> {
          break outer;
        }

        case FORK -> op.params[1].resolvedValue.ifPresentOrElse(offset1 -> {
          script.forkJumps.add(offset1);
          this.probeBranch(script, offset1);
        }, () -> LOGGER.warn("Skipping FORK at %x due to unknowable parameter", this.state.headerOffset()));
      }
    }

    script.popRegisters();

    this.state.headerOffset(oldHeaderOffset);
    this.state.currentOffset(oldCurrentOffset);
  }

  private void probeTableOfTables(final Script script, final Set<Integer> tableDestinations, final int tableAddress, final ResolvedValue length) {
    this.probeTable(script, script.subTables, tableDestinations, tableAddress, subtableAddress -> !this.isProbablyOp(script, subtableAddress), subtableAddress -> this.probeTableOfBranches(script, tableDestinations, subtableAddress, ResolvedValue.unresolved()), length);
  }

  private void probeTableOfBranches(final Script script, final Set<Integer> tableDestinations, final int subtableAddress, final ResolvedValue length) {
    this.probeTable(script, script.subTables, tableDestinations, subtableAddress, this::isValidOp, branchAddress -> this.probeBranch(script, branchAddress), length);
  }

  private void probeTable(final Script script, final Set<Integer> tables, final Set<Integer> tableDestinations, final int tableAddress, final Predicate<Integer> destinationAddressHeuristic, final Consumer<Integer> visitor, final ResolvedValue length) {
    if(tables.contains(tableAddress)) {
      return;
    }

    tables.add(tableAddress);

    final LengthPredicate lengthPredicate;
    if(this.tableLengths.containsKey(tableAddress)) { // Explicit table length
      lengthPredicate = (entryIndex, entryAddress, earliestDestination, latestDestination) ->
        entryIndex < this.tableLengths.get(tableAddress);
    } else if(length.isRange()) { // Table length determined by possible range of register
      lengthPredicate = (entryIndex, entryAddress, earliestDestination, latestDestination) ->
        entryIndex < length.max();
    } else { // Heuristic that only allows all positive or all negative pointers (breaks a few tables like in Kazas garbage room) but yields much better results overall)
      lengthPredicate = (entryIndex, entryAddress, earliestDestination, latestDestination) ->
        this.state.wordAt(entryAddress) > 0 ? entryAddress < earliestDestination : entryAddress > latestDestination;
    }

    int earliestDestination = this.state.length();
    int latestDestination = 0;
    final List<Integer> destinations = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    for(
      int entryAddress = tableAddress, entryIndex = 0;
      entryAddress <= this.state.length() - 4 && script.entries[entryAddress / 4] == null && lengthPredicate.get(entryIndex, entryAddress, earliestDestination, latestDestination) && (!this.isProbablyOp(script, entryAddress) || this.isValidOp(tableAddress + this.state.wordAt(entryAddress) * 0x4));
      entryAddress += 0x4, entryIndex++
    ) {
      final int destAddress = tableAddress + this.state.wordAt(entryAddress) * 0x4;

      if(destAddress < 0x4 || destAddress > this.state.length() - 0x4) {
        break;
      }

      if(!destinationAddressHeuristic.test(destAddress)) {
        break;
      }

      if(earliestDestination > destAddress) {
        earliestDestination = destAddress;
      }

      if(latestDestination < destAddress) {
        latestDestination = destAddress;
      }

      tableDestinations.add(destAddress);
      destinations.add(destAddress);
      labels.add(script.addLabel(destAddress, "JMP_%x_%d".formatted(tableAddress, labels.size())));
    }

    if(labels.isEmpty()) {
      throw new RuntimeException("Empty table at 0x%x".formatted(tableAddress));
    }

    script.entries[tableAddress / 0x4] = new PointerTable(tableAddress, this.state.wordAt(tableAddress), labels.toArray(String[]::new));

    // Visit tables in reverse order so that it's easier to determine where tables end
    destinations.stream().distinct().sorted(Comparator.reverseOrder()).forEach(visitor);
  }

  private void handlePointerTable(final Script script, final Op op, final int paramIndex, final int tableAddress, final List<Runnable> buildStrings, final ResolvedValue length) {
    if(tableAddress / 4 >= script.entries.length) {
      LOGGER.warn("Op %s param %d points to invalid pointer table 0x%x", op, paramIndex, tableAddress);
      return;
    }

    if(script.entries[tableAddress / 0x4] != null) {
      return;
    }

    final List<Integer> destinations = new ArrayList<>();
    int entryCount = 0;

    int earliestDestination = this.state.length();
    int latestDestination = 0;
    for(
      int entryAddress = tableAddress, entryIndex = 0;
      entryAddress <= this.state.length() - 4 && script.entries[entryAddress / 4] == null && (length.isRange() ? entryIndex < length.max() : (this.state.wordAt(entryAddress) > 0 ? entryAddress < earliestDestination : entryAddress > latestDestination));
      entryAddress += 0x4, entryIndex++
    ) {
      int destination = tableAddress + this.state.wordAt(entryAddress) * 0x4;

      if(op.type == OpType.CALL && "string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        if(script.entries[entryAddress / 4] instanceof Op) {
          break;
        }

        if(this.isProbablyOp(script, entryAddress)) {
          boolean foundTerminator = false;

          // Look for a string terminator at the destination
          for(int i = destination / 4; i < destination / 4 + 300; i++) {
            // We ran into another entry or the end of the script
            if(i >= script.entries.length || script.entries[i] != null) {
              break;
            }

            final int word = this.state.wordAt(i * 0x4);
            if((word & 0xffff) == 0xa0ff || (word >> 16 & 0xffff) == 0xa0ff) {
              foundTerminator = true;
              break;
            }
          }

          if(!foundTerminator) {
            break;
          }
        }
      } else if(this.isProbablyOp(script, entryAddress)) {
        break;
      }

      if(destination >= this.state.length() - 0x4) {
        break;
      }

      if(earliestDestination > destination) {
        earliestDestination = destination;
      }

      if(latestDestination < destination) {
        latestDestination = destination;
      }

      if(op.type == OpType.GOSUB_TABLE || op.type == OpType.JMP_TABLE) {
        destination = tableAddress + this.state.wordAt(destination) * 0x4;
      }

      destinations.add(destination);
      entryCount++;
    }

    final String[] labels = new String[entryCount];
    for(int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
      labels[entryIndex] = script.addLabel(destinations.get(entryIndex), "PTR_%x_%d".formatted(tableAddress, entryIndex));
    }

    final PointerTable table = new PointerTable(tableAddress, this.state.wordAt(tableAddress), labels);
    script.entries[tableAddress / 0x4] = table;

    // Add string entries if appropriate
    if(op.type == OpType.CALL) {
      if("string".equalsIgnoreCase(this.meta.methods[op.headerParam].params[paramIndex].type)) {
        buildStrings.add(() -> {
          //IMPORTANT: we need to remove any extra elements that were truncated by the table overrun detector
          while(destinations.size() > table.labels.length) {
            destinations.removeLast();
          }

          final List<Integer> sorted = destinations.stream()
            .distinct()
            .sorted(Integer::compareTo)
            .toList();

          for(int i = 0; i < sorted.size(); i++) {
            if(i < sorted.size() - 1) {
              script.strings.add(new StringInfo(sorted.get(i), sorted.get(i + 1) - sorted.get(i))); // String length is next string - this string
            } else {
              script.strings.add(new StringInfo(sorted.get(i), -1)); // We don't know the length
            }
          }
        });
      }
    }
  }

  private void fillStrings(final Script script) {
    for(final StringInfo string : script.strings) {
      this.fillString(script, string.start, string.maxLength);
    }
  }

  private void fillString(final Script script, final int address, final int maxLength) {
    final List<Integer> chars = new ArrayList<>();

    for(int i = 0; i < (maxLength != -1 ? maxLength : script.entries.length * 0x4 - address); i++) {
      final int chr = this.state.wordAt(address + i / 2 * 0x4) >>> i % 2 * 16 & 0xffff;

      // String end
      if(chr == 0xa0ff) {
        break;
      }

      chars.add(chr);
    }

    final LodString string = new LodString(address, chars.stream().mapToInt(Integer::intValue).toArray());

    for(int i = 0; i < Math.max(1, string.chars.length / 2); i++) {
      script.entries[address / 0x4 + i] = string;
    }
  }

  private void fillData(final Script script) {
    for(int i = 0; i < script.entries.length; i++) {
      if(script.entries[i] == null) {
        script.entries[i] = new Data(i * 0x4, this.state.wordAt(i * 0x4));
      }
    }
  }

  private void getEntrypoints(final Script script) {
    for(int i = 0; i < 0x20 && this.state.hasMore(); i++) { // Most have 0x10, some have less, player_combat_script is the only one I've seen with 0x20
      final int entrypoint = this.state.currentWord();

      if(!this.isValidOp(entrypoint)) {
        break;
      }

      final String label = "ENTRYPOINT_" + i;

      script.entries[i] = new Entrypoint(i * 0x4, label);
      script.entrypoints.add(entrypoint);
      script.allEntrypoints.add(entrypoint);
      script.addUniqueLabel(entrypoint, label);
      this.state.advance();
    }
  }

  private Op parseHeader(final int offset) {
    if(offset > this.state.length() - 4) {
      return null;
    }

    final int opcode = this.state.wordAt(offset);
    final OpType type = OpType.byOpcode(opcode & 0xff);

    if(type == null) {
      return null;
    }

    // CALL with function index out of range
    if(type == OpType.CALL && ((opcode >>> 16) >= 1024)) {
      return null;
    }

    //TODO once we implement all subfuncs, add their param counts too
    final int paramCount = opcode >> 8 & 0xff;
    if(type != OpType.CALL && type.params.length != paramCount) {
      return null;
    }

    final int opParam = opcode >> 16;

    if(type.headerParamName == null && opParam != 0) {
      return null;
    }

    return new Op(offset, type, opParam, paramCount);
  }

  private boolean isValidOp(final int offset) {
    if((offset & 0x3) != 0) {
      return false;
    }

    if(offset < 0x4 || offset >= this.state.length()) {
      return false;
    }

    return this.parseHeader(offset) != null;
  }

  private boolean isProbablyOp(final Script script, int address) {
    if((address & 0x3) != 0) {
      return false;
    }

    if(address < 0x4 || address >= this.state.length()) {
      return false;
    }

    if(script.entries[address / 4] instanceof Op) {
      return true;
    }

    final int testCount = 3;
    int certainty = 0;
    for(int opIndex = 0; opIndex < testCount; opIndex++) {
      final Op op = this.parseHeader(address);

      if(op == null) {
        certainty -= testCount - opIndex;
        break;
      }

      certainty += opIndex + 1;

      // If we read valid params that aren't immediates, it's probably an op
      address += 0x4;

      for(int paramIndex = 0; paramIndex < op.type.params.length; paramIndex++) {
        final ParameterType parameterType = ParameterType.byOpcode(this.state.wordAt(address) >>> 24);

        if(parameterType != ParameterType.IMMEDIATE) {
          certainty += 1;
        }

        address += parameterType.getWidth((String)null) * 0x4; //TODO
      }
    }

    return certainty >= 2;
  }

  private ResolvedValue parseParamValue(final ScriptRegisters registers, final State state, final ParameterType param) {
    final ResolvedValue value = switch(param) {
      case IMMEDIATE -> ResolvedValue.of(state.currentWord());
      case NEXT_IMMEDIATE -> ResolvedValue.of(state.wordAt(state.currentOffset() + 4));
      case STORAGE -> ResolvedValue.register(registers.getDecompState().stor[state.wordAt(state.currentOffset()) & 0xff]);
/*TODO
      case OTHER_OTHER_STORAGE -> {
        final int word = state.wordAt(state.currentOffset());
        final Register register = this.getOtherOtherStor(registers, word & 0xff, word >>> 8 & 0xff, word >>> 16 & 0xff);

        if(register == null) {
          yield ResolvedValue.unresolved();
        }

        yield ResolvedValue.register(register);
      }
      case OTHER_STORAGE_OFFSET -> {
        final int word = state.wordAt(state.currentOffset());
        final Register register = this.getOtherStorOffset(registers, word & 0xff, word >>> 16 & 0xff, word >>> 8 & 0xff);

        if(register == null) {
          yield ResolvedValue.unresolved();
        }

        yield ResolvedValue.register(register);
      }
      case OTHER_STORAGE -> {
        final int word = state.wordAt(state.currentOffset());
        final Register register = this.getOtherStor(registers, word & 0xff, word >>> 8 & 0xff);

        if(register == null) {
          yield ResolvedValue.unresolved();
        }

        yield ResolvedValue.register(register);
      }
*/
      case INLINE_1, INLINE_2, INLINE_TABLE_1, INLINE_TABLE_3 -> ResolvedValue.of(state.headerOffset() + (short)state.currentWord() * 0x4);
//      case INLINE_TABLE_1 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.wordAt(state.headerOffset() + (short)state.currentWord() * 0x4)) * 0x4);
      case INLINE_TABLE_2, INLINE_TABLE_4 -> ResolvedValue.of(state.headerOffset() + 0x4);
      case INLINE_3 -> ResolvedValue.of(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 4);
//      case INLINE_TABLE_3 -> OptionalInt.of(state.headerOffset() + ((short)state.currentWord() + state.wordAt(state.headerOffset() + ((short)state.currentWord() + state.param2()) * 0x4)) * 0x4);
      default -> ResolvedValue.unresolved();
    };

    this.state.advance(param.getWidth(state));
    return value;
  }

  private RegisterSet getRegisterSetFromStor(final ScriptRegisters registers, final RegisterSet sourceSet, final int storIndex) {
    if(storIndex == 0) {
      return sourceSet;
    }

    final Register stor = sourceSet.stor[storIndex];
    return stor.known().stream().mapToObj(registers::getState).findFirst().orElse(null);
  }

  private Register getOtherOtherStor(final ScriptRegisters registers, final int firstRegister, final int secondRegister, final int thirdRegister) {
    final RegisterSet otherState = this.getRegisterSetFromStor(registers, registers.getDecompState(), firstRegister);

    if(otherState != null) {
      final RegisterSet otherOtherState = this.getRegisterSetFromStor(registers, otherState, secondRegister);

      if(otherOtherState != null) {
        return otherOtherState.stor[thirdRegister];
      }
    }

    return null;
  }

  private Register getOtherStorOffset(final ScriptRegisters registers, final int firstRegister, final int secondRegister, final int registerOffset) {
    final Register second = registers.getDecompState().stor[secondRegister];
    if(second.isKnown()) {
      final RegisterSet otherState = this.getRegisterSetFromStor(registers, registers.getDecompState(), firstRegister);

      if(otherState != null) {
        return otherState.stor[registerOffset + second.known().getAsInt()];
      }
    }

    return null;
  }

  private Register getOtherStor(final ScriptRegisters registers, final int firstRegister, final int secondRegister) {
    final RegisterSet otherState = this.getRegisterSetFromStor(registers, registers.getDecompState(), firstRegister);

    if(otherState != null) {
      return otherState.stor[secondRegister];
    }

    return null;
  }

  private void setRegister(final ScriptRegisters registers, final Op op, final int destParamIndex, final Consumer<Register> setter) {
    switch(op.params[destParamIndex].type) {
      case STORAGE -> setter.accept(registers.getDecompState().stor[op.params[destParamIndex].rawValues[0] & 0xff]);

/*
      case OTHER_OTHER_STORAGE -> {
        final int firstRegister = op.params[destParamIndex].rawValues[0] & 0xff;
        final int secondRegister = op.params[destParamIndex].rawValues[0] >>> 8 & 0xff;
        final int thirdRegister = op.params[destParamIndex].rawValues[0] >>> 16 & 0xff;
        final Register register = this.getOtherOtherStor(registers, firstRegister, secondRegister, thirdRegister);

        if(register != null) {
          setter.accept(register);
        }
      }

      case OTHER_STORAGE_OFFSET -> {
        final int firstRegister = op.params[destParamIndex].rawValues[0] & 0xff;
        final int registerOffset = op.params[destParamIndex].rawValues[0] >>> 8 & 0xff;
        final int secondRegister = op.params[destParamIndex].rawValues[0] >>> 16 & 0xff;
        final Register register = this.getOtherStorOffset(registers, firstRegister, secondRegister, registerOffset);

        if(register != null) {
          setter.accept(register);
        }
      }

      case OTHER_STORAGE -> {
        final int firstRegister = op.params[destParamIndex].rawValues[0] & 0xff;
        final int secondRegister = op.params[destParamIndex].rawValues[0] >>> 8 & 0xff;
        final Register register = this.getOtherStor(registers, firstRegister, secondRegister);

        if(register != null) {
          setter.accept(register);
        }
      }
*/
    }
  }

  private void setRegister(final ScriptRegisters registers, final Op op, final int destParamIndex, final int value) {
    this.setRegister(registers, op, destParamIndex, register -> register.known(value));
  }

  private void copyRegister(final ScriptRegisters registers, final Op op, final int destParamIndex, final int sourceParamIndex) {
    this.setRegister(registers, op, destParamIndex, register -> op.params[sourceParamIndex].resolvedValue.ifPresentOrElse(register::known, register::unknown));
  }

  private void modifyRegister(final ScriptRegisters registers, final Op op, final int paramIndex, final ToIntFunction<Integer> merge) {
    this.setRegister(registers, op, paramIndex, dest -> dest.known().ifPresent(merge::applyAsInt));
  }

  private void mergeRegister(final ScriptRegisters registers, final Op op, final int destParamIndex, final int sourceParamIndex, final ToIntBiFunction<Integer, Integer> merge) {
    op.params[sourceParamIndex].resolvedValue.ifPresent(sourceValue -> this.setRegister(registers, op, destParamIndex, dest -> dest.known().ifPresent(destValue -> merge.applyAsInt(sourceValue, destValue))));
  }

  private interface LengthPredicate {
    boolean get(final int entryIndex, final int entryAddress, final int earliestDestination, final int latestDestination);
  }
}
