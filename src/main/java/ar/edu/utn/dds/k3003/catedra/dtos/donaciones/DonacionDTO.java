package ar.edu.utn.dds.k3003.catedra.dtos.donaciones;

import java.util.List;

public record DonacionDTO(
        String id,
        String donadorID,
        String depositoID,
        String descripcion,
        List<DetalleProductoDTO> detallesProductosDTO,
        EstadoDonacionEnum estado){}