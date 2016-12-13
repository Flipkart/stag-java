package com.vimeo.stag.processor.generators;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.vimeo.stag.processor.generators.model.ClassInfo;
import com.vimeo.stag.processor.utils.FileGenUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

public class EnumTypeAdapterGenerator extends AdapterGenerator {

    @NotNull
    private final ClassInfo mInfo;

    @NotNull
    private final Element mElement;


    public EnumTypeAdapterGenerator(@NotNull ClassInfo info, @NotNull Element element) {
        mInfo = info;
        mElement = element;
    }

    /**
     * Generates the TypeSpec for the TypeAdapter
     * that this enum generates.
     *
     * @return a valid TypeSpec that can be written
     * to a file or added to another class.
     */
    @Override
    @NotNull
    public TypeSpec getTypeAdapterSpec(@NotNull TypeTokenConstantsGenerator typeTokenConstantsGenerator,
                                       @NotNull StagGenerator stagGenerator) {
        TypeMirror typeMirror = mInfo.getType();
        TypeName typeVariableName = TypeVariableName.get(typeMirror);

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Gson.class, "gson")
                .addParameter(stagGenerator.getGeneratedClassName(), "stagFactory");

        String className = FileGenUtils.unescapeEscapedString(mInfo.getTypeAdapterClassName());
        TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), typeVariableName));


        Map<String, Element> nameToConstant = new HashMap<>();
        Map<Element, String> constantToName = new HashMap<>();

        for(Element enclosingElement : mElement.getEnclosedElements()) {
            if(enclosingElement.getKind() == ElementKind.ENUM_CONSTANT) {
                String name = getJsonName(enclosingElement);
                nameToConstant.put(name, enclosingElement);
                constantToName.put(enclosingElement, name);
            }
        }

        MethodSpec writeMethod = getWriteMethodSpec(typeVariableName);
        MethodSpec readMethod = getReadMethodSpec(typeVariableName);

        TypeName typeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeVariableName.get(String.class), TypeVariableName.get(typeMirror));
        adapterBuilder.addField(typeName, "NAME_TO_CONSTANT", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        typeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeVariableName.get(typeMirror), TypeVariableName.get(String.class));
        adapterBuilder.addField(typeName, "CONSTANT_TO_NAME", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        CodeBlock.Builder staticBlockBuilder = CodeBlock.builder();
        staticBlockBuilder.addStatement("NAME_TO_CONSTANT = new HashMap<>(" + nameToConstant.size() + ")");

        for(Map.Entry<String, Element> entry : nameToConstant.entrySet()) {
            staticBlockBuilder.addStatement("NAME_TO_CONSTANT.put(\"" + entry.getKey() + "\", " + typeVariableName + "." + entry.getValue().getSimpleName().toString() + ")");
        }

        staticBlockBuilder.add("\n");
        staticBlockBuilder.addStatement("CONSTANT_TO_NAME = new HashMap<>(" + constantToName.size() + ")");
        for(Map.Entry<Element, String> entry : constantToName.entrySet()) {
            staticBlockBuilder.addStatement("CONSTANT_TO_NAME.put(" + typeVariableName + "." + entry.getKey().getSimpleName().toString() + ", \""  + entry.getValue() + "\")");
        }

        adapterBuilder.addStaticBlock(staticBlockBuilder.build());
        adapterBuilder.addMethod(constructorBuilder.build());
        adapterBuilder.addMethod(writeMethod);
        adapterBuilder.addMethod(readMethod);

        return adapterBuilder.build();
    }

    @NotNull
    private MethodSpec getWriteMethodSpec(@NotNull TypeName typeName) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("write")
                .addParameter(JsonWriter.class, "writer")
                .addParameter(typeName, "object")
                .returns(void.class)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addException(IOException.class);

        builder.addStatement("writer.value(object == null ? null : CONSTANT_TO_NAME.get(object))");
        return builder.build();
    }

    @NotNull
    private MethodSpec getReadMethodSpec(@NotNull TypeName typeName) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("read")
                .addParameter(JsonReader.class, "reader")
                .returns(typeName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addException(IOException.class);

        builder.beginControlFlow("if (reader.peek() == com.google.gson.stream.JsonToken.NULL)");
        builder.addStatement("reader.nextNull()");
        builder.addStatement("return null");
        builder.endControlFlow();
        builder.addStatement("return NAME_TO_CONSTANT.get(reader.nextString())");
        return builder.build();
    }
}