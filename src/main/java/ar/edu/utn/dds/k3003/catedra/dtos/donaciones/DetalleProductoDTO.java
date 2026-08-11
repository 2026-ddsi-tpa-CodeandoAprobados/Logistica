package ar.edu.utn.dds.k3003.catedra.dtos.donaciones;

public record DetalleProductoDTO(
        String id,
        String productoID,   // ← antes: productoId
        Integer cantidadProducto
) {}