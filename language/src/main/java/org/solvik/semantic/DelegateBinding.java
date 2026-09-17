/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
