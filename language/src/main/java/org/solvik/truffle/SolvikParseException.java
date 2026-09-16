/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * A compile-time failure surfaced to the Truffle runtime. It carries every Solvik diagnostic
 * produced by parsing or static analysis so a program with compile-time errors is rejected before
 * lowering and never produces an executable call target.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("serial")
public final class SolvikParseException extends AbstractTruffleException {

    private static final long serialVersionUID = 1L;

    private final transient Source source;
    private final int startOffset;
    private final int length;

    private SolvikParseException(String message, Source source, int startOffset, int length) {
        super(message);
        this.source = source;
        this.startOffset = startOffset;
        this.length = length;
    }

    /** Builds a parse error whose message lists every diagnostic and whose location is the first. */
    @TruffleBoundary
    public static SolvikParseException create(Source source, SourceFile file, DiagnosticBag diagnostics) {
        SourceSpan primary = diagnostics.all().isEmpty() ? SourceSpan.of(0, 0) : diagnostics.all().get(0).span();
        StringBuilder message = new StringBuilder();
        String sep = "";
        for (Diagnostic diagnostic : diagnostics.all()) {
            message.append(sep);
            message.append(file.formatLocation(diagnostic.span())).append(": ").append(diagnostic);
            sep = "\n";
        }
        return new SolvikParseException(message.toString(), source, primary.startOffset(), primary.length());
    }

    @ExportMessage
    ExceptionType getExceptionType() {
        return ExceptionType.PARSE_ERROR;
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return source != null;
    }

    @ExportMessage(name = "getSourceLocation")
    @TruffleBoundary
    SourceSection getSourceSection() throws UnsupportedMessageException {
        if (source == null) {
            throw UnsupportedMessageException.create();
        }
        int start = Math.min(startOffset, source.getLength());
        int len = Math.min(length, source.getLength() - start);
        return source.createSection(start, len);
    }
}
