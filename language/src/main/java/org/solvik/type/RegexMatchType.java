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
 * The built-in {@code RegexMatch} type (docs/LANGUAGE_SPEC.md section 14). It is a non-generic
 * nominal class under {@code Any} that a {@code Regex.find} or {@code Regex.findAll} call
 * produces; it is not constructible from source. Its members are baked into static analysis:
 * immutable {@code value: String}, {@code start: Int}, {@code end: Int}, and {@code groupCount: Int}
 * properties plus {@code group(index: Int): String?}.
 */
public final class RegexMatchType extends Type {

    public static final RegexMatchType INSTANCE = new RegexMatchType();

    private RegexMatchType() {
        super("RegexMatch");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
