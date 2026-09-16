/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

/** Local declaration binding kinds. */
public enum BindingKind {
    /** Immutable binding. */
    VAL("val"),
    /** Mutable binding. */
    VAR("var");

    private final String keyword;

    BindingKind(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }
}
