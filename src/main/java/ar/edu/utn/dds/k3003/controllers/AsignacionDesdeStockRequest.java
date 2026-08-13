package ar.edu.utn.dds.k3003.controllers;

/**
 * Cuerpo de POST /stock/{productoID}/asignaciones: la cantidad que necesita la nueva
 * necesidad y su id, para asignar desde stock (origen SOLICITUD_DONADORES).
 */
public record AsignacionDesdeStockRequest(Integer cantidad, String necesidadID) {}
