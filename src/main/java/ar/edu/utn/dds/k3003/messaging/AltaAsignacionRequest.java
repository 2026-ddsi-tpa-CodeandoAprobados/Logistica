package ar.edu.utn.dds.k3003.messaging;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo del POST /asignaciones que hace el Worker para dar de alta una asignación
 * calculada por matchmaking (el Worker no tiene BD, escribe vía API de Logística).
 */
@Schema(description = "Porción de una donación que el Worker asignó a una necesidad.")
public record AltaAsignacionRequest(
        @Schema(description = "Donación de la que proviene la porción.", example = "703") String donacionID,
        @Schema(description = "Producto asignado.", example = "15") String productoID,
        @Schema(description = "Unidades asignadas a la necesidad.", example = "20") Integer cantidad,
        @Schema(description = "Necesidad de Donadores y Entidades que se cubre.",
                example = "z64taSQ4r5WBvv0SynbrD") String necesidadID) {}
