package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;

@Component
public class Matchmaker {

    public NecesidadMaterialDTO calcularMejorOpcion(List<NecesidadMaterialDTO> necesidades) {
        return necesidades.stream()
                .max(Comparator.comparingDouble(this::calcularScore))
                .orElseThrow(() -> new RuntimeException("No hay necesidades disponibles"));
    }

    private double calcularScore(NecesidadMaterialDTO n) {
        double cantidadActual = 0.0;
        double ratioCobertura = cantidadActual / n.cantidadObjetivo();
        if (ratioCobertura == 0) return Double.MAX_VALUE;
        return n.nivelDeUrgencia() / ratioCobertura;
    }
}
