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
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikContext;
import org.solvik.truffle.SolvikUnit;

/**
 * The predeclared Solvik {@code exit(code: Int)} function (docs/LANGUAGE_SPEC.md section 6); like
 * every callable declared without a return type it returns no value.
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
