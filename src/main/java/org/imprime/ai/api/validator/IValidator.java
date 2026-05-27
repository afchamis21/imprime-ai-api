package org.imprime.ai.api.validator;

public interface IValidator<T> {
    boolean validate(T value);
}
