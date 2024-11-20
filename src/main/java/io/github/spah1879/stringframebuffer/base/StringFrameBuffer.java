package io.github.spah1879.stringframebuffer.base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import io.github.spah1879.stringframebuffer.annotation.FieldSpec;
import io.github.spah1879.stringframebuffer.exception.StringFrameBufferException;

public abstract class StringFrameBuffer {

  private static boolean isStringFrameBuffer(Class<?> type) {
    return StringFrameBuffer.class.isAssignableFrom(type);
  }

  private StringFrameBuffer getNewInstance(Class<? extends StringFrameBuffer> clazz) {
    try {
      Constructor<?> constructor = clazz.getDeclaredConstructor();
      try {
        if (!constructor.isAccessible()) {
          constructor.setAccessible(true);
        }
        return (StringFrameBuffer) constructor.newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException
          | IllegalArgumentException e) {
        throw new StringFrameBufferException("Failed to construct a new Instance. Class : " + clazz.getName(), e);
      } finally {
        constructor.setAccessible(false);
      }
    } catch (NoSuchMethodException e) {
      throw new StringFrameBufferException("Failed to construct a new Instance. Class : " + clazz.getName(), e);
    }
  }

  private void getObjectBytes(Object obj, FieldSpec spec, ByteArrayOutputStream baos)
      throws IOException, BufferOverflowException, IllegalArgumentException {
    byte[] bytes;
    char padding = spec.padding();
    boolean isLeadingPadding = true;

    if (obj instanceof String) {
      String value = (String) obj;
      bytes = value.getBytes(spec.charSet());
      if (padding == 0)
        padding = ' ';
      isLeadingPadding = false;
    } else if (obj instanceof Integer) {
      Integer value = (Integer) obj;
      bytes = String.valueOf(value).getBytes();
      if (padding == 0)
        padding = '0';
    } else if (obj instanceof Float) {
      Float value = (Float) obj;
      bytes = String.valueOf(value).getBytes();
      if (padding == 0)
        padding = '0';
    } else if (obj instanceof Double) {
      Double value = (Double) obj;
      bytes = String.valueOf(value).getBytes();
      if (padding == 0)
        padding = '0';
    } else {
      throw new IllegalArgumentException("Unhandled Object Type - " + obj.getClass().getName());
    }

    int length = spec.value();
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
      try {
        field.setAccessible(true);
        Object obj = field.get(frameBuffer);
        if (spec != null) {
          if (obj == null) {
            throw new StringFrameBufferException("Value of field is Null.", clazz, field, spec);
          }
          getObjectBytes(obj, spec, baos);
        } else if (obj != null && isStringFrameBuffer(field.getType())) {
          getBytes((StringFrameBuffer) obj, baos);
        }
      } catch (IllegalArgumentException | IllegalAccessException | IOException | BufferOverflowException e) {
        throw new StringFrameBufferException(clazz, field, spec, e);
      } finally {
        field.setAccessible(false);
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

  private Object fromBytesToObject(Field field, FieldSpec spec, ByteArrayInputStream bais) {
    try {
      Class<?> type = field.getType();
      byte[] bytes = new byte[spec.value()];

      bais.read(bytes);
      String stringValue = new String(bytes, spec.charSet());
      Object obj;

      if (type == String.class) {
        obj = stringValue;
      } else if (type == Integer.class || type == int.class) {
        obj = Integer.valueOf(stringValue);
      } else if (type == Float.class || type == float.class) {
        obj = Float.valueOf(stringValue);
      } else if (type == Double.class || type == double.class) {
        obj = Double.valueOf(stringValue);
      } else {
        throw new IllegalArgumentException("Unhandled Type - " + type.getName());
      }

      return obj;
    } catch (StringIndexOutOfBoundsException | IOException e) {
      throw new StringFrameBufferException(field.getDeclaringClass(), field, spec, e);
    }
  }

  private StringFrameBuffer fromBytes(ByteArrayInputStream bais, StringFrameBuffer result,
      Class<? extends StringFrameBuffer> clazz) {
    if (result == null)
      result = getNewInstance(clazz);

    for (Field field : clazz.getDeclaredFields()) {
      FieldSpec spec = field.getAnnotation(FieldSpec.class);
      try {
        Class<?> type = field.getType();
        Object obj;
        if (spec != null) {
          obj = fromBytesToObject(field, spec, bais);
        } else if (isStringFrameBuffer(type)) {
          obj = fromBytes(bais, null, (Class<? extends StringFrameBuffer>) type);
        } else {
          obj = null;
        }
        if (obj != null) {
          field.setAccessible(true);
          field.set(result, obj);
        }
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new StringFrameBufferException(result.getClass(), field, spec, e);
      } finally {
        field.setAccessible(false);
      }
    }

    return result;
  }

  public <T extends StringFrameBuffer> T fromBytes(byte[] bytes, int offset) {
    return (T) fromBytes(new ByteArrayInputStream(bytes, offset, bytes.length - offset), this, getClass());
  }

  public <T extends StringFrameBuffer> T fromBytes(byte[] bytes) {
    return fromBytes(bytes, 0);
  }

  public <T extends StringFrameBuffer> T fromByteBuffer(ByteBuffer buffer, int offset) {
    return fromBytes(buffer.array(), offset);
  }

  public <T extends StringFrameBuffer> T fromByteBuffer(ByteBuffer buffer) {
    return fromBytes(buffer.array(), 0);
  }

  public void normalize() {
    fromBytes(getBytes());
  }
}
