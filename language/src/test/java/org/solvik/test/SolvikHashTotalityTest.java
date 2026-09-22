package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * Guards the assumption that makes {@code SolvikHash} and {@code SolvikValues} total: every value
 * reaching them was produced by Solvik itself, so its runtime class is one of the kinds the two
 * services dispatch on, and the terminal {@code AssertionError} branches are genuinely unreachable.
 *
 * <p>Both services end in {@code throw new AssertionError("unknown ... runtime kind")}. That is sound
 * only if no value kind exists that the dispatch does not name. The one way such a kind could appear
 * is a foreign host object flowing in through interop, which the language launcher enables with
 * {@code allowAllAccess(true)}. This test pins that all interop entry points are closed, so the
 * terminal branches cannot be reached from a host that has full access enabled.
 *
 * <p>If a future change opens any of these paths -- exporting guest callables, accepting host
 * bindings, or a host-object import -- this test fails and {@code SolvikHash} must grow an interop
 * case before it can be considered total again. That failure is the point: the crash it prevents is a
 * wrong answer in {@code Set.contains}, not a visible error.
 */
public class SolvikHashTotalityTest {

    @Test
    public void guestCodeExportsNothingForTheHostToSendBackIn() {
        // If the host could not obtain a guest callable, it cannot call one with a foreign argument.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = hostAccessContext(out)) {
            Value result = eval(context, """
                    func use(x: Any): Integer {
                        return x.hashCode();
                    }
                    """);
            assertThat(result.canExecute()).as("a top-level eval result must not be callable").isFalse();
            assertThat(result.getMemberKeys()).as("no guest member is exported from a unit").isEmpty();
        }
    }

    @Test
    public void theHostCannotInjectValuesIntoGuestScope() {
        // Two directions are closed at once: the host cannot write a binding, and even a name that
        // somehow appeared would not resolve, because guest name resolution only knows guest symbols.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = hostAccessContext(out)) {
            Value bindings = context.getBindings("solvik");
            assertThat(bindings.getMemberKeys()).as("guest bindings start empty").isEmpty();
            assertThatThrownBy(() -> bindings.putMember("hostList", new ArrayList<>(List.of("x"))))
                            .as("the host cannot place a foreign object into guest scope")
                            .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    public void aNameTheHostTriedToPublishIsAnUnknownGuestName() {
        // The failure must be a static resolution error rather than a foreign value reaching the guest
        // type model, which is what would let an unhandled runtime class reach the hash service.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = hostAccessContext(out)) {
            assertThatThrownBy(() -> eval(context, """
                    var viaBinding: Any = hostList;
                    println(viaBinding.hashCode());
                    """)).isInstanceOf(PolyglotException.class)
                            .hasMessageContaining("SOLV-RESOL-001")
                            .hasMessageContaining("unknown name 'hostList'");
        }
    }

    @Test
    public void aHashOverrideCannotBeReachedWithAForeignReceiver() {
        // A guest `equals`/`hashCode` pair is only ever invoked with values the language created. With
        // no entry point for foreign objects, a user override cannot observe a host type it must decide
        // equality or hashing for.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = hostAccessContext(out)) {
            assertThat(eval(context, """
                    class Key {
                        val id: Integer

                        Key(id: Integer) {
                            this.id = id
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Key) {
                                return this.id == other.id
                            }
                            return false
                        }

                        override func hashCode(): Integer {
                            return this.id
                        }
                    }

                    var keys: Set<Key> = Set();
                    keys.add(Key(1));
                    println(keys.contains(Key(1)));
                    println(keys.contains(Key(2)));
                    """).isNull()).isTrue();
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("true\nfalse\n");
        }
    }

    private static Context hostAccessContext(ByteArrayOutputStream out) {
        // Mirrors the launcher's own configuration, which is the strongest host that can exist.
        return Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build();
    }

    private static Value eval(Context context, String source) {
        try {
            return context.eval(Source.newBuilder("solvik", source, "totality.sol").build());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
