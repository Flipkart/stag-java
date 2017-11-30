/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 Vimeo
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vimeo.stag.processor.generators;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.vimeo.stag.processor.generators.model.ClassInfo;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

public class StagGenerator {

    @NotNull
    private static final String CLASS_STAG = "Stag";
    @NotNull
    private static final String CLASS_TYPE_ADAPTER_FACTORY = "Factory";

    @NotNull
    private final Map<String, ClassInfo> mKnownClasses;
    @NotNull
    private List<String> generatedStagFactoryWrappers = new ArrayList<>();

    public StagGenerator(@NotNull Set<TypeMirror> knownTypes) {
        mKnownClasses = new HashMap<>(knownTypes.size());

        for (TypeMirror knownType : knownTypes) {
            if (!TypeUtils.isAbstract(knownType)) {
                ClassInfo classInfo = new ClassInfo(knownType);
                mKnownClasses.put(knownType.toString(), classInfo);
            }
        }
    }

    public static String getGeneratedFactoryClassAndPackage(String generatedPackageName) {
        return generatedPackageName + "." + CLASS_STAG + "." + CLASS_TYPE_ADAPTER_FACTORY;
    }

    public void setGeneratedStagFactoryWrappers(@NotNull List<String> generatedStagFactoryWrappers) {
        this.generatedStagFactoryWrappers = generatedStagFactoryWrappers;
    }

    @Nullable
    ClassInfo getKnownClass(@NotNull TypeMirror typeMirror) {
        return mKnownClasses.get(typeMirror.toString());
    }

    /**
     * Generates the public API in the form of the {@code Stag.Factory} type adapter factory
     * for the annotated classes. Creates the spec for the class.
     *
     * @return A non null TypeSpec for the factory class.
     */
    @NotNull
    public TypeSpec createStagSpec() {
        TypeSpec.Builder stagBuilder =
                TypeSpec.classBuilder(CLASS_STAG).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        stagBuilder.addType(getAdapterFactorySpec());

        return stagBuilder.build();
    }

