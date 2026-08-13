package ar.edu.utn.dds.k3003.messaging;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import java.util.List;

/**
 * Mensaje que viaja por la cola con la donación a asignar. Incluye el algoritmo del
 * depósito para que el Worker (stateless) pueda calcular el matchmaking sin consultar la BD.
 */
public record DonacionMessage(
        String donacionID,
        String depositoID,
        TipoAlgoritmoEnum algoritmo,
        List<Item> items) {

    public record Item(String productoID, Integer cantidad) {}
}
