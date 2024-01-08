package me.deob;

public record DeobfuscationResult(boolean succeeded, String result) {
  public static final DeobfuscationResult FAILURE = new DeobfuscationResult(false, null);

  public static DeobfuscationResult of(String result) {
    return new DeobfuscationResult(true, result);
  }
}
