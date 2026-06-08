package ar.edu.utn.dds.k3003.catedra.dtos.donaciones;

import java.util.List;

public record DonacionDTO(
        String id,
        String donadorID,
        String depositoID,
        String descripcion,
        List<DetalleProductoDTO> detallesProductosDTO,
        EstadoDonacionEnum estado
) {
    public DonacionDTO(String id, String donadorID, String depositoID, String descripcion,
                       String productoID, Integer cantidad, EstadoDonacionEnum estado) {
        this(id, donadorID, depositoID, descripcion,
                List.of(new DetalleProductoDTO("auto-gen", productoID, cantidad)),
                estado);
    }
}