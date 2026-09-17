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

import java.net.URI;
import java.util.Objects;
import org.solvik.source.SourceFile;

/**
 * One physical source file participating in an include expansion. It pairs the file's
 * compilation-local {@link SourceFile} with the identity and base information include resolution
 * needs, without depending on Truffle.
 *
 * @param canonicalKey the canonical identity of the physical file; two paths or symlinks that
 *     resolve to the same file share it. A synthetic root that has no file backing uses a unique
 *     non-file URI so it can never collide with a real include.
 * @param baseDirectory the directory URI used to resolve this file's relative includes; it always
 *     ends in {@code /} so URI resolution treats it as a directory.
 * @param file the parsed-source identity carrying this file's id, name, and text.
 */
public record LoadedSource(URI canonicalKey, URI baseDirectory, SourceFile file) {

    public LoadedSource {
        Objects.requireNonNull(canonicalKey, "canonicalKey");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(file, "file");
        if (!baseDirectory.toString().endsWith("/")) {
            throw new IllegalArgumentException("baseDirectory must end with '/': " + baseDirectory);
        }
    }
}
