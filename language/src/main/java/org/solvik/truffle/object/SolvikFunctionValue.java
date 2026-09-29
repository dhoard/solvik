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
package org.solvik.truffle.object;

import java.util.Objects;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.solvik.truffle.SolvikFunction;

/**
 * A Solvik function value (docs/LANGUAGE_SPEC.md section 6, "Function values"): the guest-visible
 * value of a callable. Every kind of function value shares this one runtime shape, and the shape is
 * closed, because the specification fixes equality, hashing, and display for the whole category and
 * gives a function value no members that could observe it further.
 *
 * <p>The three kinds differ only in what {@link #target} is and what {@link #receiver} holds:
 * <ul>
 * <li>a reference to a declared top-level function carries its lowering's runtime handle and no
 * receiver, and the value is canonical per declaration (see below);</li>
 * <li>a bound method reference carries the implementation the receiver's runtime class selects and
 * that receiver, captured once when the reference is created;</li>
 * <li>an anonymous function will carry its own lowered target, which needs no receiver because
 * capture is materialized as additional parameters rather than as state on the value.</li>
 * </ul>
 *
 * <p>Identity is reference identity and display is the fixed string {@code func}: neither may expose
 * a Java class name, an address, a module path, or a captured value. The {@link #target} is therefore
 * never rendered, and the {@code toString} exported here is the only rendering rule.
 *
 * <h2>Why a named function value is canonical</h2>
 *
 * A reference to a declared function is a name, not an allocation, so evaluating it twice must yield
 * one value: {@code format === format} holds, and module qualification does not create a second
 * identity for the same declaration. That is decided by construction rather than by comparison — the
 * lowering installs one shared value per declared function, so the identity is not a property any
 * operation has to maintain. A bound method reference is the opposite case: each creation is
 * distinct, so the expression that creates one allocates a new value.
 *
 * <h2>Why no type arguments are stored</h2>
 *
 * A function value is monomorphic. Instantiating a generic declaration at a function type is a static
 * event that produces no distinct runtime value, so a value carries no type-argument list and
 * explicit type arguments on a call through one are rejected by static analysis.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("serial")
public final class SolvikFunctionValue implements TruffleObject {

    /** The fixed rendering of every function value (docs/LANGUAGE_SPEC.md section 6). */
    public static final String DISPLAY = "func";

    /**
     * The executable this value invokes, for a value that names a lowered target directly. Null for a
     * value created from a declared function, which resolves through {@link #declared} instead.
     */
    private final CallTarget target;

    /**
     * The declaration this value names, or {@code null} for a value created from a bare call target.
     *
     * <p>A declared function's call target is installed only after its body has been lowered, and bodies
     * lower in an order driven by declarations rather than by references, so a reference to a function
     * can be lowered before the target it will eventually invoke exists. Holding the handle and reading
     * its target on demand is what makes such a reference legal; capturing the target at creation would
     * either fail here or force a second, order-dependent pass over the program.
     */
    private final SolvikFunction declared;

    /** The receiver captured by a bound method reference, or {@code null} for a value that takes no receiver. */
    private final Object receiver;

    /** A stable name for diagnostics and the native-image resource bundle; never guest-visible. */
    private final String debugName;

    private SolvikFunctionValue(CallTarget target, SolvikFunction declared, Object receiver, String debugName) {
        this.target = target;
        this.declared = declared;
        this.receiver = receiver;
        this.debugName = Objects.requireNonNull(debugName);
    }

    /**
     * Creates the value for a declared top-level function. The caller holds one instance per declaration,
     * so the canonical identity follows from how often this is called, not from a cache.
     */
    public static SolvikFunctionValue forFunction(SolvikFunction function) {
        return new SolvikFunctionValue(null, Objects.requireNonNull(function), null, function.name());
    }

    /** Creates a distinct value binding {@code method} to {@code receiver} (docs/LANGUAGE_SPEC.md section 6). */
    public static SolvikFunctionValue bound(CallTarget method, Object receiver, String debugName) {
        return new SolvikFunctionValue(method, null, receiver, debugName);
    }

    /** Creates a value for a lowered target that is not a declared function, such as an anonymous function body. */
    public static SolvikFunctionValue forTarget(CallTarget target, String debugName) {
        return new SolvikFunctionValue(target, null, null, debugName);
    }

    /**
     * The executable to invoke. For a value naming a declaration this is the declaration's installed
     * target, which every caller reaches only during execution, by which point lowering has installed a
     * target for every declared function.
     */
    public CallTarget target() {
        return declared != null ? declared.callTarget() : target;
    }

    /** The captured receiver, or {@code null} when this value takes no receiver. */
    public Object receiver() {
        return receiver;
    }

    /**
     * Whether this value carries a captured receiver. Only a bound method value does, and its
     * receiver is never null: a safe reference through a null receiver yields a null function value
     * rather than a bound one (docs/LANGUAGE_SPEC.md section 6).
     */
    public boolean hasReceiver() {
        return receiver != null;
    }

    /**
     * The argument array to hand to {@link #target}: the guest arguments, preceded by the captured
     * receiver when there is one. The implicit receiver of a method is not a guest argument, so it
     * appears nowhere in the source-shaped argument list; supplying it here is what lets a bound value
     * invoke the same target an immediate call would (docs/LANGUAGE_SPEC.md section 7).
     */
    public static Object[] withReceiver(SolvikFunctionValue function, Object[] arguments) {
        if (!function.hasReceiver()) {
            return arguments;
        }
        Object[] call = new Object[arguments.length + 1];
        call[0] = function.receiver();
        System.arraycopy(arguments, 0, call, 1, arguments.length);
        return call;
    }

    /**
     * Semantic equality for function values is reference identity, and the hash is the matching
     * identity hash (docs/LANGUAGE_SPEC.md section 3). Both are fixed and cannot be overridden, so
     * this path reaches no guest code.
     */
    public boolean valueEquals(SolvikFunctionValue other) {
        return this == other;
    }

    /** The reference-identity hash paired with {@link #valueEquals}. */
    public int valueHash() {
        return System.identityHashCode(this);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return DISPLAY;
    }

    /** A name for engine tooling and assertion messages; guest code never sees it. */
    public String debugName() {
        return debugName;
    }

    /**
     * Whether a host may execute this value. Only a non-null function value reaches a host at all —
     * a null is a null — so every value of this type reports itself executable
     * (docs/LANGUAGE_SPEC.md section 6, "Type tests, casts, and other constructs").
     */
    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    /**
     * Executes this value for a host. Arity is an internal runtime invariant here and not a guest
     * observable: semantic analysis has already rejected a bad arity in Solvik source, so a host that
     * supplies the wrong number of arguments is misusing the value rather than violating a documented
     * contract. A bound value invokes with its captured receiver prepended, so the host sees the same
     * executable a guest call reaches.
     */
    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("unused")
    public static Object execute(SolvikFunctionValue value, Object... arguments) {
        return value.target.call(withReceiver(value, arguments));
    }

    /** The rendering a host sees is the guest rendering, so a function value never leaks its shape. */
    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("unused")
    Object toDisplayString(boolean allowSideEffects) {
        return DISPLAY;
    }
}
