package ar.edu.utn.dds.k3003.messaging;

/**
 * Cuerpo del POST /asignaciones que hace el Worker para dar de alta una asignación
 * calculada por matchmaking (el Worker no tiene BD, escribe vía API de Logística).
 */
public record AltaAsignacionRequest(
        String donacionID,
        String productoID,
        Integer cantidad,
        String necesidadID) {}
