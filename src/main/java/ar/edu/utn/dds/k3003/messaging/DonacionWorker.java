package ar.edu.utn.dds.k3003.messaging;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.LogisticaApiClient;
import ar.edu.utn.dds.k3003.model.Matchmaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entrega 4 - Parte B. Worker STATELESS: consume donaciones de la cola, calcula el matchmaking
 * (sin BD) y persiste el resultado llamando por HTTP a la API de Logística. No accede a la
 * base de datos del componente. Puede correr en la misma instancia web (TIP 2) o en un
 * proceso aparte suscripto a la misma cola (TIP 3).
 */
@Component
@ConditionalOnProperty(name = "logistica.mensajeria.enabled", havingValue = "true")
public class DonacionWorker {

    private static final Logger log = LoggerFactory.getLogger(DonacionWorker.class);

    private final EntidadesClient entidadesClient;
    private final LogisticaApiClient logisticaApiClient;
    private final Matchmaker matchmaker;

    public DonacionWorker(EntidadesClient entidadesClient,
                          LogisticaApiClient logisticaApiClient,
                          Matchmaker matchmaker) {
        this.entidadesClient = entidadesClient;
        this.logisticaApiClient = logisticaApiClient;
        this.matchmaker = matchmaker;
    }

    @RabbitListener(queues = "${logistica.mensajeria.cola}")
    public void procesar(DonacionMessage mensaje) {
        try {
            for (DonacionMessage.Item item : mensaje.items()) {
                procesarItem(mensaje, item);
            }
        } catch (Exception e) {
            // El espacio ya fue validado en la recepción; ante un fallo puntual, logueamos.
            log.error("Error procesando donación {} en el worker: {}",
                    mensaje.donacionID(), e.getMessage(), e);
        }
    }

    private void procesarItem(DonacionMessage mensaje, DonacionMessage.Item item) {
        String productoID = item.productoID();
        int cantidad = item.cantidad();

        List<NecesidadMaterialDTO> necesidades =
                entidadesClient.getAllNecesidadesDeUnProducto(productoID);

        if (necesidades == null || necesidades.isEmpty()) {
            guardarEnStock(mensaje, productoID, cantidad);
            return;
        }

        NecesidadMaterialDTO necesidad =
                matchmaker.elegirNecesidadStateless(necesidades, mensaje.algoritmo(), cantidad);

        if (necesidad == null) {
            guardarEnStock(mensaje, productoID, cantidad);
            return;
        }

        int aAsignar = matchmaker.cantidadAAsignarStateless(necesidad, cantidad);
        if (aAsignar <= 0) {
            guardarEnStock(mensaje, productoID, cantidad);
            return;
        }

        // Alta de la asignación vía API (Logística escribe en la BD).
        logisticaApiClient.altaAsignacion(new AltaAsignacionRequest(
                mensaje.donacionID(), productoID, aAsignar, necesidad.id()));

        int sobrante = cantidad - aAsignar;
        if (sobrante > 0) {
            guardarEnStock(mensaje, productoID, sobrante);
        }
    }

    private void guardarEnStock(DonacionMessage mensaje, String productoID, int cantidad) {
        logisticaApiClient.guardarSobrante(mensaje.depositoID(),
                new GuardarStockRequest(mensaje.donacionID(), productoID, cantidad));
    }
}
