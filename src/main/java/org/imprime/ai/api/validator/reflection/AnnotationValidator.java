package org.imprime.ai.api.validator.reflection;

import org.imprime.ai.api.validator.IValidator;

import java.lang.annotation.Annotation;

public interface AnnotationValidator<A extends Annotation, T> {

    IValidator<T> create(A annotation);
}