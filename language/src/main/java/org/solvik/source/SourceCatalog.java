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
package org.solvik.source;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, insertion-ordered map from compilation-local source id to {@link SourceFile}. It is
 * the bridge that lets a diagnostic carrying only a {@link SourceSpan} recover the physical file
 * that supplied the code, whether that file is the root or an included source.
 *
 * <p>This class is compiler-only and intentionally has no Truffle dependency: the language layer
 * pairs it with an id-to-Truffle-{@code Source} map when it needs runtime or interop locations.
 */
public final class SourceCatalog {

    private final Map<Integer, SourceFile> filesById;

    private SourceCatalog(Map<Integer, SourceFile> files) {
        this.filesById = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    /** A catalog containing exactly one file, whatever its id. */
    public static SourceCatalog singleton(SourceFile file) {
        return builder().add(Objects.requireNonNull(file, "file")).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The physical file that produced a span. */
    public SourceFile file(SourceSpan span) {
        return file(span.sourceId());
    }

    /** The physical file registered under {@code id}. */
    public SourceFile file(int id) {
        SourceFile file = filesById.get(id);
        if (file == null) {
            throw new IllegalArgumentException("unknown source id " + id);
        }
        return file;
    }

    public boolean contains(int id) {
        return filesById.containsKey(id);
    }

    /** Registered files in source-id order. */
    public Collection<SourceFile> files() {
        return filesById.values();
    }

    public int size() {
        return filesById.size();
    }

    /** Mutable builder that rejects duplicate ids. */
    public static final class Builder {

        private final Map<Integer, SourceFile> files = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(SourceFile file) {
            Objects.requireNonNull(file, "file");
            if (files.putIfAbsent(file.id(), file) != null) {
                throw new IllegalArgumentException("duplicate source id " + file.id());
            }
            return this;
        }

        public SourceCatalog build() {
            return new SourceCatalog(files);
        }
    }
}
