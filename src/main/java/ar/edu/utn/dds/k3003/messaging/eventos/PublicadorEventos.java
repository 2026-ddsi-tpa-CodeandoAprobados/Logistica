package ar.edu.utn.dds.k3003.messaging.eventos;

import ar.edu.utn.dds.k3003.logging.Traza;
import ar.edu.utn.dds.k3003.messaging.eventos.EventoLogistica.AsignacionEvento;
import ar.edu.utn.dds.k3003.messaging.eventos.EventoLogistica.DepositoEvento;
import ar.edu.utn.dds.k3003.messaging.eventos.EventoLogistica.StockEvento;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica los cambios de estado de Logística para el modelo de lectura (Entrega 6, CQRS).
 *
 * <p>Tres decisiones, todas pensadas para no afectar la escritura:
 * <ul>
 *   <li><b>Después del commit.</b> El evento se arma y se envía solo si la transacción se confirma.
 *       Si hay rollback, no sale nada: el modelo de lectura no puede enterarse de algo que no pasó.
 *   <li><b>Sin bloquear el pedido.</b> El envío a RabbitMQ lo hace un hilo aparte. Si el broker está
 *       caído, el pedido del cliente no espera.
 *   <li><b>Sin romper la operación.</b> Un fallo al publicar se registra y listo: la fuente de verdad
 *       es la base de Logística, y MongoDB es solo una copia para consultar.
 * </ul>
 *
 * <p>Apagado por defecto: con {@code logistica.eventos.enabled=false} no hace nada.
 */
@Component
public class PublicadorEventos {

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
  private static final int CAPACIDAD_DE_COLA = 1000;

  private final ObjectMapper mapper;

  @Autowired(required = false)
  private RabbitTemplate rabbitTemplate;

  @Value("${logistica.eventos.enabled:false}")
  private boolean habilitado;

  @Value("${logistica.eventos.exchange:donatrack.exchange}")
  private String exchange;

  /** Con false, el envío se hace en el mismo hilo. Existe para poder verificarlo en los tests. */
  @Value("${logistica.eventos.asincrono:true}")
  private boolean asincrono;

  private ThreadPoolExecutor ejecutor;

  public PublicadorEventos(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @PostConstruct
  void iniciar() {
    if (!habilitado) {
      return;
    }
    if (asincrono) {
      // Un solo hilo: los eventos salen en el orden en que se confirmaron las transacciones.
      ejecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<>(CAPACIDAD_DE_COLA),
          tarea -> {
            Thread hilo = new Thread(tarea, "publicador-eventos");
            hilo.setDaemon(true);
            return hilo;
          },
          (tarea, pool) -> log.warn("Cola de eventos llena: se descarta un evento"));
    }
    log.info("Publicación de eventos activa: exchange {}, {}", exchange,
        asincrono ? "en segundo plano" : "en el mismo hilo");
  }

  @PreDestroy
  void detener() {
    if (ejecutor != null) {
      ejecutor.shutdown();
    }
  }

  // ---------------- Depósitos ----------------

  public void depositoCreado(Deposito deposito) {
    publicarDeposito(TipoEventoLogistica.DEPOSITO_CREADO, deposito);
  }

  public void depositoModificado(Deposito deposito) {
    publicarDeposito(TipoEventoLogistica.DEPOSITO_MODIFICADO, deposito);
  }

  public void depositoEliminado(Deposito deposito) {
    publicarDeposito(TipoEventoLogistica.DEPOSITO_ELIMINADO, deposito);
  }

  public void algoritmoConfigurado(Deposito deposito) {
    publicarDeposito(TipoEventoLogistica.DEPOSITO_ALGORITMO_CONFIGURADO, deposito);
  }

  /** El stock del depósito cambió: entró o salió algún paquete. */
  public void stockActualizado(Deposito deposito) {
    publicarDeposito(TipoEventoLogistica.DEPOSITO_STOCK_ACTUALIZADO, deposito);
  }

  // ---------------- Asignaciones y entregas ----------------

  public void asignacionCreada(Asignacion asignacion, Paquete paquete) {
    publicar(TipoEventoLogistica.ASIGNACION_CREADA,
        () -> new Carga(asignacion.getId(), null, asignacion(asignacion, paquete, null)));
  }

  /** La entrega se reportó: la asignación pasó a COMPLETADA. {@code fechaEntrega} es el momento del reporte. */
  public void entregaCompletada(Asignacion asignacion, Paquete paquete, Instant fechaEntrega) {
    publicar(TipoEventoLogistica.ENTREGA_COMPLETADA,
        () -> new Carga(asignacion.getId(), null, asignacion(asignacion, paquete, fechaEntrega)));
  }

