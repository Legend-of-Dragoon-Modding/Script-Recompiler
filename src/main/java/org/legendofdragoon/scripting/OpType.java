package org.legendofdragoon.scripting;

import org.legendofdragoon.scripting.tokens.Op;

import java.util.Arrays;

public enum OpType {
  YIELD(0, "yield"),
  REWIND(1, "rewind"),
  WAIT(2, "wait", OpParam.in("frames")),
  WAIT_CMP(3, "wait_cmp", "operand", OpParam.in("left"), OpParam.in("right")),
  WAIT_CMP_0(4, "wait_cmp", "operand", OpParam.in("right")),
  REWIND5(5, "rewind"),
  REWIND6(6, "rewind"),
  REWIND7(7, "rewind"),
  MOV(8, "mov", OpParam.in("source"), OpParam.out("dest")),
  SWAP_BROKEN(9, "swap_broken", OpParam.both("sourceDest"), OpParam.out("dest")),
  MEMCPY(10, "memcpy", OpParam.in("size"), OpParam.in("src"), OpParam.in("dest")),
  REWIND11(11, "rewind"),
  MOV_0(12, "mov", OpParam.out("dest")),
  REWIND13(13, "rewind"),
  REWIND14(14, "rewind"),
  REWIND15(15, "rewind"),
  AND(16, "and", OpParam.in("and"), OpParam.both("operand")),
  OR(17, "or", OpParam.in("or"), OpParam.both("operand")),
  XOR(18, "xor", OpParam.in("xor"), OpParam.both("operand")),
  ANDOR(19, "andor", OpParam.in("and"), OpParam.in("or"), OpParam.both("operand")),
  NOT(20, "not", OpParam.both("val")),
  SHL(21, "shl", OpParam.in("shift"), OpParam.both("val")),
  SHR(22, "shr", OpParam.in("shift"), OpParam.both("val")),
  ADD(24, "add", OpParam.in("amount"), OpParam.both("operand")),
  SUB(25, "sub", OpParam.in("amount"), OpParam.both("operand")),
  SUB_REV(26, "sub_rev", OpParam.in("amount"), OpParam.both("operand")),
  INCR(27, "incr", OpParam.both("operand")),
  DECR(28, "decr", OpParam.both("operand")),
  NEG(29, "neg", OpParam.both("operand")),
  ABS(30, "abs", OpParam.both("operand")),
  MUL(32, "mul", OpParam.in("amount"), OpParam.both("operand")),
  DIV(33, "div", OpParam.in("amount"), OpParam.both("operand")),
  DIV_REV(34, "div_rev", OpParam.in("amount"), OpParam.both("operand")),
  MOD(35, "mod", OpParam.in("amount"), OpParam.both("operand")),
  MOD_REV(36, "mod_rev", OpParam.in("amount"), OpParam.both("operand")),
  MUL_12(40, "mul_12", OpParam.in("amount"), OpParam.both("operand")),
  DIV_12(41, "div_12", OpParam.in("amount"), OpParam.both("operand")),
  DIV_12_REV(42, "div_12_rev", OpParam.in("amount"), OpParam.both("operand")),
  MOD43(43, "mod", OpParam.in("amount"), OpParam.both("operand")),
  MOD_REV44(44, "mod_rev", OpParam.in("amount"), OpParam.both("operand")),
  SQRT(48, "sqrt", OpParam.in("val"), OpParam.out("dest")),
  RAND(49, "rand", OpParam.in("bound"), OpParam.out("dest")),
  SIN_12(50, "sin_12", OpParam.in("angle"), OpParam.out("dest")),
  COS_12(51, "cos_12", OpParam.in("angle"), OpParam.out("dest")),
  ATAN2_12(52, "atan2_12", OpParam.in("y"), OpParam.in("x"), OpParam.out("dest")),
  CALL(56, "call", "index"),
  JMP(64, "jmp", OpParam.in("addr")),
  JMP_CMP(65, "jmp_cmp", "operator", OpParam.in("left"), OpParam.in("right"), OpParam.in("addr")),
  JMP_CMP_0(66, "jmp_cmp", "operator", OpParam.in("right"), OpParam.in("addr")),
  WHILE(67, "while", OpParam.both("counter"), OpParam.in("addr")),
  JMP_TABLE(68, "jmp_table", OpParam.in("index"), OpParam.in("table")),
  GOSUB(72, "gosub", OpParam.in("addr")),
  RETURN(73, "return"),
  GOSUB_TABLE(74, "gosub_table", OpParam.in("index"), OpParam.in("table")),
  DEALLOCATE(80, "deallocate"),
  DEALLOCATE82(82, "deallocate"),
  DEALLOCATE_OTHER(83, "deallocate_other", OpParam.in("index")),
  FORK(86, "fork", OpParam.in("index"), OpParam.in("addr"), OpParam.in("stor[32] value")),
  FORK_REENTER(87, "fork_reenter", OpParam.in("index"), OpParam.in("entrypoint"), OpParam.in("stor[32] value")),
  CONSUME(88, "consume"),
  NOOP_96(96, "debug96", "?", OpParam.in("?"), OpParam.in("?")),
  NOOP_97(97, "debug97"),
  NOOP_98(98, "debug98", OpParam.in("?")),
  DEPTH(99, "depth", OpParam.out("dest")),
  ;

