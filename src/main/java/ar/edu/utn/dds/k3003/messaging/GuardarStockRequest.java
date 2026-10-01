package ar.edu.utn.dds.k3003.messaging;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo del POST /depositos/{id}/stock que hace el Worker para guardar el sobrante
 * de una donación en el stock del depósito.
 */
@Schema(description = "Sobrante de una donación que se guarda en el stock del depósito.")
public record GuardarStockRequest(
        @Schema(description = "Donación de la que proviene el sobrante.", example = "703") String donacionID,
        @Schema(description = "Producto que se guarda.", example = "15") String productoID,
        @Schema(description = "Unidades que se guardan.", example = "10") Integer cantidad) {}
