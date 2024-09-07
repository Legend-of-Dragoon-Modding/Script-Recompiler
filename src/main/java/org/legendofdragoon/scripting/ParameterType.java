package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Param;

import java.util.function.ToIntFunction;

public enum ParameterType {
  IMMEDIATE(0x0, 1),
  NEXT_IMMEDIATE(0x1, 2),
  STORAGE(0x2, 1),
  OTHER_OTHER_STORAGE(0x3, 1),
  OTHER_STORAGE_OFFSET(0x4, 1),
  GAMEVAR_1(0x5, 1),
  GAMEVAR_2(0x6, 1),
  GAMEVAR_ARRAY_1(0x7, 1),
  GAMEVAR_ARRAY_2(0x8, 1),
  INLINE_1(0x9, 1),
  INLINE_2(0xa, 1),
  INLINE_TABLE_1(0xb, 1),
  INLINE_TABLE_2(0xc, 2),
  OTHER_STORAGE(0xd, 1),
  GAMEVAR_3(0xe, 1),
  GAMEVAR_ARRAY_3(0xf, 1),
  GAMEVAR_ARRAY_4(0x10, 1),
  GAMEVAR_ARRAY_5(0x11, 1),
  _12(0x12, 1),
  INLINE_3(0x13, 1),
  INLINE_TABLE_3(0x14, 1),
  _15(0x15, 1),
  _16(0x16, 1),
  INLINE_TABLE_4(0x17, 2),
  REG(0x20, 1),
  ID(0x21, param -> 1 + (param.length() + 3) / 4, param -> 1 + ((param.rawValues[0] >>> 16 & 0xff) + 3) / 4, state -> 1 + (state.param2() + 3) / 4),
  REG_NULL(0x22, 1),
  REG_VAR(0x23, 1)
  ;

  public static ParameterType byOpcode(final int opcode) {
    for(final ParameterType op : ParameterType.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return ParameterType.IMMEDIATE;
  }

  public final int opcode;
  private final ToIntFunction<String> stringToWidth;
  private final ToIntFunction<Param> paramToWidth;
  private final ToIntFunction<State> stateToWidth;

  ParameterType(final int opcode, final ToIntFunction<String> stringToWidth, final ToIntFunction<Param> paramToWidth, final ToIntFunction<State> stateToWidth) {
    this.opcode = opcode;
    this.stringToWidth = stringToWidth;
    this.paramToWidth = paramToWidth;
    this.stateToWidth = stateToWidth;
  }

  ParameterType(final int opcode, final int width) {
    this(opcode, param -> width, param -> width, state -> width);
  }

  public int getWidth(final String param) {
    return this.stringToWidth.applyAsInt(param);
  }

  public int getWidth(final Param param) {
    return this.paramToWidth.applyAsInt(param);
  }

  public int getWidth(final State state) {
    return this.stateToWidth.applyAsInt(state);
  }

  /** table[index] */
  public boolean isInline() {
    return this == INLINE_1 || this == INLINE_2 || this == INLINE_TABLE_1 || this == INLINE_TABLE_2 || this == INLINE_3 || this == INLINE_TABLE_3 || this == INLINE_TABLE_4;
  }

  /** table[table[index]] */
  public boolean isInlineTable() {
    return this == INLINE_TABLE_1 || this == INLINE_TABLE_2 || this == INLINE_TABLE_3 || this == INLINE_TABLE_4;
  }
}
