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
package org.solvik.test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.parser.IncludeResolver;
import org.solvik.parser.IncludeSourceAccess;
import org.solvik.parser.LoadedSource;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;

/**
 * An in-memory {@link IncludeSourceAccess} for resolver tests. It models a tiny virtual filesystem
 * with POSIX-like absolute paths, normalizes {@code .}/{@code ..} in include paths, assigns source
 * ids on first canonical load, and returns the cached source thereafter. It has no Truffle
 * dependency, so resolution and semantic tests can run without a polyglot context.
 */
final class VirtualIncludeFiles implements IncludeSourceAccess {

    private static final URI ROOT_DIRECTORY = URI.create("mem:/");

    private final Map<String, String> contents;
    private final Map<String, LoadedSource> byCanonicalKey = new HashMap<>();
    private final LoadedSource root;
    private int nextId = 1;

    private VirtualIncludeFiles(String rootName, Map<String, String> rawContents) {
        this.contents = new HashMap<>();
        for (Map.Entry<String, String> entry : rawContents.entrySet()) {
            contents.put(normalize("/" + entry.getKey()), entry.getValue());
        }
        SourceFile rootFile = new SourceFile(0, rootName, contents.get(normalize("/" + rootName)));
        this.root = new LoadedSource(URI.create("mem:" + normalize("/" + rootName)), ROOT_DIRECTORY, rootFile);
        byCanonicalKey.put(canonicalPath(root), root);
    }

    /** Parses {@code rootName} and resolves its includes against the virtual files. */
    static IncludeResolutionResult resolve(String rootName, Map<String, String> contents) {
        VirtualIncludeFiles access = new VirtualIncludeFiles(rootName, contents);
        SolvikParseResult parsed = SolvikParser.parse(access.root.file());
        if (!parsed.isSuccess()) {
            throw new AssertionError("root must parse: " + parsed.diagnostics().all());
        }
        return IncludeResolver.resolve(parsed.requireAst(), access);
    }

    @Override
    public LoadedSource root() {
        return root;
    }

    @Override
    public LoadResult load(LoadedSource including, String decodedPath) {
        String path;
        if (decodedPath.startsWith("/")) {
            path = normalize(decodedPath);
        } else {
            String base = canonicalPath(including);
            int slash = base.lastIndexOf('/');
            path = normalize(base.substring(0, slash + 1) + decodedPath);
        }
        if (!contents.containsKey(path)) {
            return new IncludeSourceAccess.Failure(org.solvik.diagnostic.DiagnosticCode.RESOL_INCLUDE_NOT_FOUND, "not found: " + path);
        }
        LoadedSource cached = byCanonicalKey.get(path);
        if (cached != null) {
            return new IncludeSourceAccess.Success(cached);
        }
        SourceFile file = new SourceFile(nextId++, fileName(path), contents.get(path));
        LoadedSource loaded = new LoadedSource(URI.create("mem:" + path), ROOT_DIRECTORY, file);
        byCanonicalKey.put(path, loaded);
        return new IncludeSourceAccess.Success(loaded);
    }

    private static String canonicalPath(LoadedSource source) {
        return source.canonicalKey().getSchemeSpecificPart();
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else {
                parts.addLast(part);
            }
        }
        return "/" + String.join("/", parts);
    }
}
