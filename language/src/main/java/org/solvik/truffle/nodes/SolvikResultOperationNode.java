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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikEnumVariant;

/**
 * A {@code Result} error-handling operation: one of {@code isOk}, {@code isErr}, {@code unwrap},
 * {@code unwrapErr}, {@code expect}, or {@code ignore} (docs/LANGUAGE_SPEC.md error-handling
 * operations). The node is produced by lowering with the two {@code Result} variant identities already
 * resolved by the compiler ({@code okVariant} is {@code Ok}, {@code errVariant} is {@code Err}), so the
 * fast path is a reference comparison against the receiver's variant with no name lookup or table
 * dispatch. {@code Ok} and {@code Err} carry their payload at positional index zero.
 *
 * <p>On a successful receiver the success or error payload is returned; on the wrong variant
 * {@code unwrap}, {@code unwrapErr}, and {@code expect} raise an established host runtime fault
 * ({@link SolvikException}), the same class of failure as an arithmetic or cast error. {@code ignore}
 * consumes the value and yields {@code Unit}. A safe call on a {@code null} receiver yields
 * {@code null} and evaluates no arguments, matching every other safe member call.
 */
@NodeInfo(shortName = "result", description = "A Result error-handling operation")
public final class SolvikResultOperationNode extends SolvikExpressionNode {

    /** The operation this node performs on a {@code Result} receiver. */
    public enum Operation {
        IS_OK,
        IS_ERR,
        UNWRAP,
        UNWRAP_ERR,
        EXPECT,
        IGNORE
    }

    private final Operation operation;
    private final boolean safe;
    private final SolvikEnumVariant okVariant;
    private final SolvikEnumVariant errVariant;
    @Child private SolvikExpressionNode receiver;
    /** The caller-supplied message operand for {@code expect}; {@code null} for every other operation. */
    @Child private SolvikExpressionNode message;

    public SolvikResultOperationNode(Operation operation, SolvikExpressionNode receiver, SolvikExpressionNode message, boolean safe, SolvikEnumVariant okVariant, SolvikEnumVariant errVariant) {
        this.operation = operation;
        this.receiver = receiver;
        this.message = message;
        this.safe = safe;
        this.okVariant = okVariant;
        this.errVariant = errVariant;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            // A safe call on a null receiver short-circuits to null before any argument is evaluated.
            return null;
        }
        SolvikEnumValue result = (SolvikEnumValue) target;
        boolean isOk = result.variant() == okVariant;
        switch (operation) {
            case IS_OK:
                return isOk;
            case IS_ERR:
                return !isOk;
            case IGNORE:
                return SolvikUnit.INSTANCE;
            case UNWRAP:
                if (isOk) {
                    return result.value(0);
                }
                return unwrapFailure("unwrap", result);
            case UNWRAP_ERR:
                if (!isOk) {
                    return result.value(0);
                }
                return unwrapFailure("unwrapErr", result);
            case EXPECT:
                Object suppliedMessage = message.executeGeneric(frame);
                if (isOk) {
                    return result.value(0);
                }
                return expectFailure(suppliedMessage, result);
            default:
                throw new IllegalStateException("unknown Result operation " + operation);
        }
    }

    /** The fault for unwrap/unwrapErr on the wrong variant; cold path, formatted at the boundary. */
    @TruffleBoundary
    private Object unwrapFailure(String operationName, SolvikEnumValue result) {
        throw SolvikException.unwrapFailed(operationName, result.variant().name(), this);
    }

    /** The fault for expect on an Err; cold path, formatted at the boundary. */
    @TruffleBoundary
    private Object expectFailure(Object suppliedMessage, SolvikEnumValue result) {
        throw SolvikException.expectFailed(String.valueOf(suppliedMessage), result.value(0), this);
    }
}
