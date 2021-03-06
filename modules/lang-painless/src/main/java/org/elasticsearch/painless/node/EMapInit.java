/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.Scope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.MapInitializationNode;
import org.elasticsearch.painless.lookup.PainlessConstructor;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.painless.lookup.def;
import org.elasticsearch.painless.symbol.ScriptRoot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.painless.lookup.PainlessLookupUtility.typeToCanonicalTypeName;

/**
 * Represents a map initialization shortcut.
 */
public class EMapInit extends AExpression {

    protected final List<AExpression> keys;
    protected final List<AExpression> values;

    public EMapInit(Location location, List<AExpression> keys, List<AExpression> values) {
        super(location);

        this.keys = Collections.unmodifiableList(Objects.requireNonNull(keys));
        this.values = Collections.unmodifiableList(Objects.requireNonNull(values));
    }

    @Override
    Output analyze(ClassNode classNode, ScriptRoot scriptRoot, Scope scope, Input input) {
        Output output = new Output();

        if (input.read == false) {
            throw createError(new IllegalArgumentException("Must read from map initializer."));
        }

        output.actual = HashMap.class;

        PainlessConstructor constructor = scriptRoot.getPainlessLookup().lookupPainlessConstructor(output.actual, 0);

        if (constructor == null) {
            throw createError(new IllegalArgumentException(
                    "constructor [" + typeToCanonicalTypeName(output.actual) + ", <init>/0] not found"));
        }

        PainlessMethod method = scriptRoot.getPainlessLookup().lookupPainlessMethod(output.actual, false, "put", 2);

        if (method == null) {
            throw createError(new IllegalArgumentException("method [" + typeToCanonicalTypeName(output.actual) + ", put/2] not found"));
        }

        if (keys.size() != values.size()) {
            throw createError(new IllegalStateException("Illegal tree structure."));
        }

        List<Output> keyOutputs = new ArrayList<>(keys.size());
        List<Output> valueOutputs = new ArrayList<>(values.size());

        for (int i = 0; i < keys.size(); ++i) {
            AExpression expression = keys.get(i);
            Input expressionInput = new Input();
            expressionInput.expected = def.class;
            expressionInput.internal = true;
            Output expressionOutput = expression.analyze(classNode, scriptRoot, scope, expressionInput);
            expression.cast(expressionInput, expressionOutput);
            keyOutputs.add(expressionOutput);

            expression = values.get(i);
            expressionInput = new Input();
            expressionInput.expected = def.class;
            expressionInput.internal = true;
            expressionOutput = expression.analyze(classNode, scriptRoot, scope, expressionInput);
            expression.cast(expressionInput, expressionOutput);
            valueOutputs.add(expressionOutput);
        }

        MapInitializationNode mapInitializationNode = new MapInitializationNode();

        for (int i = 0; i < keys.size(); ++i) {
            mapInitializationNode.addArgumentNode(
                    keys.get(i).cast(keyOutputs.get(i)),
                    values.get(i).cast(valueOutputs.get(i)));
        }

        mapInitializationNode.setLocation(location);
        mapInitializationNode.setExpressionType(output.actual);
        mapInitializationNode.setConstructor(constructor);
        mapInitializationNode.setMethod(method);

        output.expressionNode = mapInitializationNode;

        return output;
    }

    @Override
    public String toString() {
        return singleLineToString(pairwiseToString(keys, values));
    }
}
