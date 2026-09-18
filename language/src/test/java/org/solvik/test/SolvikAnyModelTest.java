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

import com.oracle.truffle.api.object.DynamicObject.PutNode;
import com.oracle.truffle.api.strings.TruffleString;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikAny;

/**
 * Object-model structure tests: the class layout is fixed,
 * instances of one class share a stable shape, and there is no API for inserting undeclared
 * members. The shape-sharing assertions exercise the same allocation path used by
 * {@code SolvikNewNode}.
 */
public final class SolvikAnyModelTest {

    private static SolvikAny newInstance(SolvikClass solvikClass, PutNode putNode) {
        SolvikAny object = new SolvikAny(solvikClass);
        for (int i = 0; i < solvikClass.propertyCount(); i++) {
            putNode.execute(object, solvikClass.propertyKey(i), null);
        }
        return object;
    }

    @Test
    public void instancesOfAClassShareAStableShape() {
        SolvikClass point = new SolvikClass("Point", List.of("x", "y"), List.of(false, false));
        PutNode putNode = PutNode.create();
        SolvikAny a = newInstance(point, putNode);
        SolvikAny b = newInstance(point, putNode);
        assertThat(b.getShape()).as("same class and property order must reuse one shape").isSameAs(a.getShape());
        assertThat(a.getShape().getPropertyCount()).isEqualTo(2);
        assertThat(SolvikClass.rootShape().getPropertyCount()).isEqualTo(0);
    }

    @Test
    public void propertyMetadataIsFixedByTheClass() {
        SolvikClass cell = new SolvikClass("Cell", List.of("value", "label"), List.of(true, false));
        assertThat(cell.name()).isEqualTo("Cell");
        assertThat(cell.propertyCount()).isEqualTo(2);
        assertThat(cell.propertyName(0)).isEqualTo("value");
        assertThat(cell.propertyIndex("value")).isEqualTo(0);
        assertThat(cell.propertyIndex("label")).isEqualTo(1);
        assertThat(cell.isPropertyMutable(0)).isTrue();
        assertThat(cell.isPropertyMutable(1)).isFalse();
        assertThat(cell.propertyKey(1).toJavaStringUncached()).isEqualTo("label");
    }

    @Test
    public void declaredPropertiesExistAfterAllocation() {
        SolvikClass marker = new SolvikClass("Marker", List.of("id"), List.of(false));
        PutNode putNode = PutNode.create();
        SolvikAny object = newInstance(marker, putNode);
        assertThat(object.getShape().getPropertyCount()).isEqualTo(1);
        assertThat(object.getShape().hasProperty(marker.propertyKey(0))).isTrue();
        TruffleString missing = TruffleString.fromJavaStringUncached("missing", TruffleString.Encoding.UTF_8);
        assertThat(object.getShape().hasProperty(missing)).as("no undeclared member is present").isFalse();
    }
}
