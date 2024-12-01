package io.github.spah1879.stringframebuffer.base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.spah1879.stringframebuffer.annotation.FieldSpec;
import io.github.spah1879.stringframebuffer.exception.StringFrameBufferException;

public abstract class StringFrameBuffer {

  private static boolean isStringFrameBuffer(Class<?> type) {
    return type.isAssignableFrom(StringFrameBuffer.class);
  }

  private static boolean isAssignableFromBoolean(Class<?> type) {
    return type == boolean.class || type.isAssignableFrom(Boolean.class);
  }

  private StringFrameBuffer getNewInstance(Class<? extends StringFrameBuffer> clazz) {
    try {
      Constructor<?> constructor = clazz.getDeclaredConstructor();
      return (StringFrameBuffer) constructor.newInstance();
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException
        | IllegalArgumentException e) {
      throw new StringFrameBufferException("Failed to construct a new Instance. Class : " + clazz.getName(), e);
    }
  }

  private Object getFieldObject(Class<?> clazz, Field field, StringFrameBuffer frameBuffer)
      throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException {
    StringBuilder sb = new StringBuilder();
    String name = field.getName();

    sb.append(isAssignableFromBoolean(field.getType()) ? "is" : "get");
    sb.append(name.substring(0, 1).toUpperCase());
    if (name.length() > 1) {
      sb.append(name.substring(1));
    }

    Method method = clazz.getDeclaredMethod(sb.toString());
    return method.invoke(frameBuffer);
  }

  private void getObjectBytes(Object obj, FieldSpec spec, ByteArrayOutputStream baos)
      throws IOException, BufferOverflowException, IllegalArgumentException {
    byte[] bytes;

    if (obj instanceof String) {
      String value = (String) obj;
      bytes = value.getBytes(spec.charSet());
    } else if (obj instanceof Integer) {
      Integer value = (Integer) obj;
      bytes = String.valueOf(value).getBytes();
    } else if (obj instanceof Float) {
      Float value = (Float) obj;
      bytes = String.valueOf(value).getBytes();
    } else if (obj instanceof Double) {
      Double value = (Double) obj;
      bytes = String.valueOf(value).getBytes();
    } else if (obj instanceof Boolean) {
      Boolean value = (Boolean) obj;
      bytes = new byte[] { value.booleanValue() ? (byte) '1' : (byte) '0' };
    } else if (obj instanceof Date) {
      Date value = (Date) obj;
      String formatter = spec.formatter();
      if (formatter.isEmpty()) {
        throw new IllegalArgumentException("A 'formatter' must be set for the Date Type - " + obj.getClass().getName());
      }
      String stringValue = new SimpleDateFormat(formatter).format(value);
      bytes = stringValue.getBytes(spec.charSet());
    } else {
      throw new IllegalArgumentException("Unhandled Object Type - " + obj.getClass().getName());
    }

    int length = spec.length();
    char padding = spec.padding();
    boolean isNumber = Number.class.isAssignableFrom(obj.getClass());
    boolean isLeadingPadding = isNumber;

    if (padding == 0) {
      padding = isNumber ? '0' : ' ';
    }

    if (bytes != null) {
      if (bytes.length >= length) {
        baos.write(bytes, 0, length);
      } else {
        if (isLeadingPadding) {
          for (int i = bytes.length; i < length; i++) {
            baos.write(padding);
          }
        }
        baos.write(bytes);
        if (!isLeadingPadding) {
          for (int i = bytes.length; i < length; i++) {
            baos.write(padding);
          }
        }
      }
    }
  }

