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

import java.util.List;
import java.util.Set;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikAny;

/**
 * Lowers a {@code try} statement (docs/LANGUAGE_SPEC.md sections 22 and 23): an operation grouped
 * with zero or more ordered catch clauses and an optional finally clause. The run phase unwinds the
 * try body, separating a guest throw from the other abrupt exits a body can take — {@code return},
 * {@code break}, {@code continue}, and a {@code ?} propagation exit. When a guest throw is present,
 * handlers are consulted in source order and the first whose type is nominally compatible runs; its
 * own throw, if any, replaces the propagating value.
 *
 * <p>The finally clause runs on <em>every</em> exit path (docs/LANGUAGE_SPEC.md section 22.3): normal
 * completion, a handled throw, an unhandled throw still propagating, and each {@code return} /
 * {@code break} / {@code continue} / {@code ?} exit. Its completion follows Java's
 * last-abrupt-completion-wins rule: if the finally clause itself completes abruptly — by throw,
 * {@code return}, {@code break}, {@code continue}, or {@code ?} — that transition <em>replaces</em>
 * whatever was in flight, and the replaced throw is simply discarded (unchecked exceptions carry no
 * suppressed chain, and Solvik has no checked-exception or try-with-resources feature that would need
 * one). If the finally clause completes normally, the transition already in flight continues
 * unchanged. Internal failures ({@code AbstractTruffleException}) are not caught here and escape
 * untouched.
 */
@NodeInfo(shortName = "try", description = "A Solvik try/catch/finally statement")
public final class SolvikTryNode extends SolvikStatementNode {

    /** One ordered {@code catch (binding: ExceptionType) block} clause. {@code matchedTypes} is the set of
     * exception class names this handler catches: its own type plus every subclass (built-in bases included). */
    public record CatchHandler(String bindingName, int slot, Set<String> matchedTypes, SolvikStatementNode handlerBody) {
    }

    @Child private SolvikStatementNode tryBlock;
    private final List<CatchHandler> handlers;
    @Child private SolvikStatementNode finallyBlock;

    public SolvikTryNode(SolvikStatementNode tryBlock, List<CatchHandler> handlers, SolvikStatementNode finallyBlock) {
        this.tryBlock = tryBlock;
        this.handlers = List.copyOf(handlers);
        this.finallyBlock = finallyBlock;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        PendingExit pending = runTryBody(frame);
        if (finallyBlock != null) {
            pending = runFinally(frame, pending);
        }
        pending.exit();
    }

    /**
     * Runs the try body and returns the abrupt transition still in flight afterward, or {@link
     * PendingExit#normal()} when the body (or a selected handler) completed normally. A guest throw is
     * dispatched to the handlers first; the remaining abrupt exits are captured here so the finally
     * clause runs before the transition continues outward.
     */
    private PendingExit runTryBody(VirtualFrame frame) {
        try {
            tryBlock.executeVoid(frame);
        } catch (SolvikGuestException ge) {
            return dispatchGuest(frame, ge);
        } catch (SolvikReturnException e) {
            return PendingExit.ofReturn(e);
        } catch (SolvikBreakException e) {
            return PendingExit.ofBreak(e);
        } catch (SolvikContinueException e) {
            return PendingExit.ofContinue(e);
        } catch (SolvikPropagationException e) {
            // A {@code ?} exit unwinds to the enclosing Result-returning function's root node; the
            // finally clause must still run first, so the pending signal is carried, not rethrown.
            return PendingExit.ofReturn(e);
        }
        return PendingExit.normal();
    }

    /** Dispatches a collected guest throw, returning the transition that still needs to escape. */
    private PendingExit dispatchGuest(VirtualFrame frame, SolvikGuestException ge) {
        for (CatchHandler handler : handlers) {
            if (!isMatched(ge.value(), handler.matchedTypes())) {
                continue;
            }
            frame.setObject(handler.slot(), ge.value());
            try {
                handler.handlerBody().executeVoid(frame);
                return PendingExit.normal(); // handled normally: no transition escapes
            } catch (SolvikGuestException failure) {
                return PendingExit.ofGuest(failure); // the handler's own throw replaces the original
            } catch (SolvikReturnException e) {
                return PendingExit.ofReturn(e);
            } catch (SolvikBreakException e) {
                return PendingExit.ofBreak(e);
            } catch (SolvikContinueException e) {
                return PendingExit.ofContinue(e);
            } catch (SolvikPropagationException e) {
                return PendingExit.ofReturn(e);
            }
        }
        return PendingExit.ofGuest(ge); // no compatible handler: the original throw still escapes
    }

    /**
     * Runs the finally clause and returns the single abrupt transition that escapes once it returns.
     * Java's last-abrupt-completion-wins rule (docs/LANGUAGE_SPEC.md section 22.3): a finally clause
     * that completes normally leaves the pending transition in place, while a finally clause that
     * completes abruptly — by throw, {@code return}, {@code break}, {@code continue}, or {@code ?} —
     * replaces it entirely. A replaced throw is discarded, never recorded as suppressed.
     */
    private PendingExit runFinally(VirtualFrame frame, PendingExit pending) {
        try {
            finallyBlock.executeVoid(frame);
            return pending;
        } catch (SolvikGuestException fe) {
            return PendingExit.ofGuest(fe);
        } catch (SolvikReturnException fe) {
            return PendingExit.ofReturn(fe);
        } catch (SolvikBreakException fe) {
            return PendingExit.ofBreak(fe);
        } catch (SolvikContinueException fe) {
            return PendingExit.ofContinue(fe);
        } catch (SolvikPropagationException fe) {
            return PendingExit.ofReturn(fe);
        }
    }

    /**
     * The abrupt transition leaving a {@code try} statement, carried across the finally clause so that
     * {@code finally} runs on every exit path and exactly one transition escapes. At most one of its
     * fields is set; {@link #normal()} represents a normal completion with nothing in flight.
     */
    private record PendingExit(SolvikGuestException guest, ControlFlowException control) {

        static PendingExit normal() {
            return new PendingExit(null, null);
        }

        static PendingExit ofGuest(SolvikGuestException value) {
            return new PendingExit(value, null);
        }

        static PendingExit ofReturn(ControlFlowException value) {
            return new PendingExit(null, value);
        }

        static PendingExit ofBreak(SolvikBreakException value) {
            return new PendingExit(null, value);
        }

        static PendingExit ofContinue(SolvikContinueException value) {
            return new PendingExit(null, value);
        }

        /** Throws the single carried transition, or returns normally when nothing is in flight. */
        void exit() {
            if (guest != null) {
                throw guest;
            }
            if (control != null) {
                throw control;
            }
        }
    }

    /** True when {@code value} is an instance of a class the handler catches. Matching is nominal over
     * exception class names (docs/LANGUAGE_SPEC.md error-handling phases): this also matches built-in
     * exception bases, whose subclasses have no runtime superclass wired into the chain. The set lookup
     * is a cold exceptional path and is kept behind a boundary so its JDK collection call is never
     * runtime-compiled (native-image blocklists {@code HashMap.containsKey} for compiled code). */
    @TruffleBoundary
    private static boolean isMatched(Object value, Set<String> matchedTypes) {
        if (value instanceof SolvikAny receiver) {
            return matchedTypes.contains(receiver.solvikClass().name());
        }
        // A non-object operand never reaches here: semantic analysis rejects a throw whose
        // operand is not assignable to Exception, so any other value is an internal invariant.
        CompilerDirectives.shouldNotReachHere();
        return false;
    }
}
