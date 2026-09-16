/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.diagnostic;

/** Severity of a Solvik compile-time diagnostic. */
public enum DiagnosticSeverity {
    /** A problem that prevents compilation; the front end must not produce an AST or lowering. */
    ERROR,
    /** A reportable concern that does not prevent compilation. */
    WARNING,
    /** An informational note. */
    INFO
}
