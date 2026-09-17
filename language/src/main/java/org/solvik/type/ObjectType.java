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
package org.solvik.type;

import java.util.Optional;

/**
 * The root of class, interface, and enum values (docs/LANGUAGE_SPEC.md section 4). The built-in
 * scalar types are its direct subtypes; user-defined classes join the hierarchy in later phases.
 */
public final class ObjectType extends Type {

    public static final ObjectType INSTANCE = new ObjectType();

    private ObjectType() {
        super("Object");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
