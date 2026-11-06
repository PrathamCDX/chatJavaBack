package com.example.demo.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows the method only for logged-in users with at least one of the given roles
 * (case-insensitive, matched against role names in the roles table). Checked by AuthorizationAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleFilter {

    String[] value();
}
