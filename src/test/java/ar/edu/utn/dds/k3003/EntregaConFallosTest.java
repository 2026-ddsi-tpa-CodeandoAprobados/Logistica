package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.service.EntregaService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Cuando Entidades o Donaciones no responden al reportar una entrega, la entrega se completa
 * igual. Es una decisión consciente: frenarla dejaría trabado un flujo de la demostración por un
 * fallo ajeno. Lo que no puede pasar es que el fallo quede invisible, y de eso se ocupan estos tests.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class EntregaConFallosTest {

  @Autowired private Fachada fachada;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  private ListAppender<ILoggingEvent> eventos;
  private Logger loggerDelServicio;

  @BeforeEach
  void prepararEscenario() {
    fachada.limpiarBaseDeDatos();
    loggerDelServicio = (Logger) LoggerFactory.getLogger(EntregaService.class);
    eventos = new ListAppender<>();
    eventos.start();
    loggerDelServicio.addAppender(eventos);
  }

  @AfterEach
  void soltarLogs() {
    loggerDelServicio.detachAppender(eventos);
  }

  /** Deja un paquete asignado a la necesidad "nec-1" y devuelve su asignación. */
  private AsignacionDTO asignacionPendiente() {
    DepositoDTO deposito = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", 100, new ArrayList<>()));
    when(entidadesClient.getAllNecesidadesDeUnProducto("arroz")).thenReturn(List.of(
            new NecesidadMaterialDTO("nec-1", "entidad-1", 5, "arroz", 10, "arroz",
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
    fachada.gestionarDonacion(new DonacionDTO("don-1", "donador-1", deposito.id(), "donación",
            List.of(new DetalleProductoDTO("d1", "arroz", 10)), null, null));
    return fachada.buscarTodasLasAsignaciones().get(0);
  }

  private List<ILoggingEvent> advertencias() {
    return eventos.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
  }

  @Test
  @DisplayName("Si Entidades falla, la entrega se completa y el fallo queda en el log con la necesidad")
  void fallaEntidades() {
    AsignacionDTO asignacion = asignacionPendiente();
    doThrow(new RuntimeException("503 de Entidades"))
            .when(entidadesClient).postSatisfacerNecesidad(eq("nec-1"), any(Map.class));

    fachada.reportarEntrega(fachada.buscarPaquetePorID(asignacion.paqueteID()));

    assertThat(fachada.buscarAsignacionPorID(asignacion.id()).estado())
            .isEqualTo(EstadoAsginacionEnum.COMPLETADA);
    assertThat(advertencias()).hasSize(1);
    assertThat(advertencias().get(0).getFormattedMessage())
            .contains("nec-1").contains("Entidades").contains("503 de Entidades");
  }

  @Test
  @DisplayName("Si Donaciones falla, la entrega se completa y el fallo queda en el log con la donación")
  void fallaDonaciones() {
    AsignacionDTO asignacion = asignacionPendiente();
    doThrow(new RuntimeException("404 de Donaciones"))
            .when(donacionesClient).actualizarEstadoDonacion(eq("don-1"), any(EstadoDonacionRequest.class));

    fachada.reportarEntrega(fachada.buscarPaquetePorID(asignacion.paqueteID()));

    assertThat(fachada.buscarAsignacionPorID(asignacion.id()).estado())
            .isEqualTo(EstadoAsginacionEnum.COMPLETADA);
    assertThat(advertencias()).hasSize(1);
    assertThat(advertencias().get(0).getFormattedMessage())
            .contains("don-1").contains("Donaciones").contains("404 de Donaciones");
  }

  @Test
  @DisplayName("Si todo responde bien no hay advertencias, y el cierre informa el resultado de cada aviso")
  void todoBien() {
    AsignacionDTO asignacion = asignacionPendiente();

    fachada.reportarEntrega(fachada.buscarPaquetePorID(asignacion.paqueteID()));

    assertThat(advertencias()).isEmpty();
    assertThat(eventos.list.get(eventos.list.size() - 1).getFormattedMessage())
            .contains("completada").contains("Necesidad satisfecha: true").contains("Donación aceptada: true");
  }
}
