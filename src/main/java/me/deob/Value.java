package me.deob;

public record Value(Object value) {
  public static final Value COMPLEX = new Value(new ComplexValue());
  public static final Value METHOD_PARAM = new Value(new MethodParam());
  public static final Value FIELD_VAR = new Value(new FieldVar());
  public static final Value UNINITIALIZED = new Value(new Uninitialized());

  public boolean isSimpleInitialized() {
    return this != COMPLEX && this != METHOD_PARAM && this != UNINITIALIZED && this != FIELD_VAR;
  }

  @Override
  public String toString() {
    if (this == COMPLEX) {
      return "<complex>";
    }

    if (this == METHOD_PARAM) {
      return "<method_param>";
    }

    if (this == UNINITIALIZED) {
      return "<uninitialized>";
    }

    if (this == FIELD_VAR) {
      return "<field_var>";
    }

    if (value == null) {
      return "<null>";
    }

    return value.toString();
  }

  private static class ComplexValue {
  }
  private static class MethodParam {
  }
  private static class FieldVar {
  }
  private static class Uninitialized {
  }
}
