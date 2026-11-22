package org.imprime.ai.api.http.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.DocumentTypes;
import org.imprime.ai.api.validator.annotations.Regex;
import org.imprime.ai.api.validator.annotations.Required;
import org.imprime.ai.api.validator.annotations.StringLength;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

@Slf4j
@Schema(description = "Dados para cadastro de um novo usuário (cliente ou Maker)")
public record RegisterUserRequest(
        @Schema(description = "Primeiro nome do usuário (3 a 100 letras)", example = "Lucas", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_FIRST_NAME)
        @StringLength(minLength = 3, maxLength = 100, minLengthMessage = MessageCd.USER_FIRST_NAME_INVALID_LENGTH, maxLengthMessage =  MessageCd.USER_FIRST_NAME_INVALID_LENGTH)
        @Regex(regex = "^[\\p{L}]+$", message =  MessageCd.INVALID_USER_FIRST_NAME)
        String firstName,

        @Schema(description = "Sobrenome do usuário (3 a 100 caracteres)", example = "Amorim", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_LAST_NAME)
        @StringLength(minLength = 3, maxLength = 100, minLengthMessage = MessageCd.USER_LAST_NAME_INVALID_LENGTH, maxLengthMessage =  MessageCd.USER_LAST_NAME_INVALID_LENGTH)
        @Regex(regex = "^[\\p{L}\\s]+$", message =  MessageCd.INVALID_USER_LAST_NAME)
        String lastName,

        @Schema(description = "Endereço de e-mail único (até 255 caracteres)", example = "lucas@imprime.ai", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_EMAIL)
        @StringLength(maxLength = 255, minLengthMessage = MessageCd.INVALID_USER_EMAIL, maxLengthMessage =  MessageCd.INVALID_USER_EMAIL)
        @Regex(regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$",  message =  MessageCd.INVALID_USER_EMAIL)
        String email,

        @Schema(description = "Senha (8 a 32 caracteres, contendo letra maiúscula, minúscula, número e caractere especial)", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_PASSWORD)
        @StringLength(minLength = 8,maxLength = 32, minLengthMessage = MessageCd.INVALID_USER_PASSWORD, maxLengthMessage =  MessageCd.INVALID_USER_PASSWORD)
        @Regex(regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",  message =  MessageCd.INVALID_USER_PASSWORD)
        String password,

        @Schema(description = "Confirmação da senha (deve ser idêntica ao campo `password`)", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.USER_PASSWORD_MISMATCH)
        String confirmPassword,

        @Schema(description = "Tipo de documento pessoal (`CPF` ou `RG`)", example = "CPF", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_DOCUMENT_TYPE)
        @DocumentTypes(types = {DocumentType.CPF, DocumentType.RG}, message = MessageCd.INVALID_DOCUMENT_TYPE)
        DocumentType documentType,

        @Schema(description = "Número do documento pessoal (CPF ou RG válido)", example = "52998224725", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_DOCUMENT)
        String document,

        @Schema(description = "Número de telefone celular com DDD", example = "11987654321", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_PHONE_NUMBER)
//        @PhoneNumber(invalidPhoneNumberMessage = MessageCd.INVALID_USER_PHONE_NUMBER)
        String phoneNumber,

        @Schema(description = "Endereço principal do usuário", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_USER_ADDRESS)
        RegisterAddressRequest address,

        @Schema(description = "Indica se o usuário está se cadastrando como Maker (caso `true`, o campo `company` é obrigatório)", example = "false")
        Boolean isMaker,
        @Schema(description = "Dados da empresa do Maker (obrigatório quando `isMaker` for `true`)")
        @Nullable RegisterCompanyRequest company,
        @Schema(description = "URL opcional da foto de perfil do usuário")
        @Nullable String avatarUrl
) implements ValidatedRequest {
    @Nullable
    public MessageCd getInvalidReason() {
        try {
            MessageCd messageCd = ValidatorEngine.validate(this);
            if (messageCd != null) {
                return messageCd;
            }
        } catch (Exception e) {
            log.error("Error running reflective validations for User Request!", e);
        }

        if (!password.equals(confirmPassword)) {
            return MessageCd.USER_PASSWORD_MISMATCH;
        }

        MessageCd invalidDocumentMessage = documentType.validate(document);
        if (invalidDocumentMessage != null) {
            return invalidDocumentMessage;
        }

        if (Boolean.TRUE.equals(isMaker)) {
            if (company == null) {
                return MessageCd.MISSING_USER_COMPANY;
            }

            MessageCd messageCd = company.getInvalidReason();
            if (messageCd != null) {
                return messageCd;
            }
        }

        return address.getInvalidReason();
    }
}