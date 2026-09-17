/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.dsl.TypeSystem;

/**
 * Truffle DSL type system for the primitive Solvik runtime representations. {@code Int} is a Java
 * {@code int}, {@code Boolean} a Java {@code boolean}, and {@code Long}, {@code Float}, and
 * {@code Double} use their Java primitives; {@code Byte}, {@code Short}, {@code Char},
 * {@code String}, and every other value use the generic {@code Object} path (Byte/Short/Char are
 * boxed because Truffle frame slots have no dedicated kind for them). The Truffle DSL generates
 * {@code SolvikTypesGen} from this declaration.
 */
@TypeSystem({int.class, boolean.class, long.class, float.class, double.class})
public abstract class SolvikTypes {
}
