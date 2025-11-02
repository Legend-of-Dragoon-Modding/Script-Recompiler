package org.legendofdragoon.scripting.tokens;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static org.legendofdragoon.scripting.Lexer.CONTROL_PATTERN;
import static org.legendofdragoon.scripting.Lexer.NUMBER_PATTERN;

public class LodString extends Entry {
  private static final Logger LOGGER = LogManager.getFormatterLogger();

  public final int[] chars;

  public LodString(final int address, final int[] chars) {
    super(address);
    this.chars = chars;
  }

  @Override
  public String toString() {
    final StringBuilder out = new StringBuilder();

    for(int i = 0; i < this.chars.length; i++) {
      final int chr = this.chars[i];

      if((chr & 0xff00) != 0) {
        final CONTROLS control = CONTROLS.fromControl(chr);

        if(control != null) {
          out.append('<').append(control.name);

          if(control.paramType != ControlType.NONE) {
            out.append('=');
          }

          if(control.paramType == ControlType.NUMERIC) {
            out.append(chr & 0xff);
          } else if(control.paramType == ControlType.STRING) {
            final int length = chr & 0xff;

            for(int n = 0; n < length; n++) {
              out.append((char)(this.chars[i + 1 + n / 2] >>> (n & 0x1) * 8 & 0xff));
            }

            i += (length + 1) / 2;
          }

          out.append('>');
          continue;
        }
      }

      out.append(switch(chr) {
        case 0x00 -> ' ';
        case 0x01 -> ',';
        case 0x02 -> '.';
        case 0x03 -> '·';
        case 0x04 -> ':';
        case 0x05 -> '?';
        case 0x06 -> '!';
        case 0x07 -> '_';
        case 0x08 -> '/';
        case 0x09 -> '\'';
        case 0x0a -> '"';
        case 0x0b -> '(';
        case 0x0c -> ')';
        case 0x0d -> '-';
        case 0x0e -> '`';
        case 0x0f -> '%';
        case 0x10 -> '&';
        case 0x11 -> '*';
        case 0x12 -> '@';
        case 0x13 -> '+';
        case 0x14 -> '~';
        case 0x15 -> '0';
        case 0x16 -> '1';
        case 0x17 -> '2';
        case 0x18 -> '3';
        case 0x19 -> '4';
        case 0x1a -> '5';
        case 0x1b -> '6';
        case 0x1c -> '7';
        case 0x1d -> '8';
        case 0x1e -> '9';
        case 0x1f -> 'A';
        case 0x20 -> 'B';
        case 0x21 -> 'C';
        case 0x22 -> 'D';
        case 0x23 -> 'E';
        case 0x24 -> 'F';
        case 0x25 -> 'G';
        case 0x26 -> 'H';
        case 0x27 -> 'I';
        case 0x28 -> 'J';
        case 0x29 -> 'K';
        case 0x2a -> 'L';
        case 0x2b -> 'M';
        case 0x2c -> 'N';
        case 0x2d -> 'O';
        case 0x2e -> 'P';
        case 0x2f -> 'Q';
        case 0x30 -> 'R';
        case 0x31 -> 'S';
        case 0x32 -> 'T';
        case 0x33 -> 'U';
        case 0x34 -> 'V';
        case 0x35 -> 'W';
        case 0x36 -> 'X';
        case 0x37 -> 'Y';
        case 0x38 -> 'Z';
        case 0x39 -> 'a';
        case 0x3a -> 'b';
        case 0x3b -> 'c';
        case 0x3c -> 'd';
        case 0x3d -> 'e';
        case 0x3e -> 'f';
        case 0x3f -> 'g';
        case 0x40 -> 'h';
        case 0x41 -> 'i';
        case 0x42 -> 'j';
        case 0x43 -> 'k';
        case 0x44 -> 'l';
        case 0x45 -> 'm';
        case 0x46 -> 'n';
        case 0x47 -> 'o';
        case 0x48 -> 'p';
        case 0x49 -> 'q';
        case 0x4a -> 'r';
        case 0x4b -> 's';
        case 0x4c -> 't';
        case 0x4d -> 'u';
        case 0x4e -> 'v';
        case 0x4f -> 'w';
        case 0x50 -> 'x';
        case 0x51 -> 'y';
        case 0x52 -> 'z';
        case 0x53 -> '[';
        case 0x54 -> ']';
        case 0x55 -> ';';
        case 0x56 -> '□';
        default -> {
          LOGGER.warn("Found invalid character %x", chr);
          yield "<chr=0x%x>".formatted(chr);
        }
      });
    }

    return out.toString();
  }

