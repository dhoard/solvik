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
package org.solvik.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The compile-time module and namespace context of one physical source file
 * (docs/LANGUAGE_SPEC.md section 20). Every resolved top-level item carries the {@code FileScope}
 * of the file that physically declared it, so semantic analysis can resolve unqualified names
 * against the declaring file's module and qualified {@code prefix.Name} references against the
 * module prefixes that file made visible.
 *
 * <p>A {@code null} module name means the implicit default module, whose declarations are visible
 * unqualified from every file. {@code prefixes} maps a visible single-identifier prefix to the
 * module name it denotes: both explicit {@code include ... alias p} bindings and the declared module
 * names of unaliased includes. Prefixes are file-local and non-transitive.
 */
public final class FileScope {

    /** The scope of a file in the default module with no visible module prefixes. */
    public static final FileScope DEFAULT = new FileScope(null, Map.of());

    private final String moduleName;
    private final Map<String, String> prefixes;

    public FileScope(String moduleName, Map<String, String> prefixes) {
        this.moduleName = moduleName;
        // A plain `Map.copyOf` does not promise an iteration order, but this map is documented to
        // expose the visible bindings in declaration order, so the order is preserved explicitly.
        this.prefixes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(prefixes, "prefixes")));
    }

    /** The declared module name, or empty for the implicit default module. */
    public Optional<String> moduleName() {
        return Optional.ofNullable(moduleName);
    }

    /** The module name, or {@code null} for the implicit default module. */
    public String moduleNameOrNull() {
        return moduleName;
    }

    /** Whether this file belongs to the implicit default module. */
    public boolean isDefaultModule() {
        return moduleName == null;
    }

    /** The visible prefix-to-module bindings in declaration order. */
    public Map<String, String> prefixes() {
        return prefixes;
    }

    /** The module a visible prefix denotes, or empty when the prefix is not visible in this file. */
    public Optional<String> moduleForPrefix(String prefix) {
        return Optional.ofNullable(prefixes.get(prefix));
    }
}
