package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.exceptions.OperacionNoPermitidaException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Una asignación pasa de ASIGNADA a COMPLETADA una sola vez. Los enunciados piden trazabilidad de
 * los estados de las asignaciones, y aceptar una segunda entrega volvería a satisfacer la necesidad.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class EntregaRepetidaTest {

  @Autowired private Fachada fachada;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  @BeforeEach
  void limpiar() {
    fachada.limpiarBaseDeDatos();
  }

  private PaqueteDTO paqueteAsignado() {
    DepositoDTO deposito = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", 100, new ArrayList<>()));
    when(entidadesClient.getAllNecesidadesDeUnProducto("arroz")).thenReturn(List.of(
            new NecesidadMaterialDTO("nec-1", "entidad-1", 5, "arroz", 10, "arroz",
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
    fachada.gestionarDonacion(new DonacionDTO("don-1", "donador-1", deposito.id(), "donación",
            List.of(new DetalleProductoDTO("d1", "arroz", 10)), null, null));
    AsignacionDTO asignacion = fachada.buscarTodasLasAsignaciones().get(0);
    return fachada.buscarPaquetePorID(asignacion.paqueteID());
  }

  @Test
  @DisplayName("Reportar dos veces la misma entrega rechaza la segunda y no vuelve a avisar a nadie")
  void segundaEntregaRechazada() {
    PaqueteDTO paquete = paqueteAsignado();

    fachada.reportarEntrega(paquete);

    assertThatThrownBy(() -> fachada.reportarEntrega(paquete))
            .isInstanceOf(OperacionNoPermitidaException.class)
            .hasMessage("La entrega del paquete " + paquete.id() + " ya fue reportada");

    // La necesidad se satisfizo una sola vez y la donación se actualizó una sola vez.
    verify(entidadesClient, times(1)).postSatisfacerNecesidad(any(String.class), any(Map.class));
    verify(donacionesClient, times(1)).actualizarEstadoDonacion(any(String.class), any(EstadoDonacionRequest.class));
    assertThat(fachada.buscarAsignacionPorPaqueteID(paquete.id()).estado())
            .isEqualTo(EstadoAsginacionEnum.COMPLETADA);
  }

  @Test
  @DisplayName("La primera entrega sigue funcionando igual")
  void primeraEntregaSigueIgual() {
    PaqueteDTO paquete = paqueteAsignado();

    fachada.reportarEntrega(paquete);

    verify(entidadesClient, times(1)).postSatisfacerNecesidad(any(String.class), any(Map.class));
    assertThat(fachada.buscarAsignacionPorPaqueteID(paquete.id()).estado())
            .isEqualTo(EstadoAsginacionEnum.COMPLETADA);
  }
}
