package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CpfValidatorTest {

    @Test
    void validate() {
        CpfValidator validator = new CpfValidator();

        assertNull(validator.validate("086.394.329.29"));
        assertNull(validator.validate("064.868.633.74"));

        assertEquals(MessageCd.INVALID_CPF, validator.validate(null));
        assertEquals(MessageCd.INVALID_CPF, validator.validate(""));
        assertEquals(MessageCd.INVALID_CPF, validator.validate("ab.cde.345"));
        assertEquals(MessageCd.INVALID_CPF, validator.validate("012.345.678.99"));
        assertEquals(MessageCd.INVALID_CPF, validator.validate("111.111.111.11"));
        assertEquals(MessageCd.INVALID_CPF, validator.validate("000.000.000.00"));
    }

    @Test
    void isValid() {
        assertTrue(CpfValidator.isValid("086.394.329.29"));
        assertTrue(CpfValidator.isValid("064.868.633.74"));

        assertFalse(CpfValidator.isValid(null));
        assertFalse(CpfValidator.isValid(""));
        assertFalse(CpfValidator.isValid("ab.cde.345"));
        assertFalse(CpfValidator.isValid("012.345.678.99"));
        assertFalse(CpfValidator.isValid("111.111.111.11"));
        assertFalse(CpfValidator.isValid("000.000.000.00"));
    }
}