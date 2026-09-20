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
import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
/**
 * The Solvik command-line launcher. It builds a Solvik context, evaluates the given source file (or
 * standard input), and returns a process exit code. Only the {@code solvik} language id is used;
 * there is no SimpleLanguage alias or compatibility mode. Standard output carries only program
 * output: no interpreter, engine, or other launcher information is printed.
 */
public final class SolvikMain {

    private static final String SOLVIK = "solvik";

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
        for (String arg : args) {
            if (parseOption(opts, arg)) {
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

        return executeSource(source, in, out, err, opts);
    }

    /**
     * Evaluates a prepared Solvik source and returns the process exit code. Exposed so the launcher
     * module tests can exercise the evaluation and exit-code behavior without terminating the test
     * JVM.
     */
    public static int executeSource(Source source, InputStream in, PrintStream out, PrintStream err, Map<String, String> options) {
        Context context;
        try {
            context = Context.newBuilder(SOLVIK).in(in).out(out).err(err).options(options).allowAllAccess(true).build();
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }

        try {
            context.eval(source);
            return 0;
        } catch (PolyglotException ex) {
            if (ex.isExit()) {
                return ex.getExitStatus();
            }
            if (ex.isInternalError()) {
                ex.printStackTrace();
            } else {
                err.println(ex.getMessage());
            }
            return 1;
        } finally {
            close(context);
        }
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
