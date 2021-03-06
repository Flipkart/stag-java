package com.vimeo.stag.processor.generators.model.accessor;

import com.vimeo.stag.processor.utils.DebugLog;
import com.vimeo.stag.processor.utils.ElementUtils;
import com.vimeo.stag.processor.utils.StringUtils;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A variation of the {@link FieldAccessor}
 * which populates the field by accessing
 * it through getters and setters.
 * <p>
 * Created by restainoa on 5/8/17.
 */
public class MethodFieldAccessor extends FieldAccessor {

    /**
     * Field naming notation,
     * used to determine the
     * names of the accessor methods.
     */
    public enum Notation {
        STANDARD,
        HUNGARIAN
    }

    @NotNull private final String mSetterName;
    @NotNull private final String mGetterName;

    public MethodFieldAccessor(@NotNull final VariableElement element,
                               @NotNull final Notation notation) throws UnsupportedOperationException {
        super(element);

        mSetterName = findSetterMethodName(element, notation);
        mGetterName = findGetterMethodName(element, notation);
    }

    @NotNull
    @Override
    public String createGetterCode() {
        return mGetterName + "()";
    }

    @NotNull
    @Override
    public String createSetterCode(@NotNull final String assignment) {
        return mSetterName + '(' + assignment + ')';
    }

    @NotNull
    private static List<ExecutableElement> getSiblingMethods(@NotNull final VariableElement variableElement) {
        final List<ExecutableElement> methodElements = new ArrayList<>();
        final List<? extends Element> otherElements = variableElement.getEnclosingElement().getEnclosedElements();

        for (final Element element : otherElements) {
            if (element.getKind() == ElementKind.METHOD && element instanceof ExecutableElement) {
                methodElements.add((ExecutableElement) element);
            }
        }

        return methodElements;
    }

    private static boolean isSupportedSetter(@NotNull final ExecutableElement method,
                                             @NotNull final VariableElement variableElement,
                                             @NotNull final Notation namingNotation) {

        final String variableNameMethodComponent = getVariableNameAsMethodName(variableElement, namingNotation);
        final String methodName = method.getSimpleName().toString();

        return (variableNameBeginsWithIs(variableNameMethodComponent)
                && methodName.equals("set" + variableNameMethodComponent.substring(2)))
               || methodName.equals("set" + variableNameMethodComponent);
    }

    @NotNull
    private static String findSetterMethodName(@NotNull final VariableElement variableElement,
                                               @NotNull final Notation namingNotation) throws UnsupportedOperationException {
        DebugLog.log("Looking for setter");

        for (final ExecutableElement method : getSiblingMethods(variableElement)) {

            final List<? extends VariableElement> parameters = method.getParameters();

            if (method.getReturnType().getKind() == TypeKind.VOID &&
                parameters.size() == 1 &&
                TypeUtils.areEqual(parameters.get(0).asType(), variableElement.asType()) &&
                isSupportedSetter(method, variableElement, namingNotation)) {
                DebugLog.log("Found setter");

                return method.getSimpleName().toString();
            }

        }

        throw new UnsupportedOperationException("Unable to find setter for variable: " + variableElement.getSimpleName());
    }

    private static boolean isBoolean(@NotNull final TypeMirror typeMirror) {
        return TypeUtils.areEqual(TypeUtils.getPrimitive(typeMirror.getKind()), TypeUtils.getPrimitive(TypeKind.BOOLEAN))
               || TypeUtils.areEqual(typeMirror, ElementUtils.getTypeFromClass(Boolean.class));
    }

    private static boolean isSupportedGetter(@NotNull final ExecutableElement method,
                                             @NotNull final VariableElement variableElement,
                                             @NotNull final Notation namingNotation) {
        final String methodName = method.getSimpleName().toString();
        final TypeMirror returnType = method.getReturnType();
        final String variableNameMethodComponent = getVariableNameAsMethodName(variableElement, namingNotation);

        final boolean variableNameBeginsWithIs = variableNameBeginsWithIs(variableNameMethodComponent);

        return (isBoolean(returnType)
                && (methodName.equals(variableNameBeginsWithIs
                                              ? StringUtils.convertCharAtToLowerCase(variableNameMethodComponent, 0)
                                              : "is" + variableNameMethodComponent)))
               || methodName.equals("get" + variableNameMethodComponent);
    }

    private static boolean variableNameBeginsWithIs(@NotNull final String variableName) {
        return variableName.length() > 2
               && (variableName.startsWith("is") || variableName.startsWith("Is"))
               && Character.isUpperCase(variableName.charAt(2));
    }

    @NotNull
    private static String findGetterMethodName(@NotNull final VariableElement variableElement,
                                               @NotNull final Notation namingNotation) throws UnsupportedOperationException {
        DebugLog.log("Looking for getter");

        for (final ExecutableElement method : getSiblingMethods(variableElement)) {

            final TypeMirror returnType = method.getReturnType();
            if (TypeUtils.areEqual(returnType, variableElement.asType())
                && method.getParameters().isEmpty()
                && isSupportedGetter(method, variableElement, namingNotation)) {

                DebugLog.log("Found getter");

                return method.getSimpleName().toString();
            }

        }

        throw new UnsupportedOperationException("Unable to find getter for variable: " + variableElement.getSimpleName());
    }

    @NotNull
    private static String getVariableNameAsMethodName(@NotNull final VariableElement variableElement,
                                                      @NotNull final Notation notation) {
        final String variableName = variableElement.getSimpleName().toString();

        switch (notation) {
            case STANDARD:
                return Character.toUpperCase(variableName.charAt(0)) + variableName.substring(1);
            case HUNGARIAN:
                return variableName.substring(1, variableName.length());
            default:
                throw new UnsupportedOperationException("Unknown notation type");
        }

    }

}
