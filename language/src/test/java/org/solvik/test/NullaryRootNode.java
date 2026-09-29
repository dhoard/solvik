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
package org.solvik.test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A callable that does nothing, for tests that need a real {@code CallTarget} without building a
 * program. Building one through the language would make a runtime-invariant test depend on the whole
 * compiler, so a test that asks whether hashing agrees with equality would also be able to fail for a
 * reason unrelated to either.
 *
 * <p>The frame descriptor and language are both {@code null}, which Truffle permits for a root that is
 * never executed; every test that uses this one inspects values, not execution.
 */
final class NullaryRootNode extends RootNode {

    NullaryRootNode() {
        super(null, null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new AssertionError("the test fixture is never executed");
    }
}
