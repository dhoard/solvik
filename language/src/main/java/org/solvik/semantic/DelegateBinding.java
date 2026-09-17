/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Objects;
import org.solvik.type.Type;

/**
 * A resolved {@code delegate} declaration (docs/LANGUAGE_SPEC.md section 9): the immutable property
 * that stores the delegate value together with the interface contract whose members it can supply
 * to the declaring class.
 *
 * <p>The contract is the resolved {@link InterfaceSymbol} of the delegate's declared type, so
 * {@link InterfaceSymbol#members()} — the members visible through that interface, inherited ones
 * included — is exactly the set of members this delegate can forward. A delegate whose written type
 * is not an interface has no contract and therefore no binding: the semantic pass reports it through
 * {@code SOLV-SEM-025} and the declaring class simply has no forwarding source for the members such a
 * delegate would have supplied.
 */
public final class DelegateBinding {

    private final PropertySymbol property;
    private final Type declaredType;
    private final InterfaceSymbol contract;

    DelegateBinding(PropertySymbol property, Type declaredType, InterfaceSymbol contract) {
        this.property = Objects.requireNonNull(property);
        this.declaredType = Objects.requireNonNull(declaredType);
        this.contract = Objects.requireNonNull(contract);
    }

    /** The immutable {@code delegate val} property that holds the delegate value. */
    public PropertySymbol property() {
        return property;
    }

    /** The written type of the delegate declaration. */
    public Type declaredType() {
        return declaredType;
    }

    /** The interface whose members this delegate supplies. */
    public InterfaceSymbol contract() {
        return contract;
    }
}
