package ar.edu.utn.dds.k3003.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Entrega 6. Declara en RabbitMQ el exchange compartido por los módulos y la cola de Logística, con el
 * mismo esquema que Donadores: un exchange topic {@code donatrack.exchange} y una cola propia atada con
 * {@code logistica.#}. Solo existe con {@code logistica.eventos.enabled=true}.
 *
 * <p>El exchange se declara igual que en Donadores (durable, sin borrado automático): si dos módulos lo
 * declaran con argumentos distintos, RabbitMQ rechaza al segundo.
 */
@Configuration
@ConditionalOnProperty(name = "logistica.eventos.enabled", havingValue = "true")
public class EventosConfig {

  @Bean
  public TopicExchange exchangeDeEventos(@Value("${logistica.eventos.exchange:donatrack.exchange}") String nombre) {
    return new TopicExchange(nombre, true, false);
  }

  @Bean
  public Queue colaDeEventos(@Value("${logistica.eventos.cola:logistica.cola}") String nombre) {
    return QueueBuilder.durable(nombre).build();
  }

  @Bean
  public Binding bindingDeEventos(@Qualifier("colaDeEventos") Queue cola,
                                  @Qualifier("exchangeDeEventos") TopicExchange exchange) {
    // Hay otra cola en el contexto (la de donaciones del Worker), por eso se pide cada una por nombre.
    return BindingBuilder.bind(cola).to(exchange).with("logistica.#");
  }
}
