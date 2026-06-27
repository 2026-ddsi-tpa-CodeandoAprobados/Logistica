package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.model.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LogisticaDataMapper {

    public DepositoDTO map(Deposito deposito) {
        List<PaqueteDTO> stockDTOs = deposito.getStock().stream()
                .map(this::map)
                .collect(Collectors.toList());

        return new DepositoDTO(
                String.valueOf(deposito.getId()),
                deposito.getAlgoritmo(),
                deposito.getNombre(),
                deposito.getDireccion(),
                deposito.getCapacidadMaxima(),
                stockDTOs
        );
    }

    public Deposito map(DepositoDTO dto) {
        Deposito d = new Deposito(dto.nombre(), dto.direccion(), dto.capacidadMaxima());
        if (dto.id() != null) {
            try {
                d.setId(Integer.valueOf(dto.id()));
            } catch (NumberFormatException e) { }
        }
        d.setAlgoritmo(dto.algoritmo());
        return d;
    }

    public PaqueteDTO map(Paquete paquete) {
        return new PaqueteDTO(
                String.valueOf(paquete.getId()),
                paquete.getDonacionID(),
                paquete.getProductoID(),
                paquete.getCantidad()
        );
    }

    public AsignacionDTO map(Asignacion asignacion) {
        return new AsignacionDTO(
                String.valueOf(asignacion.getId()),
                String.valueOf(asignacion.getPaqueteID()),
                String.valueOf(asignacion.getNecesidadID()),
                asignacion.getFecha(),
                asignacion.getEstado()
        );
    }
}