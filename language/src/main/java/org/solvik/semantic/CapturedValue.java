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
package org.solvik.semantic;

import java.util.Objects;
import org.solvik.ast.expression.CaptureItem;
import org.solvik.source.SourceSpan;
import org.solvik.type.Type;

/**
 * One capture item resolved at the closure-creation site (docs/LANGUAGE_SPEC.md section 6, "Explicit
 * immutable closure capture"). {@link #source()} is the binding whose value the creation site reads,
 * {@link #name()} is the name the body uses to read that value, and {@link #captureSpan()} is the written
 * item, so a diagnostic that must point at the item rather than at the body still can.
 *
 * <p>Every {@link #source()} of an identifier item is an immutable local or an immutable parameter of an
 * enclosing function, including the function-valued binding an intervening closure lists to forward a
 * value it does not own. A {@code var} never appears here: naming one is rejected, and the rejected name
 * is recorded so the body reports the mutable-capture code for it instead of reading a silent mirror.
 *
 * <p>A {@code [this]} item carries a synthetic source symbol that is <em>declared in no scope</em>. It
 * exists so the creation site's receiver read and the body's {@code this} share one key — lowering gives
 * the binding a capture slot in the closure's frame, and the body's {@code this} reads that slot — without
 * the name {@code this} becoming resolvable as a variable. Keeping it out of the body's scope is what
 * makes {@code this} mean the captured receiver and nothing else.
 *
 * <p>Like {@link org.solvik.ast.expression.CaptureItem}, whether this is a receiver capture is recovered
 * from the name, so a value cannot claim to be a {@code this} capture while naming something else.
 */
public record CapturedValue(String name, Type type, VariableSymbol source, SourceSpan captureSpan) {

    public CapturedValue {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        Objects.requireNonNull(source);
        Objects.requireNonNull(captureSpan);
        // The name and type are read off the binding rather than carried beside it, so an item cannot
        // claim a value under a spelling or a type that the binding it names does not have. Resolution
        // finds the binding by looking the item's own name up in a scope, so this holds of every value
        // the analysis can produce; stating it here is what stops a future factory from breaking it.
        if (!source.name().equals(name)) {
            throw new IllegalArgumentException("capture item '" + name + "' does not name binding '" + source.name() + "'");
        }
        if (type != source.type()) {
            throw new IllegalArgumentException("capture item '" + name + "' has type " + type.name() + " but names a binding of type " + source.type().name());
        }
    }

    /**
     * The value an accepted identifier capture item binds in the body. Both the name and the type come
     * from {@code binding}, because {@code item} reached it by spelling exactly that name and the body
     * must see the value as the type the binding has.
     */
    static CapturedValue ofBinding(CaptureItem item, VariableSymbol binding) {
        return new CapturedValue(item.name(), binding.type(), binding, item.span());
    }

    /**
     * The value a written {@code [this]} item binds: the receiver the creation site reads, recorded under a
     * synthetic binding no scope contains.
     *
     * @param type       the enclosing receiver's type, which only the caller can know since no binding of
     *                   {@code this} exists to read it from
     * @param captureSpan the written {@code [this]} item
     */
    static CapturedValue ofReceiver(Type type, SourceSpan captureSpan) {
        // Declared immutable because the body may not reassign what `this` means, and marked initialized
        // because a receiver exists from the moment the enclosing callable is entered. Both name and type
        // are given to the synthetic binding, which is what lets the invariants above hold of a receiver
        // capture exactly as they do of a named one.
        VariableSymbol receiver = new VariableSymbol(CaptureItem.THIS_LEXEME, captureSpan, type, false, false);
        receiver.markInitialized();
        return new CapturedValue(CaptureItem.THIS_LEXEME, type, receiver, captureSpan);
    }

    /** Whether this item is the captured enclosing receiver rather than a named binding. */
    public boolean isThis() {
        return name.equals(CaptureItem.THIS_LEXEME);
    }
}
