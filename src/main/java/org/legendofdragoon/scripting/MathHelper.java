package org.legendofdragoon.scripting;

public final class MathHelper {
  private MathHelper() { }

  public static int get(final byte[] data, final int offset, final int size) {
    int value = 0;

    for(int i = 0; i < size; i++) {
      value |= (data[offset + i] & 0xff) << i * 8;
    }

    return value;
  }

  public static final float PI = (float)Math.PI;
  public static final float TWO_PI = (float)(Math.PI * 2);
  public static final float HALF_PI = (float)(Math.PI / 2);

  private static final float PSX_DEG_TO_DEG = 360.0f / 4096.0f;
  private static final float DEG_TO_RAD = (float)(Math.PI / 180.0f);
  private static final float PSX_DEG_TO_RAD = PSX_DEG_TO_DEG * DEG_TO_RAD;

  private static final float DEG_TO_PSX_DEG = 4096.0f / 360.0f;
  private static final float RAD_TO_DEG = (float)(180.0f / Math.PI);
  private static final float RAD_TO_PSX_DEG = RAD_TO_DEG * DEG_TO_PSX_DEG;

  public static float psxDegToRad(final int psxDeg) {
    return psxDeg * PSX_DEG_TO_RAD;
  }

  public static int radToPsxDeg(final float rads) {
    return (int)(rads * RAD_TO_PSX_DEG);
  }

  public static float atan2(float y, final float x) {
    if(y == 0.0f && x == 0.0f) {
      return 0.0f;
    }

    if(flEq(y, -0.0f)) {
      y = 0.0f;
    }

    return (float)Math.atan2(y, x);
  }

  public static boolean flEq(final float a, final float b, final float epsilon) {
    return Math.abs(a - b) < epsilon;
  }

  public static boolean flEq(final float a, final float b) {
    return flEq(a, b, 0.00001f);
  }
}