    @NotNull
    private TypeSpec getAdapterFactorySpec() {
        TypeVariableName genericTypeName = TypeVariableName.get("T");
        TypeVariableName wildcardTypeName = TypeVariableName.get("?");

        TypeSpec.Builder adapterFactoryBuilder = TypeSpec.classBuilder(CLASS_TYPE_ADAPTER_FACTORY)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addSuperinterface(TypeAdapterFactory.class);

        TypeName callsTypeName = ParameterizedTypeName.get(ClassName.get(ThreadLocal.class),
                ParameterizedTypeName.get(ClassName.get(Map.class), ParameterizedTypeName.get(ClassName.get(TypeToken.class), wildcardTypeName), TypeVariableName.get("FutureTypeAdapter<?>")));

        TypeName typeTokenCacheTypeName = ParameterizedTypeName.get(ClassName.get(ConcurrentHashMap.class), ParameterizedTypeName.get(ClassName.get(TypeToken.class), wildcardTypeName),
                ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), wildcardTypeName));

        FieldSpec.Builder callsFieldBuilder = FieldSpec.builder(callsTypeName, "calls").
                addModifiers(Modifier.PRIVATE, Modifier.FINAL).initializer("new " + callsTypeName.toString() + "()");

        FieldSpec.Builder typeTokenCacheFieldBuilder = FieldSpec.builder(typeTokenCacheTypeName, "typeTokenCache").
                addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC).initializer("new " + typeTokenCacheTypeName.toString() + "(100)");

        MethodSpec.Builder getAdapterMethodBuilder = MethodSpec.methodBuilder("getAdapter")
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(genericTypeName)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "\"unchecked\"")
                        .addMember("value", "\"rawtypes\"")
                        .build())
                .returns(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), genericTypeName))
                .addParameter(Gson.class, "gson")
                .addParameter(ParameterizedTypeName.get(ClassName.get(TypeToken.class), genericTypeName), "typeToken");

        getAdapterMethodBuilder.addStatement("TypeAdapter<?> cached = typeTokenCache.get(typeToken)");
        getAdapterMethodBuilder.beginControlFlow("if (cached != null)");
        getAdapterMethodBuilder.addStatement("return (TypeAdapter) cached");
        getAdapterMethodBuilder.endControlFlow();
        getAdapterMethodBuilder.addStatement("Map<TypeToken<?>, FutureTypeAdapter<?>> threadCalls = calls.get()");
        getAdapterMethodBuilder.addStatement("boolean requiresThreadLocalCleanup = false");
        getAdapterMethodBuilder.beginControlFlow("if (threadCalls == null)");
        getAdapterMethodBuilder.addStatement("threadCalls = new " + ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeVariableName.get("TypeToken<?>"), TypeVariableName.get("FutureTypeAdapter<?>")) + "()");
        getAdapterMethodBuilder.addStatement("calls.set(threadCalls)");
        getAdapterMethodBuilder.addStatement("requiresThreadLocalCleanup = true");
        getAdapterMethodBuilder.endControlFlow();
        getAdapterMethodBuilder.addStatement("FutureTypeAdapter ongoingCall = (FutureTypeAdapter) threadCalls.get(typeToken)");
        getAdapterMethodBuilder.beginControlFlow("if (ongoingCall != null)");
        getAdapterMethodBuilder.addStatement("return ongoingCall");
        getAdapterMethodBuilder.endControlFlow();
        getAdapterMethodBuilder.beginControlFlow("try");
        getAdapterMethodBuilder.addCode("FutureTypeAdapter call = new FutureTypeAdapter<T>();\n" +
                "threadCalls.put(typeToken, call);\n" +
                "\n" +
                "TypeAdapter typeAdapter = this.create(gson, typeToken);\n" +
                "if (typeAdapter == null) {\n" +
                "   typeAdapter = gson.getAdapter(typeToken);\n" +
                "}\n" +
                "\n" +
                "if (typeAdapter != null) {\n" +
                "   typeTokenCache.put(typeToken, typeAdapter);\n" +
                "   return typeAdapter;\n" +
                "}\n" +
                "\n" +
                "throw new IllegalArgumentException(\"GSON cannot handle \" + typeToken);\n");
        getAdapterMethodBuilder.endControlFlow();
        getAdapterMethodBuilder.beginControlFlow("finally");
        getAdapterMethodBuilder.addCode("threadCalls.remove(typeToken);\n" +
                "\n" +
                "if (requiresThreadLocalCleanup) {\n" +
                "   calls.remove();\n" +
                "}\n");
        getAdapterMethodBuilder.endControlFlow();

        MethodSpec.Builder createMethodBuilder = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "\"unchecked\"")
                        .addMember("value", "\"rawtypes\"")
                        .build())
                .addTypeVariable(genericTypeName)
                .returns(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), genericTypeName))
                .addParameter(Gson.class, "gson")
                .addParameter(ParameterizedTypeName.get(ClassName.get(TypeToken.class), genericTypeName),
                        "type");

        createMethodBuilder.addCode("TypeAdapter typeAdapter = typeTokenCache.get(type);\n" +
                "if (typeAdapter != null) {\n" +
                "   return typeAdapter;\n" +
                "}\n\n");
        createMethodBuilder.addStatement("TypeAdapter<T> result = null");

        int count = 1;
        for (String stagFileName : generatedStagFactoryWrappers) {
            String fieldName = "adapter" + count;
            createMethodBuilder.addStatement("TypeAdapter<T> " + fieldName + " = " + stagFileName + ".getAdapter(gson, type, this)");
            createMethodBuilder.beginControlFlow("if (" + fieldName + " != null)");
            createMethodBuilder.addStatement("result = " + fieldName);
            createMethodBuilder.endControlFlow();
            count++;
        }

        createMethodBuilder.addCode("\nif (result != null) {\n" +
                "   typeTokenCache.put(type, result);\n" +
                "}\n\n");
        createMethodBuilder.addStatement("return result");

        adapterFactoryBuilder.addMethod(createMethodBuilder.build());
        adapterFactoryBuilder.addMethod(getAdapterMethodBuilder.build());
        adapterFactoryBuilder.addField(callsFieldBuilder.build());
        adapterFactoryBuilder.addField(typeTokenCacheFieldBuilder.build());
        adapterFactoryBuilder.addType(createFutureTypeAdapter());

        return adapterFactoryBuilder.build();
    }

    @NotNull
    private TypeSpec createFutureTypeAdapter() {
        TypeVariableName genericTypeName = TypeVariableName.get("T");

        TypeSpec.Builder futureTypeAdapterBuilder = TypeSpec.classBuilder("FutureTypeAdapter")
                .addTypeVariable(genericTypeName)
                .addModifiers(Modifier.STATIC)
                .superclass(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), genericTypeName));


        TypeName parameterizedTypeName = ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), genericTypeName);
        FieldSpec.Builder delegateFieldBuilder = FieldSpec.builder(parameterizedTypeName, "delegate")
                .addModifiers(Modifier.PRIVATE);

        MethodSpec.Builder setDelegateMethodBuilder = MethodSpec.methodBuilder("setDelegate")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parameterizedTypeName, "typeAdapter")
                .beginControlFlow("if (delegate != null)")
                .addStatement("throw new AssertionError()")
                .endControlFlow()
                .addStatement("delegate = typeAdapter");

        MethodSpec.Builder readMethodBuilder = MethodSpec.methodBuilder("read")
                .addModifiers(Modifier.PUBLIC)
                .returns(genericTypeName)
                .addAnnotation(Override.class)
                .addParameter(JsonReader.class, "in")
                .addException(IOException.class)
                .beginControlFlow("if (delegate == null)")
                .addStatement("throw new IllegalStateException()")
                .endControlFlow()
                .addStatement("return delegate.read(in)");

        MethodSpec.Builder writeMethodBuilder = MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(JsonWriter.class, "out")
                .addParameter(genericTypeName, "value")
                .addException(IOException.class)
                .beginControlFlow("if (delegate == null)")
                .addStatement("throw new IllegalStateException()")
                .endControlFlow()
                .addStatement("delegate.write(out, value)");

        futureTypeAdapterBuilder.addField(delegateFieldBuilder.build());
        futureTypeAdapterBuilder.addMethod(setDelegateMethodBuilder.build());
        futureTypeAdapterBuilder.addMethod(readMethodBuilder.build());
        futureTypeAdapterBuilder.addMethod(writeMethodBuilder.build());

        return futureTypeAdapterBuilder.build();
    }
}