  /** Se vació la base de Logística: el modelo de lectura debe borrar lo suyo. */
  public void baseLimpiada() {
    publicar(TipoEventoLogistica.BASE_LIMPIADA, () -> new Carga("logistica", null, null));
  }

  // ---------------- Armado y envío ----------------

  private void publicarDeposito(TipoEventoLogistica tipo, Deposito deposito) {
    publicar(tipo, () -> new Carga(String.valueOf(deposito.getId()), deposito(deposito), null));
  }

  /** Lo que lleva un evento además del sobre: de qué entidad es y su estado completo. */
  private record Carga(String idReferencia, DepositoEvento deposito, AsignacionEvento asignacion) {}

  private void publicar(TipoEventoLogistica tipo, Supplier<Carga> carga) {
    if (!habilitado || rabbitTemplate == null) {
      return;
    }
    String traza = Traza.actual(); // se toma ahora: después del commit puede ser otro contexto
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          armarYDespachar(tipo, carga, traza);
        }
      });
    } else {
      armarYDespachar(tipo, carga, traza);
    }
  }

  /**
   * Se arma después del commit y en el hilo del pedido, no en el hilo de envío: las entidades de JPA no
   * están hechas para compartirse, y recién después del commit tienen los ids que asignó la base.
   */
  private void armarYDespachar(TipoEventoLogistica tipo, Supplier<Carga> cargaDelEvento, String traza) {
    try {
      Carga carga = cargaDelEvento.get();
      Map<String, Object> extra = new LinkedHashMap<>();
      if (traza != null) {
        extra.put("traceId", traza);
      }
      EventoLogistica evento = new EventoLogistica(UUID.randomUUID().toString(), tipo, Instant.now(),
          carga.idReferencia(), carga.deposito(), carga.asignacion(), extra);
      byte[] cuerpo = mapper.writeValueAsBytes(evento);
      Runnable envio = () -> enviar(evento, cuerpo, traza);
      if (ejecutor != null) {
        ejecutor.execute(envio);
      } else {
        envio.run();
      }
    } catch (Exception e) {
      log.warn("No se pudo armar el evento {}: {}", tipo, e.getMessage());
    }
  }

  private void enviar(EventoLogistica evento, byte[] cuerpo, String traza) {
    try {
      Message mensaje = MessageBuilder.withBody(cuerpo)
          .setContentType(MessageProperties.CONTENT_TYPE_JSON)
          .setContentEncoding("UTF-8")
          .setMessageId(evento.id())
          .setTimestamp(Date.from(evento.timestamp()))
          .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
          .build();
      rabbitTemplate.send(exchange, evento.tipo().routingKey(), mensaje);
      log.info("Evento {} publicado en {} ({}), idReferencia {}, traceId {}", evento.tipo(), exchange,
          evento.tipo().routingKey(), evento.idReferencia(), traza);
    } catch (Exception e) {
      log.warn("No se pudo publicar el evento {} (idReferencia {}, traceId {}): {}", evento.tipo(),
          evento.idReferencia(), traza, e.getMessage());
    }
  }

  // ---------------- Estado de las entidades ----------------

  static DepositoEvento deposito(Deposito d) {
    List<StockEvento> stock = d.getStock().stream()
        .map(p -> new StockEvento(p.getId() == null ? null : String.valueOf(p.getId()),
            p.getDonacionID(), p.getProductoID(), p.getCantidad()))
        .toList();
    return new DepositoEvento(String.valueOf(d.getId()), d.getNombre(), d.getDireccion(),
        d.getCapacidadMaxima(), d.unidadesOcupadas(),
        d.getAlgoritmo() == null ? null : d.getAlgoritmo().name(), stock);
  }

  static AsignacionEvento asignacion(Asignacion a, Paquete p, Instant fechaEntrega) {
    return new AsignacionEvento(a.getId(), a.getPaqueteID(),
        p == null ? null : p.getDonacionID(),
        p == null ? null : p.getProductoID(),
        p == null ? null : p.getCantidad(),
        a.getNecesidadID(),
        a.getOrigen() == null ? null : a.getOrigen().name(),
        a.getEstado() == null ? null : a.getEstado().name(),
        a.getFecha() == null ? null : a.getFecha().atZone(ZoneId.systemDefault()).toInstant(),
        fechaEntrega);
  }
}
