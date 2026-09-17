/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikContext;
import org.solvik.truffle.SolvikUnit;

/**
 * The predeclared Solvik {@code exit(code: Int): Unit} function (docs/LANGUAGE_SPEC.md section 6).
 * It terminates the enclosing context with the given status through the public Truffle
 * {@link com.oracle.truffle.api.TruffleContext#closeExited} operation, which the GraalVM polyglot
 * engine surfaces to an embedder as an exit {@code PolyglotException}; the launcher maps that status
 * to the process exit code. The end of this method is unreachable because {@code closeExited} never
 * returns.
 */
@NodeInfo(shortName = "exit", description = "Terminates the Solvik context with the given exit status")
public final class SolvikExitNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode code;

    public SolvikExitNode(SolvikExpressionNode code) {
        this.code = code;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        int status = code.executeInt(frame);
        SolvikContext.get(this).getEnv().getContext().closeExited(this, status);
        return SolvikUnit.INSTANCE;
    }
}
