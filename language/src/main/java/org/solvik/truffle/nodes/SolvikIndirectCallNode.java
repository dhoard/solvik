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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * Invokes a function through a function-typed value (docs/LANGUAGE_SPEC.md section 6, "Function
 * values and invocation"). A call whose target is statically known keeps its existing statically
 * resolved path; this node is the additional path and never a replacement for it, so a call such as
 * {@code sum(1, 2)} is never lowered into creating a function value and invoking it.
 *
 * <h2>Evaluation order is this node's job</h2>
 *
 * The callee is evaluated exactly once, before any argument, and the arguments are then evaluated
 * exactly once from left to right. That ordering is stated here rather than expressed as a sequence
 * node holding the callee among its argument children: the callee operand must be evaluated <em>for
 * its value</em>, since its static type is a function type, and routing it through the ordinary
 * argument machinery of a call would invoke it.
 *
 * <p>Arity is checked before argument-type compatibility by semantic analysis, which is a compile-time
 * ordering and needs no runtime counterpart. Nothing here validates the call: a program that reaches
 * it has already been proven to supply the right number of compatible arguments, so a runtime check
 * would be dead code, and writing one would put a second definition of a rule the compiler owns where
 * the two could disagree.
 *
 * <h2>Why evaluation order lives in {@code executeGeneric} and the call does not</h2>
 *
 * A {@code @Specialization} over a single {@code @Child} cannot also specialize over the array of
 * argument children, so ordering and dispatch are separate nodes: this one evaluates in the order the
 * specification states and hands the evaluated callee and arguments to
 * {@link SolvikFunctionDispatchNode}, which owns the monomorphic-direct/polymorphic-indirect choice.
 * The argument array is built here and passed as-is, which is what lets a directly-called target
 * observe the same {@code Frame} layout it observes through a direct call.
 */
@NodeInfo(shortName = "indirect", description = "Call through a function-typed value")
public final class SolvikIndirectCallNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode target;
    @Children private final SolvikExpressionNode[] arguments;
    @Child private SolvikFunctionDispatchNode dispatch;

    public SolvikIndirectCallNode(SolvikExpressionNode target, SolvikExpressionNode[] arguments, SolvikFunctionDispatchNode dispatch) {
        this.target = target;
        this.arguments = arguments.clone();
        this.dispatch = dispatch;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object callee = target.executeGeneric(frame);
        Object[] supplied = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            supplied[i] = arguments[i].executeGeneric(frame);
        }
        if (!(callee instanceof SolvikFunctionValue function)) {
            // Unreachable for a checked program: a function type is inhabited only by a function value
            // and by null, and analysis refuses to invoke a nullable callee without a non-null
            // refinement. There is deliberately no fallback, because inventing a runtime shape for a
            // value the type system excludes would be a second type system.
            throw CompilerDirectives.shouldNotReachHere("a function-typed callee is never a non-function value");
        }
        return dispatch.execute(function, supplied);
    }
}
