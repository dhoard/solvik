/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
/**
 * The Solvik command-line launcher. It builds a Solvik context, evaluates the given source file (or
 * standard input), and returns a process exit code. Only the {@code solvik} language id is used;
 * there is no SimpleLanguage alias or compatibility mode. Standard output carries only program
 * output: no interpreter, engine, or other launcher information is printed.
 */
public final class SolvikMain {

    private static final String SOLVIK = "solvik";

    /** Launcher flag: run compile-only validation (static analysis without executing the program). */
    static final String COMPILE_ONLY_FLAG = "--compile-only";
    /** Launcher flag prefix: {@code --diagnostics-json=<path>} writes structured compile diagnostics. */
    static final String DIAGNOSTICS_JSON_PREFIX = "--diagnostics-json=";
    /** Launcher flag prefix: {@code --run-json=<path>} writes the structured execute outcome. */
    static final String RUN_JSON_PREFIX = "--run-json=";

    private SolvikMain() {
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.in, System.out, System.err, new HashMap<>()));
    }

    /**
     * Selects the source (a positional file argument, otherwise standard input), evaluates it, and
     * returns the process exit code. Extracted from {@link #main} so argument parsing, file/stdin
     * selection, and the exit-code branches are testable in-process without {@code System.exit};
     * {@link #main} remains the only entry point that terminates the JVM.
     *
     * @param args        the raw command-line arguments
     * @param in          the standard input (used when no file argument is present)
     * @param out         the standard output for program output
     * @param err         the standard error for diagnostics
     * @param options     pre-populated language options; may be {@code null} for a fresh map
     */
    public static int run(String[] args, InputStream in, PrintStream out, PrintStream err, Map<String, String> options) throws IOException {
        Source source;
        Map<String, String> opts = options != null ? options : new HashMap<>();
        String file = null;
        boolean compileOnly = false;
        String diagnosticsPath = null;
        String runPath = null;
        for (String arg : args) {
            if (COMPILE_ONLY_FLAG.equals(arg)) {
                compileOnly = true;
            } else if (arg.startsWith(DIAGNOSTICS_JSON_PREFIX)) {
                diagnosticsPath = arg.substring(DIAGNOSTICS_JSON_PREFIX.length());
            } else if (arg.startsWith(RUN_JSON_PREFIX)) {
                runPath = arg.substring(RUN_JSON_PREFIX.length());
            } else if (parseOption(opts, arg)) {
                continue;
            } else if (file == null) {
                file = arg;
            }
        }

        if (file == null) {
            source = Source.newBuilder(SOLVIK, new InputStreamReader(in), "<stdin>").build();
        } else {
            source = Source.newBuilder(SOLVIK, new File(file)).build();
        }

        if (compileOnly) {
            return compileSource(source, out, err, opts, diagnosticsPath);
        }
        return executeSource(source, in, out, err, opts, runPath);
    }

    /**
     * Runs compile-only validation: the language front end parses, resolves includes, performs static
     * semantic analysis and lowering, but the produced call target is never invoked, so no application
     * code executes and there are zero application observables. On success nothing is written and the
     * exit code is {@code 0}. On a compile error, when {@code diagnosticsPath} is set, the stable
     * {@code SOLV-*} codes and source locations are written there as one compact JSON object read from
     * the guest exception through the interop API, never by parsing human-readable text.
     *
     * <p>This is the compile-only boundary required by the TCK: it is genuine because {@link
     * Context#parse(Source)} returns a callable value without calling it, so {@code println}, mutation,
     * {@code exit}, static initializers, and constructors cannot run. The caller's {@code out} stream is
     * wired to the context but receives nothing, because a program never executes during parsing; tests
     * assert {@code out} stays empty to prove the absence of application observables.
     */
    public static int compileSource(Source source, PrintStream out, PrintStream err, Map<String, String> options, String diagnosticsPath) throws IOException {
        Context context;
        try {
            context = Context.newBuilder(SOLVIK).out(out).err(err).options(options).allowAllAccess(true).build();
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        try {
            context.parse(source);
            return 0;
        } catch (PolyglotException ex) {
            if (ex.isInternalError()) {
                // A VM internal error during compilation must not be reported as a legitimate
                // COMPILE_ERROR (a crash cannot satisfy a compile-error expectation); it is surfaced
                // separately and writes no structured compile diagnostics.
                ex.printStackTrace();
                return 1;
            }
            // Human-readable diagnostics stay on stderr for users, matching normal execution; the
            // structured form is written additionally (never instead) when a path was requested.
            err.println(ex.getMessage());
            String json = diagnosticsJson(ex, source);
            if (json != null && diagnosticsPath != null) {
                Files.writeString(Path.of(diagnosticsPath), json + "\n", StandardCharsets.UTF_8);
            }
            return 1;
        } finally {
            close(context);
        }
    }

    /**
     * Reads the structured diagnostics carried by a guest compile-error value through interop and
     * serializes them as one compact JSON line, or returns {@code null} when the exception carries no
     * structured diagnostics (a non-compile failure). The serialization itself lives in {@link
     * DiagnosticsJson}; this only bridges the {@link PolyglotException} guest value to it.
     */
    private static String diagnosticsJson(PolyglotException ex, Source source) {
        Value guest = ex.getGuestObject();
        if (guest == null || !guest.hasMembers()) {
            return null;
        }
        Value diagnostics = guest.getMember("diagnostics");
        if (diagnostics == null || !diagnostics.hasArrayElements()) {
            return null;
        }
        return DiagnosticsJson.compileError(source.getName(), diagnostics);
    }

    /**
     * Evaluates a prepared Solvik source and returns the process exit code. Exposed so the launcher
     * module tests can exercise the evaluation and exit-code behavior without terminating the test
     * JVM. When {@code runPath} is non-null, a one-line structured execute outcome is written to that
     * path on every exit branch (normal, {@code exit(n)}, runtime failure, internal error) so a
     * consuming adapter can distinguish {@code exit(0)} from {@code exit(1)} from a runtime failure
     * without parsing human-readable stderr text.
     */
    public static int executeSource(Source source, InputStream in, PrintStream out, PrintStream err, Map<String, String> options, String runPath) throws IOException {
        Context context;
        try {
            context = Context.newBuilder(SOLVIK).in(in).out(out).err(err).options(options).allowAllAccess(true).build();
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            writeRunJson(runPath, RunResultJson.internalFailure());
            return 1;
        }

        try {
            context.eval(source);
            writeRunJson(runPath, RunResultJson.normalExit(0));
            return 0;
        } catch (PolyglotException ex) {
            if (ex.isExit()) {
                writeRunJson(runPath, RunResultJson.normalExit(ex.getExitStatus()));
                return ex.getExitStatus();
            }
            if (ex.isInternalError()) {
                ex.printStackTrace();
                writeRunJson(runPath, RunResultJson.internalFailure());
            } else {
                err.println(ex.getMessage());
                writeRunJson(runPath, RunResultJson.runtimeFailure(ex, source));
            }
            return 1;
        } finally {
            close(context);
        }
    }

    /**
     * Evaluates a prepared Solvik source and returns the process exit code without the structured
     * execute channel. Retained for callers that do not need the machine-readable outcome.
     */
    public static int executeSource(Source source, InputStream in, PrintStream out, PrintStream err, Map<String, String> options) throws IOException {
        return executeSource(source, in, out, err, options, null);
    }

    private static void writeRunJson(String runPath, String json) throws IOException {
        if (runPath == null) {
            return;
        }
        Files.writeString(Path.of(runPath), json + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Closes a context after evaluation. A context that exited through the Solvik {@code exit}
     * function is already closed, so closing it again re-throws the exit notification; that duplicate
     * is ignored because its status was already returned from the evaluation.
     */
    private static void close(Context context) {
        try {
            context.close();
        } catch (PolyglotException ex) {
            if (!ex.isExit()) {
                throw ex;
            }
        }
    }

    private static boolean parseOption(Map<String, String> options, String arg) {
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            return false;
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = "true";
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }
        options.put(key, value);
        return true;
    }
}
