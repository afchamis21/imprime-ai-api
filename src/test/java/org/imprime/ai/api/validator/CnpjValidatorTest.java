package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CnpjValidatorTest {

    @Test
    void validate() {
        CnpjValidator validator = new CnpjValidator();

        assertNull(validator.validate("11.222.333/0001-81"));
        assertNull(validator.validate("45.723.174/0001-10"));

        assertEquals(MessageCd.INVALID_CNPJ, validator.validate("ab.cde.345"));

        assertEquals(MessageCd.INVALID_CNPJ, validator.validate("11.222.333/0001-82"));
    }

    @Test
    void isValid() {
        assertTrue(CnpjValidator.isValid("11.222.333/0001-81"));
        assertTrue(CnpjValidator.isValid("45.723.174/0001-10"));

        assertFalse(CnpjValidator.isValid("ab.cde.345"));
        assertFalse(CnpjValidator.isValid("11.222.333/0001-82"));

        assertFalse(CnpjValidator.isValid(null));
        assertFalse(CnpjValidator.isValid(""));
        assertFalse(CnpjValidator.isValid("00000000000000"));
        assertFalse(CnpjValidator.isValid("11111111111111"));
        assertFalse(CnpjValidator.isValid("12345678000100"));
    }
}