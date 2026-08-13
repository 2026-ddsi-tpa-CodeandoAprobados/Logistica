package ar.edu.utn.dds.k3003.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Entrega 4 - Parte B. Publica la donación (ya validada de espacio) en la cola de trabajo.
 * Sólo existe como bean cuando la mensajería está activada.
 */
@Component
@ConditionalOnProperty(name = "logistica.mensajeria.enabled", havingValue = "true")
public class DonacionPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String cola;

    public DonacionPublisher(RabbitTemplate rabbitTemplate,
                             @Value("${logistica.mensajeria.cola}") String cola) {
        this.rabbitTemplate = rabbitTemplate;
        this.cola = cola;
    }

    public void publicar(DonacionMessage mensaje) {
        // Exchange por defecto ("") + routing key = nombre de la cola.
        rabbitTemplate.convertAndSend(cola, mensaje);
    }
}
