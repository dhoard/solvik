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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Test;

/**
 * Debugger/instrumentation validation: a Truffle instrument
 * attached to the engine observes the Solvik sources as they are loaded and executed, and every
 * observed source carries the expected name and characters. This proves the Truffle tooling stack
 * can see Solvik compilation units and execution without a SimpleLanguage-specific hook.
 */
public final class SolvikInstrumentationTest {

    private static Source source(String text) {
        try {
            return Source.newBuilder("solvik", text, "instrumented.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void instrumentObservesLoadedAndExecutedSolvikSources() {
        String program = """
                func double(value: Int): Int {
                    return value * 2
                }

                    println(double(21))
                """;
        List<String> loaded = Collections.synchronizedList(new ArrayList<>());
        List<String> executed = Collections.synchronizedList(new ArrayList<>());
        try (Engine engine = Engine.create()) {
            SolvikTestInstrument instrument = engine.getInstruments().get(SolvikTestInstrument.ID).lookup(SolvikTestInstrument.class);
            assertNotNull("the Solvik test instrument must be registered", instrument);
            try (Context context = Context.newBuilder("solvik").engine(engine).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).allowAllAccess(true).build()) {
                EventBinding<LoadSourceListener> loadBinding = instrument.env().getInstrumenter().attachLoadSourceListener(SourceFilter.ANY,
                                new LoadSourceListener() {
                                    @Override
                                    public void onLoad(LoadSourceEvent event) {
                                        loaded.add(event.getSource().getName() + ":" + event.getSource().getCharacters().length());
                                    }
                                }, false);
                EventBinding<ExecuteSourceListener> executeBinding = instrument.env().getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY,
                                new ExecuteSourceListener() {
                                    @Override
                                    public void onExecute(ExecuteSourceEvent event) {
                                        executed.add(event.getSource().getName());
                                    }
                                }, false);
                try {
                    context.eval(source(program));
                } finally {
                    loadBinding.dispose();
                    executeBinding.dispose();
                }
            }
        }
        assertTrue("instrumentation must observe the loaded Solvik source", loaded.stream().anyMatch(entry -> entry.startsWith("instrumented.sol:")));
        assertTrue("instrumentation must observe the executed Solvik source", executed.contains("instrumented.sol"));
    }

    @Test
    public void instrumentObservationIsScopedToTheEvaluatedSource() {
        String program = "    println(7)\n";
        List<String> executed = Collections.synchronizedList(new ArrayList<>());
        try (Engine engine = Engine.create()) {
            SolvikTestInstrument instrument = engine.getInstruments().get(SolvikTestInstrument.ID).lookup(SolvikTestInstrument.class);
            try (Context context = Context.newBuilder("solvik").engine(engine).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).allowAllAccess(true).build()) {
                EventBinding<ExecuteSourceListener> binding = instrument.env().getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY,
                                new ExecuteSourceListener() {
                                    @Override
                                    public void onExecute(ExecuteSourceEvent event) {
                                        executed.add(event.getSource().getCharacters().toString());
                                    }
                                }, false);
                try {
                    context.eval(source(program));
                } finally {
                    binding.dispose();
                }
            }
        }
        assertEquals(1, executed.size());
        assertEquals(program, executed.get(0));
    }
}
