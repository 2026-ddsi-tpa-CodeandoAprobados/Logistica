package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.repositories.AsignacionRepository;
import ar.edu.utn.dds.k3003.repositories.PaqueteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class Matchmaker {

    @Autowired
    private AsignacionRepository asignacionRepository;
    @Autowired
    private PaqueteRepository paqueteRepository;

    public NecesidadMaterialDTO calcularMejorOpcion(
            List<NecesidadMaterialDTO> necesidades,
            TipoAlgoritmoEnum algoritmo,
            Integer cantidadDonada) {

        List<NecesidadMaterialDTO> elegibles = necesidades.stream()
                .filter(n -> esElegible(n, cantidadDonada))
                .toList();

        if (elegibles.isEmpty()) {
            throw new RuntimeException(
                    "Ninguna necesidad puede ser satisfecha con esta donación " +
                            "(las recurrentes no admiten donaciones parciales)");
        }

        TipoAlgoritmoEnum algoritmoAUsar =
                (algoritmo != null) ? algoritmo : TipoAlgoritmoEnum.SUB_ATENDIDOS;

        return switch (algoritmoAUsar) {
            case SUB_ATENDIDOS, PRIORIDAD -> elegibles.stream()
                    .max(Comparator.comparingDouble(
                            n -> n.cantidadObjetivo() - cantidadAsignadaA(n.id())))
                    .orElseThrow();
            case PRIORIDAD_POR_SCORE -> elegibles.stream()
                    .max(Comparator.comparingDouble(this::calcularScore))
                    .orElseThrow();
        };
    }

    /**
     * Cantidad de la donación que se debe asignar a la necesidad elegida:
     * el mínimo entre lo donado y lo que le falta para llegar al objetivo.
     * Para EXTRAORDINARIA con faltante > donado, esto devuelve todo lo donado.
     * El sobrante (donado - resultado) es lo que va al stock.
     */
    public int cantidadAAsignar(NecesidadMaterialDTO n, Integer cantidadDonada) {
        int faltante = (int) Math.max(0, n.cantidadObjetivo() - cantidadAsignadaA(n.id()));
        return Math.min(cantidadDonada, faltante);
    }

    private boolean esElegible(NecesidadMaterialDTO n, Integer cantidadDonada) {
        double faltante = n.cantidadObjetivo() - cantidadAsignadaA(n.id());
        if (faltante <= 0) return false;

        if (n.tipo() == TipoNecesidadMaterialEnum.RECURRENTE) {
            return cantidadDonada >= faltante;
        }
        return true;
    }

    private double calcularScore(NecesidadMaterialDTO n) {
        double cantidadAsignada = cantidadAsignadaA(n.id());
        double ratioCobertura = cantidadAsignada / n.cantidadObjetivo();
        if (ratioCobertura == 0) return Double.MAX_VALUE;
        return n.nivelDeUrgencia() / ratioCobertura;
    }

    private double cantidadAsignadaA(String necesidadID) {
        return asignacionRepository.findByNecesidadID(necesidadID).stream()
                .mapToInt(a -> {
                    try {
                        return paqueteRepository.findById(Integer.valueOf(a.getPaqueteID()))
                                .map(Paquete::getCantidad)
                                .orElse(0);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .sum();
    }
}