  static {
    WAIT_CMP_0.setCommentParamNames(new String[] {"left", "right"});
    MOV_0.setCommentParamNames(new String[] {"source", "dest"});
    JMP_CMP_0.setCommentParamNames(new String[] {"left", "right", "addr"});
  }

  public static OpType byOpcode(final int opcode) {
    for(final OpType op : OpType.values()) {
      if(op.opcode == opcode) {
        return op;
      }
    }

    return null;
  }

  public static OpType byName(final String name) {
    for(final OpType op : OpType.values()) {
      if(op.name.equalsIgnoreCase(name)) {
        return op;
      }
    }

    return null;
  }

  public final int opcode;
  public final String name;
  public final String headerParamName;
  public final OpParam[] params;
  private String[] commentParamNames;

  OpType(final int opcode, final String name, final String headerParamName, final OpParam... params) {
    this.opcode = opcode;
    this.name = name;
    this.params = params;
    this.headerParamName = headerParamName;
    this.commentParamNames = Arrays.stream(this.params).map(param -> param.name).toArray(String[]::new);
  }

  OpType(final int opcode, final String name, final String headerParamName) {
    this(opcode, name, headerParamName, new OpParam[0]);
  }

  OpType(final int opcode, final String name, final OpParam... params) {
    this(opcode, name, null, params);
  }

  OpType(final int opcode, final String name) {
    this(opcode, name, null, new OpParam[0]);
  }

  private void setCommentParamNames(final String[] paramNames) {
    this.commentParamNames = paramNames;
  }

  public String[] getCommentParamNames() {
    return this.commentParamNames;
  }

  public Op modifyOp(final Op op) {
    if(op.type == WAIT_CMP && op.params[0].resolvedValue.isPresent() && op.params[0].resolvedValue.get() == 0) {
      final Op newOp = new Op(op.address, WAIT_CMP_0, op.headerParam, op.params.length - 1);
      System.arraycopy(op.params, 1, newOp.params, 0, newOp.params.length);
      return newOp;
    }

    if(op.type == JMP_CMP && op.params[0].resolvedValue.isPresent() && op.params[0].resolvedValue.get() == 0) {
      final Op newOp = new Op(op.address, JMP_CMP_0, op.headerParam, op.params.length - 1);
      System.arraycopy(op.params, 1, newOp.params, 0, newOp.params.length);
      return newOp;
    }

    if(op.type == MOV && op.params[0].resolvedValue.isPresent() && op.params[0].resolvedValue.get() == 0) {
      final Op newOp = new Op(op.address, MOV_0, op.headerParam, op.params.length - 1);
      System.arraycopy(op.params, 1, newOp.params, 0, newOp.params.length);
      return newOp;
    }

    return op;
  }
}
