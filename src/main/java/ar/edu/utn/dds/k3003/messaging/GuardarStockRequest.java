package ar.edu.utn.dds.k3003.messaging;

/**
 * Cuerpo del POST /depositos/{id}/stock que hace el Worker para guardar el sobrante
 * de una donación en el stock del depósito.
 */
public record GuardarStockRequest(
        String donacionID,
        String productoID,
        Integer cantidad) {}
