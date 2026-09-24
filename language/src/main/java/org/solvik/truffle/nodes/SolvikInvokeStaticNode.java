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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikClass;

/**
 * A call to a {@code static} method (docs/LANGUAGE_SPEC.md section 7). As with an ordinary function
 * call, the target is resolved statically during lowering and the call goes through the target's
 * {@link com.oracle.truffle.api.RootCallTarget}; unlike an instance method call there is no receiver,
 * so the declaring class is carried to make the call an active use of that class.
 *
 * <p>The class is initialized before the arguments are evaluated, matching the initialization order of
 * an active use: reading or evaluating anything that first requires the class cannot observe a
 * partially initialized class. After the first call, the guard is one boolean field test.
 */
@NodeInfo(shortName = "static-call", description = "A Solvik static method call")
public final class SolvikInvokeStaticNode extends SolvikExpressionNode {

    private final SolvikClass owner;
    private final SolvikFunction function;
    @Children private final SolvikExpressionNode[] arguments;

    public SolvikInvokeStaticNode(SolvikClass owner, SolvikFunction function, SolvikExpressionNode[] arguments) {
        this.owner = owner;
        this.function = function;
        this.arguments = arguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        owner.ensureInitialized();
        Object[] values = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            values[i] = arguments[i].executeGeneric(frame);
        }
        return function.callTarget().call(values);
    }
}
