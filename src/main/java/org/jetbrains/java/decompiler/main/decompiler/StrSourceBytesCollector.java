package org.jetbrains.java.decompiler.main.decompiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StrSourceBytesCollector {
  private static final Map<String, byte[]> SRC_BYTES = new ConcurrentHashMap<>();

  public static void put(String str, byte[] srcBytes) {
    SRC_BYTES.put(str, srcBytes);
  }

  public static byte[] get(String str) {
    return SRC_BYTES.get(str);
  }
}
