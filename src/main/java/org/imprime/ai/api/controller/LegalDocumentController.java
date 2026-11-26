package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.service.LegalDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/legal/documents")
@SkipAuth
@SecurityRequirements
public class LegalDocumentController {
    private final LegalDocumentService legalDocuments;

    @GetMapping
    public ResponseEntity<BaseResponse<List<LegalDocumentService.Document>>> list(
            @RequestParam(defaultValue = "pt-BR") String locale,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String version) {
        return BaseResponse.ok(legalDocuments.list(locale).stream()
                .filter(document -> type == null || document.documentType().equals(type))
                .filter(document -> version == null || document.version().equals(version))
                .toList());
    }
}
