package io.github.spah1879.stringframebuffer.exception;

import java.lang.reflect.Field;

import io.github.spah1879.stringframebuffer.annotation.FieldSpec;

public class StringFrameBufferException extends RuntimeException {

  public StringFrameBufferException(String message) {
    super(message);
  }

  public StringFrameBufferException(String message, Throwable cause) {
    super(message, cause);
  }

  public StringFrameBufferException(String message, Class<?> clazz, Field field, FieldSpec spec) {
    super(getTag(message, clazz, field, spec));
  }

  public StringFrameBufferException(Class<?> clazz, Field field, FieldSpec spec) {
    super(getTag(null, clazz, field, spec));
  }

  public StringFrameBufferException(String message, Class<?> clazz, Field field, FieldSpec spec, Throwable cause) {
    super(getTag(message, clazz, field, spec), cause);
  }

  public StringFrameBufferException(Class<?> clazz, Field field, FieldSpec spec, Throwable cause) {
    super(getTag(null, clazz, field, spec), cause);
  }

  private static String getTag(String message, Class<?> clazz, Field field, FieldSpec spec) {
    StringBuilder sb = new StringBuilder();

    if (message != null) {
      sb.append(message).append(" ");
    }

    sb.append(clazz.getName()).append(".").append(field.getName());
    if (spec != null) {
      String descString = spec.toString();
      sb.append(descString.substring(descString.indexOf('(')));
    }
    sb.append(", ").append(field.getType().getName());

    return sb.toString();
  }

}