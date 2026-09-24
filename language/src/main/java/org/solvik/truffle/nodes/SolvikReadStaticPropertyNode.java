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
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikStaticCell;

/**
 * Reads the class-level cell of a {@code static} property (docs/LANGUAGE_SPEC.md section 7). The cell
 * is resolved to its declaring class during lowering and held as a final field, so a static read
 * compiles to one field load and no name lookup happens in guest code. The receiver position of the
 * source reference ({@code Counter.limit}) is a class name, not a value, and produces no run-time
 * evaluation.
 *
 * <p>A static read is an active use of the declaring class, so the class is initialized first: the
 * steady-state path is one boolean test, and only the first read enters the initializer.
 */
@NodeInfo(shortName = ".static", description = "Read a Solvik static property")
public final class SolvikReadStaticPropertyNode extends SolvikExpressionNode {

    private final SolvikClass owner;
    private final SolvikStaticCell cell;

    public SolvikReadStaticPropertyNode(SolvikClass owner, SolvikStaticCell cell) {
        this.owner = owner;
        this.cell = cell;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        owner.ensureInitialized();
        return cell.get();
    }
}
