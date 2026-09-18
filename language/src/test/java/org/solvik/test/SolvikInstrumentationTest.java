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

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

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

    private static Source fileSource(Path file) {
        try {
            return Source.newBuilder("solvik", file.toFile()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
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
            assertThat(instrument).as("the Solvik test instrument must be registered").isNotNull();
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
        assertThat(loaded.stream().anyMatch(entry -> entry.startsWith("instrumented.sol:"))).as("instrumentation must observe the loaded Solvik source").isTrue();
        assertThat(executed.contains("instrumented.sol")).as("instrumentation must observe the executed Solvik source").isTrue();
    }

    @Test
    public void instrumentObservesIncludedSources() throws IOException {
        Path directory = Files.createTempDirectory("solvik-instrument-include");
        try {
            Files.writeString(directory.resolve("lib.sol"), "func helper(): Int {\n    return 1\n}\n", StandardCharsets.UTF_8);
            Path root = directory.resolve("root.sol");
            Files.writeString(root, "include \"lib.sol\"\nprintln(helper())\n", StandardCharsets.UTF_8);
            List<String> loaded = Collections.synchronizedList(new ArrayList<>());
            try (Engine engine = Engine.create()) {
                SolvikTestInstrument instrument = engine.getInstruments().get(SolvikTestInstrument.ID).lookup(SolvikTestInstrument.class);
                try (Context context = Context.newBuilder("solvik").engine(engine).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).allowAllAccess(true).build()) {
                    EventBinding<LoadSourceListener> loadBinding = instrument.env().getInstrumenter().attachLoadSourceListener(SourceFilter.ANY,
                                    new LoadSourceListener() {
                                        @Override
                                        public void onLoad(LoadSourceEvent event) {
                                            loaded.add(event.getSource().getName());
                                        }
                                    }, false);
                    try {
                        context.eval(fileSource(root));
                    } finally {
                        loadBinding.dispose();
                    }
                }
            }
            assertThat(loaded.stream().anyMatch(name -> name.contains("root.sol"))).as("instrumentation must observe the root source").isTrue();
            assertThat(loaded.stream().anyMatch(name -> name.contains("lib.sol"))).as("instrumentation must observe the included source").isTrue();
        } finally {
            deleteRecursively(directory);
        }
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
        assertThat(executed.size()).isEqualTo(1);
        assertThat(executed.get(0)).isEqualTo(program);
    }
}