  public static LodString fromString(final int address, final String string) {
    final List<Integer> out = new ArrayList<>();

    boolean noTerm = false;

    for(int i = 0; i < string.length(); i++) {
      final char chr = string.charAt(i);

      // Handle controls
      if(chr == '<') {
        final int end = string.indexOf('>', i + 1);
        final String controlString = string.substring(i, end + 1);
        final Matcher controlMatcher = CONTROL_PATTERN.matcher(controlString);

        if(controlMatcher.matches()) {
          final String controlName = controlMatcher.group(1).toLowerCase();

          if("noterm".equalsIgnoreCase(controlName)) {
            noTerm = true;
            i += 7;
            continue;
          }

          final String paramString = controlMatcher.group(2);

          final CONTROLS control = CONTROLS.fromName(controlName);

          if(control.paramType == ControlType.NUMERIC) {
            out.add(control.control | parseInt(paramString));
          } else if(control.paramType == ControlType.STRING) {
            out.add(control.control | paramString.length());

            for(int n = 0; n < (paramString.length() + 1) / 2; n++) {
              int c = paramString.charAt(n * 2);

              if(n * 2 + 1 < paramString.length()) {
                c |= paramString.charAt(n * 2 + 1) << 8;
              }

              out.add(c);
            }
          } else {
            out.add(control.control);
          }
        }

        i = end;
        continue;
      }

      out.add(switch(chr) {
        case ' ' -> 0x00;
        case ',' -> 0x01;
        case '.' -> 0x02;
        case '·' -> 0x03;
        case ':' -> 0x04;
        case '?' -> 0x05;
        case '!' -> 0x06;
        case '_' -> 0x07;
        case '/' -> 0x08;
        case '\'' -> 0x09;
        case '"' -> 0x0a;
        case '(' -> 0x0b;
        case ')' -> 0x0c;
        case '-' -> 0x0d;
        case '`' -> 0x0e;
        case '%' -> 0x0f;
        case '&' -> 0x10;
        case '*' -> 0x11;
        case '@' -> 0x12;
        case '+' -> 0x13;
        case '~' -> 0x14;
        case '0' -> 0x15;
        case '1' -> 0x16;
        case '2' -> 0x17;
        case '3' -> 0x18;
        case '4' -> 0x19;
        case '5' -> 0x1a;
        case '6' -> 0x1b;
        case '7' -> 0x1c;
        case '8' -> 0x1d;
        case '9' -> 0x1e;
        case 'A' -> 0x1f;
        case 'B' -> 0x20;
        case 'C' -> 0x21;
        case 'D' -> 0x22;
        case 'E' -> 0x23;
        case 'F' -> 0x24;
        case 'G' -> 0x25;
        case 'H' -> 0x26;
        case 'I' -> 0x27;
        case 'J' -> 0x28;
        case 'K' -> 0x29;
        case 'L' -> 0x2a;
        case 'M' -> 0x2b;
        case 'N' -> 0x2c;
        case 'O' -> 0x2d;
        case 'P' -> 0x2e;
        case 'Q' -> 0x2f;
        case 'R' -> 0x30;
        case 'S' -> 0x31;
        case 'T' -> 0x32;
        case 'U' -> 0x33;
        case 'V' -> 0x34;
        case 'W' -> 0x35;
        case 'X' -> 0x36;
        case 'Y' -> 0x37;
        case 'Z' -> 0x38;
        case 'a' -> 0x39;
        case 'b' -> 0x3a;
        case 'c' -> 0x3b;
        case 'd' -> 0x3c;
        case 'e' -> 0x3d;
        case 'f' -> 0x3e;
        case 'g' -> 0x3f;
        case 'h' -> 0x40;
        case 'i' -> 0x41;
        case 'j' -> 0x42;
        case 'k' -> 0x43;
        case 'l' -> 0x44;
        case 'm' -> 0x45;
        case 'n' -> 0x46;
        case 'o' -> 0x47;
        case 'p' -> 0x48;
        case 'q' -> 0x49;
        case 'r' -> 0x4a;
        case 's' -> 0x4b;
        case 't' -> 0x4c;
        case 'u' -> 0x4d;
        case 'v' -> 0x4e;
        case 'w' -> 0x4f;
        case 'x' -> 0x50;
        case 'y' -> 0x51;
        case 'z' -> 0x52;
        case '[' -> 0x53;
        case ']' -> 0x54;
        case ';' -> 0x55;
        case '□' -> 0x56;
        default -> throw new RuntimeException("Illegal char %c".formatted(chr));
      });
    }

    if(!noTerm) {
      out.add(0xa0ff);
    }

    return new LodString(address, out.stream().mapToInt(Integer::intValue).toArray());
  }

  private static int parseInt(final String val) {
    if(NUMBER_PATTERN.matcher(val).matches()) {
      if(val.startsWith("0x")) {
        return Integer.parseUnsignedInt(val.substring(2), 16);
      }

      return Integer.parseInt(val);
    }

    throw new NumberFormatException("Invalid number " + val);
  }

  private enum CONTROLS {
    LINE("line", 0xa1ff, ControlType.NONE),
    BUTTON("button", 0xa200, ControlType.NONE),
    MULTIBOX("multibox", 0xa3ff, ControlType.NONE),
    SPEED("speed", 0xa500, ControlType.NUMERIC),
    PAUSE("pause", 0xa600, ControlType.NUMERIC),
    COLOUR("colour", 0xa700, ControlType.NUMERIC),
    VAR("var", 0xa800, ControlType.NUMERIC),
    XOFFSET("xoffset", 0xad00, ControlType.NUMERIC),
    YOFFSET("yoffset", 0xae00, ControlType.NUMERIC),
    SAUTO("sauto", 0xb000, ControlType.NUMERIC),
    ELEMENT("element", 0xb100, ControlType.NUMERIC),
    ARROW("arrow", 0xb200, ControlType.NUMERIC),
    ACTION("action", 0xc000, ControlType.STRING),

    INVALID("chr", 0, ControlType.NUMERIC),
    ;

    public static CONTROLS fromControl(final int control) {
      for(final CONTROLS c : CONTROLS.values()) {
        if(c.paramType != ControlType.NONE) {
          if((c.control & 0xff00) == (control & 0xff00)) {
            return c;
          }
        } else if(c.control == control) {
          return c;
        }
      }

      return null;
    }

    public static CONTROLS fromName(final String name) {
      for(final CONTROLS c : CONTROLS.values()) {
        if(c.name.equals(name)) {
          return c;
        }
      }

      throw new RuntimeException("Unknown control %s".formatted(name));
    }

    public final String name;
    public final int control;
    public final ControlType paramType;

    CONTROLS(final String name, final int control, final ControlType paramType) {
      this.name = name;
      this.control = control;
      this.paramType = paramType;
    }
  }

  private enum ControlType {
    NONE,
    NUMERIC,
    STRING,
  }
}
