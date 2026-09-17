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

import org.solvik.diagnostic.DiagnosticCode;

/**
 * The host-file boundary of include resolution. The resolver decodes and validates include paths and
 * owns graph traversal; an implementation of this interface resolves a decoded path against the
 * including file, applies the host's I/O policy, canonicalizes the result, and caches each physical
 * file so it is loaded and assigned a source id exactly once.
 *
 * <p>Expected filesystem failures are returned as {@link LoadResult.Failure} values so they become
 * ordinary Solvik diagnostics rather than escaping as host exceptions.
 */
public interface IncludeSourceAccess {

    /** The root source being compiled, whose id is always {@code 0}. */
    LoadedSource root();

    /**
     * Resolves and loads the decoded include path relative to {@code including}. Implementations may
     * expand an exact leading {@code ~/} and must honor the host's public file-access policy.
     */
    LoadResult load(LoadedSource including, String decodedPath);

    /** The outcome of a load: a loaded source or a guest diagnostic code and message. */
    sealed interface LoadResult permits Success, Failure {
    }

    /** A successfully resolved source, cached so repeated includes return the same instance. */
    record Success(LoadedSource source) implements LoadResult {
    }

    /** A path, existence, file-type, or I/O failure attributed to a stable diagnostic code. */
    record Failure(DiagnosticCode code, String message) implements LoadResult {
    }
}
