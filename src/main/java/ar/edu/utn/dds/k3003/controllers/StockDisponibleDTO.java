package ar.edu.utn.dds.k3003.controllers;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de GET /stock/{productoID}: cuánto hay disponible de ese producto. */
@Schema(description = "Stock disponible de un producto, sumando todos los depósitos.")
public record StockDisponibleDTO(
        @Schema(description = "Producto consultado.", example = "15") String productoID,
        @Schema(description = "Unidades disponibles. Cero si no hay.", example = "15") Integer cantidadDisponible) {}
