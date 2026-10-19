package com.udea.digitalbank.customer.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.udea.digitalbank.shared.config.OpenApiExamples;

@Data
public class DocumentTypeResponse {
    @Schema(example = OpenApiExamples.DOCUMENT_TYPE_ID)
    private Short id;
    @Schema(example = OpenApiExamples.DOCUMENT_NAME)
    private String name;
}
