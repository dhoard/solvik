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
package org.solvik.ast.declaration;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/**
 * An interface abstract signature {@code func name(param: Type, ...): ReturnType;} terminated by
 * {@code ;} (docs/LANGUAGE_SPEC.md section 8). It declares a required member with no implementation:
 * every conforming class either implements it or inherits a default for it, otherwise static analysis
 * reports a missing implementation.
 */
public final class SignatureDeclNode extends CallableDeclNode {

    public SignatureDeclNode(String name, List<ParameterNode> parameters, TypeRefNode returnType, SourceSpan span) {
        this(name, List.of(), parameters, returnType, span);
    }

    public SignatureDeclNode(String name, List<TypeParameterNode> typeParameters, List<ParameterNode> parameters, TypeRefNode returnType, SourceSpan span) {
        super(AstKind.SIGNATURE_DECL, name, typeParameters, parameters, returnType, span);
    }

    @Override
    public boolean hasBody() {
        return false;
    }
}
