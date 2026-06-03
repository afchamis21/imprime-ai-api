package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RgValidatorTest {

    @Test
    void validate() {
        RgValidator validator = new RgValidator();

        assertNull(validator.validate("58.062.642-8"));
        assertNull(validator.validate("12.345.678-X"));

        assertEquals(MessageCd.INVALID_RG, validator.validate(null));
        assertEquals(MessageCd.INVALID_RG, validator.validate(""));
        assertEquals(MessageCd.INVALID_RG, validator.validate("ab.cde.345"));
        assertEquals(MessageCd.INVALID_RG, validator.validate("12.345.678-XX"));
        assertEquals(MessageCd.INVALID_RG, validator.validate("12.345.67"));
    }

    @Test
    void isValid() {
        assertTrue(RgValidator.isValid("58.062.642-8"));
        assertTrue(RgValidator.isValid("12.345.678-X"));

        assertFalse(RgValidator.isValid(null));
        assertFalse(RgValidator.isValid(""));
        assertFalse(RgValidator.isValid("ab.cde.345"));
        assertFalse(RgValidator.isValid("12.345.678-XX"));
        assertFalse(RgValidator.isValid("12.345.67"));
    }
}