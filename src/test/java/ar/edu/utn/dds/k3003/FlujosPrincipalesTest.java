package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
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
 * Fija el comportamiento de los flujos principales con los módulos vecinos simulados.
 *
 * <p>Son tests de caracterización: describen lo que el módulo hace hoy, para poder repartir
 * responsabilidades entre servicios sin cambiar el comportamiento observable.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class FlujosPrincipalesTest {

  private static final String PRODUCTO = "arroz";

  @Autowired private Fachada fachada;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  @BeforeEach
  void limpiar() {
    fachada.limpiarBaseDeDatos();
  }

  private String crearDeposito(Integer capacidad) {
    DepositoDTO creado = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", capacidad, new ArrayList<>()));
    return creado.id();
  }

  private DonacionDTO donacionDe(String depositoID, String donacionID, int cantidad) {
    return new DonacionDTO(donacionID, "donador-1", depositoID, "una donación",
            List.of(new DetalleProductoDTO("d1", PRODUCTO, cantidad)), null, null);
  }

  private NecesidadMaterialDTO necesidad(String id, int objetivo, TipoNecesidadMaterialEnum tipo) {
    return new NecesidadMaterialDTO(id, "entidad-1", 5, "hace falta arroz", objetivo, PRODUCTO, tipo);
  }

  @Test
  @DisplayName("Sin necesidades, la donación entera va al stock del depósito")
  void donacionSinNecesidadesVaAStock() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO)).thenReturn(List.of());

    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 30));

    assertThat(fachada.stockDisponible(PRODUCTO)).isEqualTo(30);
    assertThat(fachada.buscarTodasLasAsignaciones()).isEmpty();
  }

  @Test
  @DisplayName("Con una necesidad, se asigna lo que falta y el sobrante queda en stock")
  void donacionConNecesidadAsignaYGuardaSobrante() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO))
            .thenReturn(List.of(necesidad("nec-1", 10, TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 30));

    List<AsignacionDTO> asignaciones = fachada.buscarTodasLasAsignaciones();
    assertThat(asignaciones).hasSize(1);
    assertThat(asignaciones.get(0).origen()).isEqualTo(OrigenAsignacionEnum.MATCHMAKING);
    assertThat(asignaciones.get(0).estado()).isEqualTo(EstadoAsginacionEnum.ASIGNADA);
    assertThat(fachada.stockDisponible(PRODUCTO)).isEqualTo(20);
  }

  @Test
  @DisplayName("Una necesidad recurrente que no se puede cubrir entera manda todo a stock")
  void recurrenteNoAdmiteParcial() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO))
            .thenReturn(List.of(necesidad("nec-1", 50, TipoNecesidadMaterialEnum.RECURRENTE)));

    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 30));

    assertThat(fachada.buscarTodasLasAsignaciones()).isEmpty();
    assertThat(fachada.stockDisponible(PRODUCTO)).isEqualTo(30);
  }

  @Test
  @DisplayName("Asignar desde stock crea una asignación por cada donación de origen")
  void asignarDesdeStockSeparaPorDonacion() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO)).thenReturn(List.of());
    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 10));
    fachada.gestionarDonacion(donacionDe(depositoID, "don-2", 10));

    List<AsignacionDTO> creadas = fachada.asignarDesdeStock(PRODUCTO, 15, "nec-9");

    assertThat(creadas).hasSize(2);
    assertThat(creadas).allMatch(a -> a.origen() == OrigenAsignacionEnum.SOLICITUD_DONADORES);
    assertThat(fachada.stockDisponible(PRODUCTO)).isEqualTo(5);
  }

  @Test
  @DisplayName("Logística clampea: si se pide más de lo que hay, asigna lo disponible")
  void asignarDesdeStockClampea() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO)).thenReturn(List.of());
    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 8));

    fachada.asignarDesdeStock(PRODUCTO, 50, "nec-9");

    assertThat(fachada.stockDisponible(PRODUCTO)).isZero();
    assertThat(fachada.buscarTodosLosPaquetes()).anyMatch(p -> p.cantidad() == 8);
  }

  @Test
  @DisplayName("Sin stock del producto, asignar desde stock devuelve lista vacía y no falla")
  void asignarDesdeStockSinStock() {
    assertThat(fachada.asignarDesdeStock(PRODUCTO, 5, "nec-9")).isEmpty();
  }

  @Test
  @DisplayName("Reportar la entrega satisface la necesidad, avisa a Donaciones y completa la asignación")
  void reportarEntregaNotificaYCompleta() {
    String depositoID = crearDeposito(100);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO))
            .thenReturn(List.of(necesidad("nec-1", 10, TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 10));

    AsignacionDTO asignacion = fachada.buscarTodasLasAsignaciones().get(0);
    PaqueteDTO paquete = fachada.buscarPaquetePorID(asignacion.paqueteID());

    fachada.reportarEntrega(paquete);

    verify(entidadesClient).postSatisfacerNecesidad(eq("nec-1"), any(Map.class));
    verify(donacionesClient).actualizarEstadoDonacion(eq("don-1"), any(EstadoDonacionRequest.class));
    assertThat(fachada.buscarAsignacionPorID(asignacion.id()).estado())
            .isEqualTo(EstadoAsginacionEnum.COMPLETADA);
  }

  @Test
  @DisplayName("El depósito acepta hasta su capacidad y no más")
  void capacidadDelDeposito() {
    String depositoID = crearDeposito(10);
    when(entidadesClient.getAllNecesidadesDeUnProducto(PRODUCTO)).thenReturn(List.of());

    fachada.gestionarDonacion(donacionDe(depositoID, "don-1", 10));

    assertThat(fachada.stockDisponible(PRODUCTO)).isEqualTo(10);
    assertThat(fachada.buscarDepositoPorID(depositoID).stockActual()).hasSize(1);
  }
}
