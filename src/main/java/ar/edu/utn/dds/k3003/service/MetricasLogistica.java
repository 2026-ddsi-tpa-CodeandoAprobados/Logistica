package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Métricas de negocio del módulo, en un solo lugar para que los servicios no dependan de
 * Micrometer ni tengan que verificar si el registro está disponible. Todas llevan la etiqueta
 * {@code modulo=logistica}, así un único tablero de Datadog puede mostrar los cuatro módulos.
 *
 * <p>Hay dos clases. Los <b>contadores</b> miden lo que pasó: donaciones y unidades recibidas,
 * unidades asignadas y entregadas, donaciones rechazadas por capacidad y avisos que no llegaron
 * a otro módulo. Los <b>indicadores de estado</b> miden cómo está el sistema ahora: unidades en
 * stock, capacidad total y ocupación del depósito más lleno. Sobre estos últimos se arman las
 * alarmas de comportamiento inusual, porque un contador sólo dice que algo ocurrió.
 *
 * <p>El registro es opcional: si la aplicación corre sin exportador de métricas, los métodos
 * no hacen nada en vez de fallar.
 */
@Component
public class MetricasLogistica {

  public static final String ENTREGAS_COMPLETADAS = "logistica.entregas_completadas";
  public static final String UNIDADES_ENTREGADAS = "logistica.unidades_entregadas";
  public static final String ASIGNACIONES_MATCHMAKING = "logistica.asignaciones_matchmaking";
  public static final String ASIGNACIONES_SOLICITUD = "logistica.asignaciones_solicitud_donadores";
  public static final String UNIDADES_ASIGNADAS = "logistica.unidades_asignadas";
  public static final String PAQUETES_EN_STOCK = "logistica.paquetes_en_stock";
  public static final String DONACIONES_RECIBIDAS = "logistica.donaciones_recibidas";
  public static final String UNIDADES_RECIBIDAS = "logistica.unidades_recibidas";
  public static final String DONACIONES_RECHAZADAS = "logistica.donaciones_rechazadas";
  public static final String NOTIFICACIONES_FALLIDAS = "logistica.notificaciones_fallidas";

  public static final String UNIDADES_EN_STOCK = "logistica.unidades_en_stock";
  public static final String CAPACIDAD_TOTAL = "logistica.capacidad_total";
  public static final String OCUPACION_MAXIMA = "logistica.ocupacion_maxima";

  private static final String MOTIVO_CAPACIDAD = "capacidad";
  private static final List<String> ORIGENES = List.of("matchmaking", "solicitud_donadores");
  private static final List<String> DESTINOS = List.of("entidades", "donaciones");

  @Autowired(required = false)
  private MeterRegistry meterRegistry;

  @Autowired(required = false)
  private DepositoRepository depositoRepository;

  /** Cuánto se reutiliza una lectura de los depósitos entre un indicador y el siguiente. */
  @Value("${logistica.metricas.vigencia-segundos:10}")
  private long vigenciaSegundos;

  private volatile Foto ultimaFoto;

  /** Estado de los depósitos en un instante. Se lee una vez para los tres indicadores. */
  private record Foto(double unidadesEnStock, double capacidadTotal, double ocupacionMaxima,
                      Instant tomadaEn) {}

