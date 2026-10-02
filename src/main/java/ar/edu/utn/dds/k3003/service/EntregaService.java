package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.exceptions.OperacionNoPermitidaException;
import ar.edu.utn.dds.k3003.model.Asignacion;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrega de un paquete a la entidad beneficiaria.
 *
 * <p>Es el flujo que cierra el círculo con los otros dos módulos: avisa a Entidades que la
 * necesidad se satisfizo y a Donaciones que la donación fue aceptada. Las dos notificaciones
 * se hacen de la mejor manera posible: si una falla, la entrega se completa igual.
 */
@Service
@Transactional
public class EntregaService {

  private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

  private final AsignacionService asignacionService;
  private final MetricasLogistica metricas;

  @Autowired(required = false)
  private EntidadesClient entidadesClient;

  @Autowired(required = false)
  private DonacionesClient donacionesClient;

  public EntregaService(AsignacionService asignacionService, MetricasLogistica metricas) {
    this.asignacionService = asignacionService;
    this.metricas = metricas;
  }

  public void reportar(PaqueteDTO paquete) {
    Asignacion asignacion = asignacionService.obtenerPorPaqueteID(paquete.id());
    verificarQueNoSeEntregoYa(asignacion, paquete);
    log.info("Entrega del paquete {}: donación {}, necesidad {}, {} unidades",
            paquete.id(), paquete.donacionID(), asignacion.getNecesidadID(), paquete.cantidad());

    boolean necesidadSatisfecha = satisfacerNecesidad(asignacion.getNecesidadID(), paquete.cantidad());
    boolean donacionActualizada = marcarDonacionAceptada(paquete.donacionID());

    asignacionService.completar(asignacion);
    metricas.entregaCompletada();
    log.info("Entrega del paquete {} completada. Necesidad satisfecha: {}. Donación aceptada: {}",
            paquete.id(), necesidadSatisfecha, donacionActualizada);
  }

  /**
   * Una asignación pasa de ASIGNADA a COMPLETADA una sola vez. Aceptar una segunda entrega
   * volvería a satisfacer la necesidad en Entidades y la contaría dos veces.
   */
  private void verificarQueNoSeEntregoYa(Asignacion asignacion, PaqueteDTO paquete) {
    if (asignacion.getEstado() == EstadoAsginacionEnum.COMPLETADA) {
      log.warn("Entrega repetida rechazada: el paquete {} ya fue entregado", paquete.id());
      throw new OperacionNoPermitidaException(
              "La entrega del paquete " + paquete.id() + " ya fue reportada");
    }
  }

  /**
   * Avisa a Entidades. Un fallo no frena la entrega, pero deja un WARN con lo necesario para
   * repararlo a mano: sin él, la asignación figuraría completada con la necesidad todavía abierta.
   */
  private boolean satisfacerNecesidad(String necesidadID, Integer cantidad) {
    if (entidadesClient == null) {
      return false;
    }
    try {
      Map<String, Integer> cuerpo = new HashMap<>();
      cuerpo.put("cantidad", cantidad);
      entidadesClient.postSatisfacerNecesidad(necesidadID, cuerpo);
      return true;
    } catch (Exception e) {
      log.warn("No se pudo satisfacer la necesidad {} en Entidades ({} unidades): {}",
              necesidadID, cantidad, e.getMessage());
      return false;
    }
  }

  /** Avisa a Donaciones. Misma política: un fallo se registra y la entrega sigue. */
  private boolean marcarDonacionAceptada(String donacionID) {
    if (donacionesClient == null) {
      return false;
    }
    try {
      donacionesClient.actualizarEstadoDonacion(donacionID,
              new EstadoDonacionRequest(String.valueOf(EstadoDonacionEnum.ACEPTADA)));
      return true;
    } catch (Exception e) {
      log.warn("No se pudo marcar la donación {} como ACEPTADA en Donaciones: {}",
              donacionID, e.getMessage());
      return false;
    }
  }
}