  private void getBytes(StringFrameBuffer frameBuffer, ByteArrayOutputStream baos) {
    Class<? extends StringFrameBuffer> clazz = frameBuffer.getClass();

    for (Field field : clazz.getDeclaredFields()) {
      FieldSpec spec = field.getAnnotation(FieldSpec.class);
      Class<?> type = field.getType();
      if (spec == null && !isStringFrameBuffer(type))
        continue;
      try {
        Object obj = getFieldObject(clazz, field, frameBuffer);
        if (obj == null) {
          throw new StringFrameBufferException("Value of field is Null.", clazz, field, spec);
        }
        if (spec != null) {
          getObjectBytes(obj, spec, baos);
        } else {
          getBytes((StringFrameBuffer) obj, baos);
        }
      } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
          | IOException | BufferOverflowException e) {
        throw new StringFrameBufferException(clazz, field, spec, e);
      }
    }
  }

  public byte[] getBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    getBytes(this, baos);
    return baos.toByteArray();
  }

  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(getBytes());
  }

  private void setFieldObject(Class<?> clazz, Field field, StringFrameBuffer result, Object obj)
      throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException {
    StringBuilder sb = new StringBuilder();
    String name = field.getName();

    sb.append("set");
    sb.append(name.substring(0, 1).toUpperCase());
    if (name.length() > 1) {
      sb.append(name.substring(1));
    }

    Method method = clazz.getDeclaredMethod(sb.toString(), field.getType());
    method.invoke(result, obj);
  }

  private Object fromBytesToObject(Field field, FieldSpec spec, ByteArrayInputStream bais) {
    try {
      Class<?> type = field.getType();
      byte[] bytes = new byte[spec.length()];

      bais.read(bytes);
      String stringValue = new String(bytes, spec.charSet());
      Object obj;

      if (type.isAssignableFrom(String.class)) {
        obj = stringValue;
      } else if (type == int.class || type.isAssignableFrom(Integer.class)) {
        obj = Integer.valueOf(stringValue);
      } else if (type == float.class || type.isAssignableFrom(Float.class)) {
        obj = Float.valueOf(stringValue);
      } else if (type == double.class || type.isAssignableFrom(Double.class)) {
        obj = Double.valueOf(stringValue);
      } else if (isAssignableFromBoolean(type)) {
        obj = Integer.valueOf(stringValue) == 0 ? Boolean.FALSE : Boolean.TRUE;
      } else if (type.isAssignableFrom(Date.class)) {
        String formatter = spec.formatter();
        if (formatter.isEmpty()) {
          throw new IllegalArgumentException("A 'formatter' must be set for the Date Type.");
        }
        obj = new SimpleDateFormat(spec.formatter()).parse(stringValue.trim());
      } else {
        throw new IllegalArgumentException("Unhandled Type - " + type.getName());
      }

      return obj;
    } catch (StringIndexOutOfBoundsException | IOException | ParseException e) {
      throw new StringFrameBufferException(field.getDeclaringClass(), field, spec, e);
    }
  }

  private StringFrameBuffer fromBytes(ByteArrayInputStream bais, StringFrameBuffer result,
      Class<? extends StringFrameBuffer> clazz) {
    if (result == null)
      result = getNewInstance(clazz);

    for (Field field : clazz.getDeclaredFields()) {
      FieldSpec spec = field.getAnnotation(FieldSpec.class);
      Class<?> type = field.getType();
      if (spec == null && !isStringFrameBuffer(type))
        continue;
      try {
        Object obj;
        if (spec != null) {
          obj = fromBytesToObject(field, spec, bais);
        } else {
          obj = fromBytes(bais, null, (Class<? extends StringFrameBuffer>) type);
        }
        setFieldObject(clazz, field, result, obj);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
          | InvocationTargetException e) {
        throw new StringFrameBufferException(result.getClass(), field, spec, e);
      }
    }

    return result;
  }

  public <T> T fromBytes(byte[] bytes, int offset) {
    return (T) fromBytes(new ByteArrayInputStream(bytes, offset, bytes.length - offset), this, getClass());
  }

  public <T> T fromBytes(byte[] bytes) {
    return fromBytes(bytes, 0);
  }

  public <T> T fromByteBuffer(ByteBuffer buffer, int offset) {
    return fromBytes(buffer.array(), offset);
  }

  public <T> T fromByteBuffer(ByteBuffer buffer) {
    return fromBytes(buffer.array(), 0);
  }

  public void normalize() {
    fromBytes(getBytes());
  }
}
