package org.imprime.ai.api.http.request.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.DocumentTypes;
import org.imprime.ai.api.validator.annotations.Regex;
import org.imprime.ai.api.validator.annotations.Required;
import org.imprime.ai.api.validator.annotations.StringLength;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Schema(description = "Dados para cadastro de empresa (Maker)")
public record RegisterCompanyRequest(

        @Schema(description = "Nome da empresa ou oficina Maker (2 a 100 caracteres)", example = "Imprime 3D Lab", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_COMPANY_NAME)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_COMPANY_NAME_LENGTH, maxLengthMessage = MessageCd.INVALID_COMPANY_NAME_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'&.,()-]+$", message = MessageCd.INVALID_COMPANY_NAME)
        String name,

        @Schema(description = "Tipo de documento da empresa (`CNPJ` ou `CPF`)", example = "CNPJ", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_COMPANY_DOCUMENT_TYPE)
        @DocumentTypes(types = {DocumentType.CNPJ, DocumentType.CPF}, message = MessageCd.INVALID_COMPANY_DOCUMENT_TYPE)
        DocumentType documentType,

        @Schema(description = "Número do documento da empresa (CNPJ ou CPF)", example = "12345678000195", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_COMPANY_DOCUMENT)
        String document,

        @Schema(description = "Endereço sede da empresa", requiredMode = Schema.RequiredMode.REQUIRED)
        @Required(message = MessageCd.MISSING_COMPANY_ADDRESS)
        RegisterAddressRequest address,

        @Schema(description = "Lista de impressoras selecionadas do catálogo fixo (1 ou mais)")
        List<RegisterMakerPrinterRequest> printers,

        @Schema(description = "GUID da impressora principal do catálogo (fallback de entrada única)")
        String primaryPrinterCatalogGuid,

        @Schema(description = "Indica se o Maker aceita pedidos de orçamento personalizado (padrão true)")
        Boolean acceptingCustomQuotes,

        @Schema(description = "Indica se o setup do Maker imprime colorido / multicolor")
        Boolean supportsMulticolor,

        @Schema(description = "Número máximo de cores/slots simultâneos no estúdio")
        Integer maxMulticolorSlots,

        @Schema(description = "Materiais operados pelo Maker (quando omitido, herda dos modelos de impressora)")
        List<MaterialType> materials,

        @Schema(description = "Oferece pós-processamento / lixamento / acabamento")
        Boolean offersPostProcessing,

        @Schema(description = "Oferece pintura artística ou automotiva")
        Boolean offersPainting,

        @Schema(description = "Oferece modelagem CAD sob medida")
        Boolean offersCadModeling,

        @Schema(description = "Aceita pedidos expressos com prioridade")
        Boolean supportsExpressOrders,

        @Schema(description = "Menor altura de camada atendida em mm (ex: 0.08)")
        BigDecimal minLayerHeightMm,

        @Schema(description = "Observações técnicas do estúdio")
        String technicalNotes

) implements ValidatedRequest {

    public RegisterCompanyRequest(
            String name,
            DocumentType documentType,
            String document,
            RegisterAddressRequest address
    ) {
        this(
                name, documentType, document, address,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Nullable
    @Override
    public MessageCd getInvalidReason() {
        try {
            MessageCd messageCd = ValidatorEngine.validate(this);
            if (messageCd != null) {
                return messageCd;
            }
        } catch (Exception e) {
            log.error("Error running reflective validations for Company Request!", e);
        }

        MessageCd invalidDocumentMessage = documentType.validate(document);
        if (invalidDocumentMessage != null) {
            return invalidDocumentMessage;
        }

        if (printers != null) {
            for (RegisterMakerPrinterRequest printer : printers) {
                if (printer == null || printer.printerCatalogGuid() == null || printer.printerCatalogGuid().isBlank()) {
                    return MessageCd.VALIDATION_ERROR;
                }
                if (printer.quantity() != null && (printer.quantity() < 1 || printer.quantity() > 20)) {
                    return MessageCd.VALIDATION_ERROR;
                }
            }
        }

        // Nested validation
        return address.getInvalidReason();
    }
}