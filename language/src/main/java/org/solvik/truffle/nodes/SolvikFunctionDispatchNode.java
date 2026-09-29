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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * The call-dispatch half of an indirect call: given an already-evaluated function value and an
 * already-evaluated argument array, invoke it (docs/LANGUAGE_SPEC.md section 6).
 *
 * <p>Dispatch is factored out of {@link SolvikIndirectCallNode} because the Truffle DSL cannot
 * specialize over an array of {@code @Child} expression nodes, and the argument list must stay a list
 * of independent expressions so that each is evaluated exactly once and in order. Splitting the
 * concern lets the target specialization live here while the evaluation ordering lives in the caller.
 *
 * <p>This node validates nothing. Arity and argument types were proven by semantic analysis against
 * the callee's function type, so a runtime re-check would be dead code at best and a rival definition
 * of those rules at worst.
 *
 * <h2>Two specializations, one monomorphic and one not</h2>
 *
 * While a site sees one function value it caches that value and calls through
 * {@link DirectCallNode}, which the engine may inline. A reference to a declared function reaches this
 * through its declaration's one canonical value, so a call through such a binding is compiled as well
 * as a direct call — the point of caching, given how common that shape is. A bound or anonymous value
 * is created fresh per evaluation, so a site that keeps seeing new values exhausts the rebuild budget
 * and settles onto {@link IndirectCallNode}, which is correct for any target. A site that stores one
 * bound value in a binding and calls it in a loop is monomorphic and stays inlined, which is the
 * other reason the cache is keyed on the value rather than on whether the callee was constant.
 */
@NodeInfo(shortName = "indirect", description = "Invoke a function value")
public abstract class SolvikFunctionDispatchNode extends Node {

    /**
     * @param function  the evaluated callee
     * @param arguments the evaluated guest arguments in source order, without any receiver
     */
    public abstract Object execute(SolvikFunctionValue function, Object[] arguments);

    @Specialization(limit = "1", guards = "function == expected")
    Object doMonomorphic(SolvikFunctionValue function, Object[] arguments, //
                    @Cached("function") SolvikFunctionValue expected, //
                    @Cached("create(function.target())") DirectCallNode directCall) {
        return directCall.call(SolvikFunctionValue.withCapturedState(function, arguments));
    }

    /** The settled form of a site that has seen more than one value, or a constant site that changed. */
    @Specialization(replaces = "doMonomorphic")
    Object doPolymorphic(SolvikFunctionValue function, Object[] arguments, //
                    @Cached IndirectCallNode indirectCall) {
        return indirectCall.call(function.target(), SolvikFunctionValue.withCapturedState(function, arguments));
    }
}
