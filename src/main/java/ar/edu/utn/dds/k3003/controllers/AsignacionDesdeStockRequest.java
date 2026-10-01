package ar.edu.utn.dds.k3003.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Pedido de Donadores para cubrir una necesidad con stock.")
public record AsignacionDesdeStockRequest(
        @Schema(description = "Cantidad que se propone asignar. Logística asigna el mínimo entre "
                + "esto y lo disponible. Nulo o cero significa que no hay nada para asignar.",
                example = "10") Integer cantidad,
        @Schema(description = "Necesidad que se quiere cubrir. Es obligatoria.",
                example = "z64taSQ4r5WBvv0SynbrD") String necesidadID) {}
