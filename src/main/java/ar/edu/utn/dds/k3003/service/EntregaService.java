package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.model.Asignacion;
import java.util.HashMap;
import java.util.Map;
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

    satisfacerNecesidad(asignacion.getNecesidadID(), paquete.cantidad());
    marcarDonacionAceptada(paquete.donacionID());

    asignacionService.completar(asignacion);
    metricas.entregaCompletada();
  }

  private void satisfacerNecesidad(String necesidadID, Integer cantidad) {
    if (entidadesClient == null) {
      return;
    }
    try {
      Map<String, Integer> cuerpo = new HashMap<>();
      cuerpo.put("cantidad", cantidad);
      entidadesClient.postSatisfacerNecesidad(necesidadID, cuerpo);
    } catch (Exception e) {
      System.err.println("Error al satisfacer necesidad: " + e.getMessage());
    }
  }

  private void marcarDonacionAceptada(String donacionID) {
    if (donacionesClient == null) {
      return;
    }
    try {
      donacionesClient.actualizarEstadoDonacion(donacionID,
              new EstadoDonacionRequest(String.valueOf(EstadoDonacionEnum.ACEPTADA)));
    } catch (Exception e) {
      System.err.println("Error al actualizar estado en Donaciones: " + e.getMessage());
    }
  }
}
