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
import com.oracle.truffle.api.object.DynamicObject.PutNode;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikAny;

/**
 * Creates a Solvik object and runs its constructor (docs/LANGUAGE_SPEC.md section 7). Calling a
 * class name allocates an instance with the class's fixed shape, evaluates the declaration
 * initializers and then the explicit {@code init} body through the constructor call target, and
 * yields the constructed object.
 */
@NodeInfo(shortName = "new", description = "Construct a Solvik object")
public final class SolvikNewNode extends SolvikExpressionNode {

    private final SolvikClass solvikClass;
    private final SolvikFunction constructor;
    @Children private final SolvikExpressionNode[] arguments;
    @Child private PutNode initializeShapeNode = PutNode.create();

    public SolvikNewNode(SolvikClass solvikClass, SolvikExpressionNode[] arguments) {
        this.solvikClass = solvikClass;
        this.constructor = solvikClass.constructor();
        this.arguments = arguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        // Constructing an instance is an active use of its class, so the class and its superclass chain
        // are initialized before the constructor runs and before the value arguments are evaluated
        // (docs/LANGUAGE_SPEC.md section 7). The steady-state guard is one boolean field test.
        solvikClass.ensureInitialized();
        SolvikAny object = new SolvikAny(solvikClass);
        // Add every declared property in declaration order so all instances of a class share one
        // stable shape and no undeclared member can ever be inserted.
        for (int i = 0; i < solvikClass.propertyCount(); i++) {
            initializeShapeNode.execute(object, solvikClass.propertyKey(i), null);
        }
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = object;
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        constructor.callTarget().call(callArguments);
        return object;
    }
}
