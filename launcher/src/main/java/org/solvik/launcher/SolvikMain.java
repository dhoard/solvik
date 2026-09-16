/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * there is no SimpleLanguage alias or compatibility mode.
 */
public final class SolvikMain {

    private static final String SOLVIK = "solvik";

    private SolvikMain() {
    }

    public static void main(String[] args) throws IOException {
        Source source;
        Map<String, String> options = new HashMap<>();
        String file = null;
        boolean launcherOutput = true;
        for (String arg : args) {
            if (arg.equals("--disable-launcher-output")) {
                launcherOutput = false;
            } else if (parseOption(options, arg)) {
                continue;
            } else if (file == null) {
                file = arg;
            }
        }

        if (file == null) {
            source = Source.newBuilder(SOLVIK, new InputStreamReader(System.in), "<stdin>").build();
        } else {
            source = Source.newBuilder(SOLVIK, new File(file)).build();
        }

        System.exit(executeSource(source, System.in, System.out, options, launcherOutput));
    }

    private static int executeSource(Source source, InputStream in, PrintStream out, Map<String, String> options, boolean launcherOutput) {
        PrintStream err = System.err;
        Context context;
        try {
            context = Context.newBuilder(SOLVIK).in(in).out(out).options(options).allowAllAccess(true).build();
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }

        if (launcherOutput) {
            out.println("== running on " + context.getEngine());
        }

        try {
            context.eval(source);
            return 0;
        } catch (PolyglotException ex) {
            if (ex.isInternalError()) {
                ex.printStackTrace();
            } else {
                err.println(ex.getMessage());
            }
            return 1;
        } finally {
            context.close();
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
