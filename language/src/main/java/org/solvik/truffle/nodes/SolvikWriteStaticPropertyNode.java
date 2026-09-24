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
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikStaticCell;

/**
 * Writes the class-level cell of a {@code static} property (docs/LANGUAGE_SPEC.md section 7). As for a
 * static read, the cell is resolved during lowering, so no name lookup happens in guest code, and the
 * class name in the source assignment target contributes no run-time evaluation. Mutability is enforced
 * at compile time, so the store itself is unconditional.
 *
 * <p>A static write is an active use of the declaring class, so the class is initialized before the
 * stored value is evaluated: the initializer of an initialized class contributes one boolean test.
 */
@NodeInfo(shortName = ".static=", description = "Write a Solvik static property")
public final class SolvikWriteStaticPropertyNode extends SolvikStatementNode {

    private final SolvikClass owner;
    private final SolvikStaticCell cell;
    @Child private SolvikExpressionNode value;

    public SolvikWriteStaticPropertyNode(SolvikClass owner, SolvikStaticCell cell, SolvikExpressionNode value) {
        this.owner = owner;
        this.cell = cell;
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        owner.ensureInitialized();
        cell.set(value.executeGeneric(frame));
    }
}
