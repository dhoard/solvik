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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikBuiltinCollection;
import org.solvik.truffle.object.SolvikObject;

/**
 * Invokes an instance method. With single inheritance, an ordinary call must dispatch on the
 * receiver's runtime class, so the node looks the method up in the receiver's {@code SolvikClass}
 * method table by name. A {@code super.member(...)} call passes a fixed target and bypasses that
 * dispatch to run the immediate superclass implementation. The receiver is always passed as the
 * hidden first frame argument so the method body reads it through the {@code this} slot. A safe call
 * ({@code receiver?.member(...)}) evaluates the arguments only when the receiver is non-null and
 * otherwise yields {@code null}.
 */
@NodeInfo(shortName = "call", description = "Invoke a Solvik instance method")
public final class SolvikInvokeMethodNode extends SolvikExpressionNode {

    /** Direct target for {@code super.member(...)}; {@code null} selects virtual dispatch. */
    private final SolvikFunction directTarget;
    private final String methodName;
    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Children private final SolvikExpressionNode[] arguments;

    /** Creates a virtually dispatched call by method name. */
    public SolvikInvokeMethodNode(String methodName, SolvikExpressionNode receiver, SolvikExpressionNode[] arguments) {
        this(methodName, receiver, arguments, false);
    }

    /** Creates a virtually dispatched call, optionally guarded by a safe member access. */
    public SolvikInvokeMethodNode(String methodName, SolvikExpressionNode receiver, SolvikExpressionNode[] arguments, boolean safe) {
        this.directTarget = null;
        this.methodName = methodName;
        this.safe = safe;
        this.receiver = receiver;
        this.arguments = arguments;
    }

    /** Creates a direct call to a fixed implementation, as required by {@code super.member(...)}. */
    public SolvikInvokeMethodNode(SolvikFunction target, SolvikExpressionNode receiver, SolvikExpressionNode[] arguments) {
        this.directTarget = target;
        this.methodName = target.name();
        this.safe = false;
        this.receiver = receiver;
        this.arguments = arguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object instance = receiver.executeGeneric(frame);
        if (safe && instance == null) {
            return null;
        }
        if (instance instanceof SolvikBuiltinCollection collection) {
            // A built-in collection receiver has no method table, so dispatch through its single
            // invoke. The source arguments are evaluated exactly once, each without the receiver.
            Object[] callArguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                callArguments[i] = arguments[i].executeGeneric(frame);
            }
            return collection.invoke(methodName, callArguments, this);
        }
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = instance;
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        SolvikFunction target = directTarget;
        if (target == null) {
            if (!(instance instanceof SolvikObject object)) {
                throw new IllegalStateException("method call receiver is not a Solvik object");
            }
            target = object.solvikClass().method(methodName);
            if (target == null) {
                throw new IllegalStateException("no method '" + methodName + "' on class " + object.solvikClass().name());
            }
        }
        return target.callTarget().call(callArguments);
    }
}
