package com.example.demo.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows the method only for logged-in users whose X-content header is one of the given values
 * (case-insensitive). Checked by AuthorizationAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HeaderFilter {

    String[] value();
}
