package ar.edu.utn.dds.k3003.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Contadores de negocio del módulo, en un solo lugar para que los servicios no dependan
 * de Micrometer ni tengan que verificar si el registro está disponible.
 *
 * <p>El registro es opcional: si la aplicación corre sin exportador de métricas, los métodos
 * no hacen nada en vez de fallar.
 */
@Component
public class MetricasLogistica {

  @Autowired(required = false)
  private MeterRegistry meterRegistry;

  private Counter entregasCompletadas;
  private Counter asignacionesMatchmaking;
  private Counter asignacionesSolicitud;
  private Counter paquetesEnStock;

  @PostConstruct
  public void inicializar() {
    if (meterRegistry == null) {
      return;
    }
    this.entregasCompletadas = contador("logistica.entregas_completadas");
    this.asignacionesMatchmaking = contador("logistica.asignaciones_matchmaking");
    this.asignacionesSolicitud = contador("logistica.asignaciones_solicitud_donadores");
    this.paquetesEnStock = contador("logistica.paquetes_en_stock");
  }

  private Counter contador(String nombre) {
    return Counter.builder(nombre).tag("modulo", "logistica").register(meterRegistry);
  }

  public void entregaCompletada() {
    incrementar(entregasCompletadas);
  }

  public void asignacionPorMatchmaking() {
    incrementar(asignacionesMatchmaking);
  }

  public void asignacionPorSolicitud() {
    incrementar(asignacionesSolicitud);
  }

  public void paqueteGuardadoEnStock() {
    incrementar(paquetesEnStock);
  }

  private void incrementar(Counter contador) {
    if (contador != null) {
      contador.increment();
    }
  }
}
