package ar.edu.utn.dds.k3003.messaging;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import java.util.List;

/**
 * Mensaje que viaja por la cola con la donación a asignar. Incluye el algoritmo del
 * depósito para que el Worker (stateless) pueda calcular el matchmaking sin consultar la BD.
 *
 * <p>Lleva también la traza del pedido que lo originó, porque el Worker procesa en otro hilo y
 * en otro momento. Sin ella, los logs del trabajo asincrónico no se podrían vincular con la
 * donación que los disparó. Es nula en mensajes anteriores a este campo, y el Worker lo tolera.
 */
public record DonacionMessage(
        String donacionID,
        String depositoID,
        TipoAlgoritmoEnum algoritmo,
        List<Item> items,
        String traceId) {

    public record Item(String productoID, Integer cantidad) {}
}