  @PostConstruct
  public void inicializar() {
    if (meterRegistry == null) {
      return;
    }
    // Se registran todas las series en 0 desde el arranque: una alarma sobre un contador que
    // todavía no existe no tiene datos para evaluar, y Datadog la mostraría sin información.
    for (String nombre : List.of(ENTREGAS_COMPLETADAS, UNIDADES_ENTREGADAS, ASIGNACIONES_MATCHMAKING,
            ASIGNACIONES_SOLICITUD, PAQUETES_EN_STOCK, DONACIONES_RECIBIDAS, UNIDADES_RECIBIDAS)) {
      sumar(nombre, 0, Tags.empty());
    }
    sumar(DONACIONES_RECHAZADAS, 0, Tags.of("motivo", MOTIVO_CAPACIDAD));
    ORIGENES.forEach(origen -> sumar(UNIDADES_ASIGNADAS, 0, Tags.of("origen", origen)));
    DESTINOS.forEach(destino -> sumar(NOTIFICACIONES_FALLIDAS, 0, Tags.of("destino", destino)));

    if (depositoRepository != null) {
      indicador(UNIDADES_EN_STOCK, Foto::unidadesEnStock);
      indicador(CAPACIDAD_TOTAL, Foto::capacidadTotal);
      indicador(OCUPACION_MAXIMA, Foto::ocupacionMaxima);
    }
  }

  // ---------------- Contadores ----------------

  public void donacionRecibida(int unidades) {
    sumar(DONACIONES_RECIBIDAS, 1, Tags.empty());
    sumar(UNIDADES_RECIBIDAS, unidades, Tags.empty());
  }

  public void donacionRechazadaPorCapacidad() {
    sumar(DONACIONES_RECHAZADAS, 1, Tags.of("motivo", MOTIVO_CAPACIDAD));
  }

  public void asignacionPorMatchmaking(int unidades) {
    sumar(ASIGNACIONES_MATCHMAKING, 1, Tags.empty());
    sumar(UNIDADES_ASIGNADAS, unidades, Tags.of("origen", "matchmaking"));
  }

  public void asignacionPorSolicitud(int unidades) {
    sumar(ASIGNACIONES_SOLICITUD, 1, Tags.empty());
    sumar(UNIDADES_ASIGNADAS, unidades, Tags.of("origen", "solicitud_donadores"));
  }

  public void paqueteGuardadoEnStock() {
    sumar(PAQUETES_EN_STOCK, 1, Tags.empty());
  }

  public void entregaCompletada(int unidades) {
    sumar(ENTREGAS_COMPLETADAS, 1, Tags.empty());
    sumar(UNIDADES_ENTREGADAS, unidades, Tags.empty());
  }

  /** Un aviso a otro módulo que no llegó: la entrega siguió, pero el otro lado quedó desactualizado. */
  public void notificacionFallida(String destino) {
    sumar(NOTIFICACIONES_FALLIDAS, 1, Tags.of("destino", destino));
  }

  private void sumar(String nombre, double cantidad, Tags etiquetas) {
    if (meterRegistry != null) {
      meterRegistry.counter(nombre, etiquetas.and("modulo", "logistica")).increment(cantidad);
    }
  }

  // ---------------- Indicadores de estado ----------------

  private void indicador(String nombre, ToDoubleFunction<Foto> valor) {
    Gauge.builder(nombre, this, m -> valor.applyAsDouble(m.foto()))
            .tag("modulo", "logistica")
            .register(meterRegistry);
  }

  private Foto foto() {
    Foto actual = ultimaFoto;
    if (actual != null
            && Duration.between(actual.tomadaEn(), Instant.now()).getSeconds() < vigenciaSegundos) {
      return actual;
    }
    Foto nueva = tomarFoto();
    ultimaFoto = nueva;
    return nueva;
  }

  /**
   * Un depósito sin capacidad definida no tiene límite, así que no aporta a la capacidad total
   * ni a la ocupación: no hay contra qué compararlo.
   */
  private Foto tomarFoto() {
    double unidades = 0;
    double capacidad = 0;
    double ocupacionMaxima = 0;
    for (Deposito deposito : depositoRepository.findAll()) {
      int ocupadas = deposito.unidadesOcupadas();
      unidades += ocupadas;
      Integer maxima = deposito.getCapacidadMaxima();
      if (maxima != null && maxima > 0) {
        capacidad += maxima;
        ocupacionMaxima = Math.max(ocupacionMaxima, (double) ocupadas / maxima);
      }
    }
    return new Foto(unidades, capacidad, ocupacionMaxima, Instant.now());
  }
}
