package io.github.spah1879.stringframebuffer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldSpec {

  int value();

  char padding() default '\u0000';

  String charSet() default "UTF-8";
}
