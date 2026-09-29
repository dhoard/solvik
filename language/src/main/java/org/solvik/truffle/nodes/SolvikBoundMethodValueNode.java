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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * Creates a bound method value (docs/LANGUAGE_SPEC.md section 6, "Bound method references"). The
 * receiver expression is evaluated exactly once here and retained strongly by the value the node
 * returns, and the method's implicit receiver does not appear in the value's function type — the
 * arity a guest call supplies excludes it, and {@link SolvikFunctionValue#withCapturedState} supplies
 * it on each invocation.
 *
 * <h2>Why the target is resolved when the value is created</h2>
 *
 * An ordinary reference dispatches on the receiver's runtime class, and the specification states that
 * dispatch as a property of the call: "A reference obtained through a class or interface type invokes
 * the implementation selected by the captured receiver's runtime class." A class's virtual method
 * table is fixed by lowering — inherited methods, resolved interface defaults, delegated
 * implementations, and a class's own overrides are all merged into it before any guest code runs, and
 * nothing installs an entry afterwards — so the entry that the receiver's class holds at creation is
 * the same entry it holds at every later call. Resolving the target at creation is therefore
 * equivalent to resolving it at each call, and it is what keeps {@link SolvikFunctionValue#target()}
 * stable for the whole life of the value, which
 * {@link SolvikFunctionDispatchNode}'s monomorphic {@link com.oracle.truffle.api.nodes.DirectCallNode}
 * cache requires: a target that could change would make that inlined cache unsound.
 *
 * <p>A {@code super.method} reference passes its implementation to the constructor and performs no
 * lookup at all, which is how it bypasses virtual redispatch and reaches the immediate superclass
 * implementation, matching an immediate {@code super.method(...)} call.
 *
 * <h2>Why each evaluation allocates</h2>
 *
 * Every successful evaluation of a bound method-reference expression creates a distinct function-value
 * identity, even for the same receiver and method, so nothing is memoized here. Copying the value
 * through bindings preserves that identity because a binding stores the value rather than the
 * expression.
 */
@NodeInfo(shortName = "bound", description = "Create a bound method value")
public final class SolvikBoundMethodValueNode extends SolvikExpressionNode {

    /** The fixed implementation of a {@code super.method} reference; {@code null} selects virtual dispatch. */
    private final SolvikFunction directTarget;
    private final String methodName;
    private final boolean safe;
    @Child private SolvikExpressionNode receiver;

    /** Creates a node whose value dispatches on the receiver's runtime class table. */
    public SolvikBoundMethodValueNode(String methodName, SolvikExpressionNode receiver, boolean safe) {
        this.directTarget = null;
        this.methodName = methodName;
        this.safe = safe;
        this.receiver = receiver;
    }

    /** Creates a node whose value invokes {@code target} without virtual redispatch. */
    public SolvikBoundMethodValueNode(SolvikFunction target, SolvikExpressionNode receiver) {
        this.directTarget = target;
        this.methodName = target.name();
        this.safe = false;
        this.receiver = receiver;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object instance = receiver.executeGeneric(frame);
        if (instance == null) {
            // A safe reference through a null receiver yields a null function value and creates no bound
            // function. A non-safe read of a nullable receiver was refused by static analysis, so reaching
            // here unsafely means a value the type system excludes.
            if (safe) {
                return null;
            }
            throw CompilerDirectives.shouldNotReachHere("a bound method reference has a non-null receiver");
        }
        SolvikFunction target = directTarget;
        if (target == null) {
            if (!(instance instanceof SolvikAny object)) {
                throw CompilerDirectives.shouldNotReachHere("a bound method reference receiver is a Solvik object");
            }
            target = object.solvikClass().method(methodName);
            if (target == null) {
                throw new IllegalStateException("no method '" + methodName + "' on class " + object.solvikClass().name());
            }
        }
        return SolvikFunctionValue.bound(target.callTarget(), instance, methodName);
    }
}
