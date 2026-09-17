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
package org.solvik.truffle;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeSourceAccess;
import org.solvik.parser.LoadedSource;
import org.solvik.source.SourceFile;

/**
 * The Truffle-backed {@link IncludeSourceAccess}. It applies the host's public file-access policy
 * through {@link Env#getPublicTruffleFile}, expands an exact leading {@code ~/} against the process
 * home directory, resolves relative paths against the including file, canonicalizes the target, and
 * caches each canonical file so it is read and assigned a source id once per compilation.
 *
 * <p>The home directory is read when this adapter is constructed for a parse, never cached in a
 * build-time-initialized static field, so native images built on one machine do not freeze that
 * machine's home directory.
 */
public final class TruffleIncludeSourceAccess implements IncludeSourceAccess {

    private final Env env;
    private final String home;
    private final Map<String, LoadedSource> byCanonicalKey = new LinkedHashMap<>();
    private final Map<Integer, Source> sourcesById = new LinkedHashMap<>();
    private final LoadedSource root;
    private final boolean rootPreparationFailed;
    private final String rootPreparationFailure;
    private final boolean rootBaseIsWorkingDirectory;
    private int nextId = 1;

    public TruffleIncludeSourceAccess(Env env, Source rootSource, SourceFile rootFile) {
        this.env = env;
        this.home = System.getProperty("user.home");
        URI canonicalKey = null;
        URI baseDirectory = URI.create("solvik:cwd/");
        boolean workingDirectoryBase = true;
        String preparationFailure = null;
        URI rootUri = rootSource.getURI();
        if (rootUri != null && "file".equals(rootUri.getScheme())) {
            try {
                TruffleFile file = env.getPublicTruffleFile(rootUri);
                TruffleFile canonical = file.getCanonicalFile();
                canonicalKey = canonical.toUri();
                baseDirectory = parentDirectory(canonical);
                workingDirectoryBase = false;
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                preparationFailure = e.getMessage();
            }
        }
        this.rootPreparationFailed = preparationFailure != null;
        this.rootPreparationFailure = preparationFailure;
        this.rootBaseIsWorkingDirectory = workingDirectoryBase;
        URI key = canonicalKey != null ? canonicalKey : URI.create("solvik:root/" + UUID.randomUUID());
        this.root = new LoadedSource(key, baseDirectory, rootFile);
        this.byCanonicalKey.put(key.toString(), root);
        this.sourcesById.put(0, rootSource);
    }

    @Override
    public LoadedSource root() {
        return root;
    }

    @Override
    public LoadResult load(LoadedSource including, String decodedPath) {
        if (rootPreparationFailed && including == root) {
            return new IncludeSourceAccess.Failure(DiagnosticCode.RESOL_INCLUDE_INVALID_PATH, //
                            "cannot resolve include '" + decodedPath + "' against the root source: " + rootPreparationFailure);
        }
        String expanded = decodedPath;
        if (decodedPath.startsWith("~/")) {
            if (home == null || home.isEmpty()) {
                return new IncludeSourceAccess.Failure(DiagnosticCode.RESOL_INCLUDE_INVALID_PATH, //
                                "cannot expand '~/' for include path '" + decodedPath + "': no home directory is available");
            }
            expanded = home + decodedPath.substring(1);
        }
        try {
            TruffleFile candidate = env.getPublicTruffleFile(expanded);
            if (!candidate.isAbsolute()) {
                candidate = env.getPublicTruffleFile(baseDirectoryOf(including)).resolve(expanded);
            }
            candidate = candidate.normalize();
            if (!candidate.exists()) {
                return new IncludeSourceAccess.Failure(DiagnosticCode.RESOL_INCLUDE_NOT_FOUND, //
                                "included file not found: '" + decodedPath + "' (resolved to " + candidate + ")");
            }
            if (!candidate.isRegularFile()) {
                return new IncludeSourceAccess.Failure(DiagnosticCode.RESOL_INCLUDE_NOT_FILE, //
                                "included path is not a regular file: '" + decodedPath + "' (resolved to " + candidate + ")");
            }
            TruffleFile canonical = candidate.getCanonicalFile();
            URI key = canonical.toUri();
            LoadedSource cached = byCanonicalKey.get(key.toString());
            if (cached != null) {
                return new IncludeSourceAccess.Success(cached);
            }
            Source truffleSource = Source.newBuilder(SolvikLanguage.ID, canonical).build();
            SourceFile file = new SourceFile(nextId++, truffleSource.getName(), truffleSource.getCharacters().toString());
            LoadedSource loaded = new LoadedSource(key, parentDirectory(canonical), file);
            byCanonicalKey.put(key.toString(), loaded);
            sourcesById.put(file.id(), truffleSource);
            return new IncludeSourceAccess.Success(loaded);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return new IncludeSourceAccess.Failure(DiagnosticCode.RESOL_INCLUDE_IO, //
                            "cannot read include '" + decodedPath + "': " + e.getMessage());
        }
    }

    /** The id-to-Truffle-{@code Source} map for diagnostics, lowering, and tooling locations. */
    public Map<Integer, Source> sourcesById() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sourcesById));
    }

    /**
     * The including file's base directory. A synthetic root resolves against the environment's
     * current working directory, which is queried lazily so a denied environment denies includes
     * with {@code RESOL_INCLUDE_IO} instead of failing adapter construction.
     */
    private URI baseDirectoryOf(LoadedSource including) {
        if (including == root && rootBaseIsWorkingDirectory) {
            return trailingSlash(env.getCurrentWorkingDirectory().toUri());
        }
        return including.baseDirectory();
    }

    private static URI parentDirectory(TruffleFile file) {
        TruffleFile parent = file.getParent();
        if (parent == null) {
            return trailingSlash(file.toUri());
        }
        return trailingSlash(parent.toUri());
    }

    private static URI trailingSlash(URI uri) {
        String text = uri.toString();
        return text.endsWith("/") ? uri : URI.create(text + "/");
    }
}
