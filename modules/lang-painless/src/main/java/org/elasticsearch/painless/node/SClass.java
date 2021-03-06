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
import org.elasticsearch.painless.ScriptClassInfo;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.symbol.FunctionTable;
import org.elasticsearch.painless.symbol.ScriptRoot;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;

/**
 * The root of all Painless trees.  Contains a series of statements.
 */
public class SClass extends ANode {

    protected final ScriptClassInfo scriptClassInfo;
    protected final String name;
    protected final String sourceText;
    protected final Printer debugStream;
    protected final List<SFunction> functions = new ArrayList<>();

    public SClass(ScriptClassInfo scriptClassInfo, String name, String sourceText, Printer debugStream,
            Location location, List<SFunction> functions) {
        super(location);
        this.scriptClassInfo = Objects.requireNonNull(scriptClassInfo);
        this.name = Objects.requireNonNull(name);
        this.sourceText = Objects.requireNonNull(sourceText);
        this.debugStream = debugStream;
        this.functions.addAll(Objects.requireNonNull(functions));
    }

    public ClassNode writeClass(ScriptRoot scriptRoot) {
        scriptRoot.addStaticConstant("$NAME", name);
        scriptRoot.addStaticConstant("$SOURCE", sourceText);

        for (SFunction function : functions) {
            function.generateSignature(scriptRoot.getPainlessLookup());

            String key = FunctionTable.buildLocalFunctionKey(function.name, function.typeParameters.size());

            if (scriptRoot.getFunctionTable().getFunction(key) != null) {
                throw createError(new IllegalArgumentException("Illegal duplicate functions [" + key + "]."));
            }

            scriptRoot.getFunctionTable().addFunction(
                    function.name, function.returnType, function.typeParameters, function.isInternal, function.isStatic);
        }

        ClassNode classNode = new ClassNode();

        for (SFunction function : functions) {
            classNode.addFunctionNode(function.writeFunction(classNode, scriptRoot));
        }

        classNode.setLocation(location);
        classNode.setScriptClassInfo(scriptClassInfo);
        classNode.setScriptRoot(scriptRoot);
        classNode.setDebugStream(debugStream);
        classNode.setName(name);
        classNode.setSourceText(sourceText);

        return classNode;
    }

    @Override
    public String toString() {
        List<Object> subs = new ArrayList<>(functions.size());
        subs.addAll(functions);
        return multilineToString(emptyList(), subs);
    }
}
