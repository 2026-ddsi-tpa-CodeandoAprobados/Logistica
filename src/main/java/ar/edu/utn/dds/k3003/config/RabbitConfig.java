package ar.edu.utn.dds.k3003.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Entrega 4 - Parte B. Configuración de mensajería (CloudAMQP / RabbitMQ).
 * Todo queda gateado por {@code logistica.mensajeria.enabled=true}: sin el flag,
 * la app arranca sin tocar RabbitMQ y procesa las donaciones en modo síncrono (Parte A).
 */
@Configuration
@ConditionalOnProperty(name = "logistica.mensajeria.enabled", havingValue = "true")
public class RabbitConfig {

    @Value("${logistica.mensajeria.cola}")
    private String cola;

    /** Cola durable donde se publican las donaciones a asignar. */
    @Bean
    public Queue colaDonaciones() {
        return QueueBuilder.durable(cola).build();
    }

    /** Serializa los mensajes como JSON (tanto al publicar como al consumir). */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
