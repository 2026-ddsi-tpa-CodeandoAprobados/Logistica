package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.exceptions.CapacidadInsuficienteException;
import ar.edu.utn.dds.k3003.service.MetricasLogistica;
import io.micrometer.core.instrument.MeterRegistry;
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
 * Las métricas de dominio tienen que contar de verdad: sirven de base a las alarmas. Si un
 * contador dejara de incrementarse, la alarma que depende de él no saltaría nunca y nadie lo notaría.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class MetricasDeDominioTest {

  @Autowired private Fachada fachada;
  @Autowired private MeterRegistry registro;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  private String depositoID;

  @BeforeEach
  void prepararDeposito() {
    fachada.limpiarBaseDeDatos();
    depositoID = nuevoDeposito(100);
  }

  private String nuevoDeposito(int capacidad) {
    return fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", capacidad, new ArrayList<>())).id();
  }

  private DonacionDTO donacion(String id, String deposito, String producto, int cantidad) {
    return new DonacionDTO(id, "donador-1", deposito, "donación",
            List.of(new DetalleProductoDTO("d-" + id, producto, cantidad)), null, null);
  }

  /** Valor de un contador; -1 si no existe, para que un contador ausente falle el test a la vista. */
  private double contador(String nombre, String... etiquetas) {
    var c = registro.find(nombre).tags(etiquetas).counter();
    return c == null ? -1 : c.count();
  }

  private double indicador(String nombre) {
    var g = registro.find(nombre).gauge();
    return g == null ? Double.NaN : g.value();
  }

  @Test
  @DisplayName("Guardar stock incrementa el contador de paquetes en stock")
  void guardarStockCuenta() {
    double antes = contador(MetricasLogistica.PAQUETES_EN_STOCK);

    fachada.guardarSobranteEnStock(depositoID, "don-1", "arroz", 5);
    fachada.guardarSobranteEnStock(depositoID, "don-2", "arroz", 3);

    assertThat(antes).as("el contador existe en el registro").isGreaterThanOrEqualTo(0);
    assertThat(contador(MetricasLogistica.PAQUETES_EN_STOCK) - antes).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Una donación aceptada suma una donación y sus unidades, no sólo un evento")
  void donacionRecibidaCuentaUnidades() {
    double donaciones = contador(MetricasLogistica.DONACIONES_RECIBIDAS);
    double unidades = contador(MetricasLogistica.UNIDADES_RECIBIDAS);

    fachada.gestionarDonacion(donacion("don-1", depositoID, "arroz", 30));

    assertThat(contador(MetricasLogistica.DONACIONES_RECIBIDAS) - donaciones).isEqualTo(1.0);
    assertThat(contador(MetricasLogistica.UNIDADES_RECIBIDAS) - unidades).isEqualTo(30.0);
  }

  @Test
  @DisplayName("Una donación que no entra se cuenta como rechazada por capacidad y no como recibida")
  void rechazoPorCapacidadCuenta() {
    double rechazadas = contador(MetricasLogistica.DONACIONES_RECHAZADAS, "motivo", "capacidad");
    double recibidas = contador(MetricasLogistica.DONACIONES_RECIBIDAS);

    assertThatThrownBy(() -> fachada.gestionarDonacion(donacion("don-1", depositoID, "arroz", 150)))
            .isInstanceOf(CapacidadInsuficienteException.class);

    assertThat(contador(MetricasLogistica.DONACIONES_RECHAZADAS, "motivo", "capacidad") - rechazadas)
            .isEqualTo(1.0);
    assertThat(contador(MetricasLogistica.DONACIONES_RECIBIDAS) - recibidas).isEqualTo(0.0);
  }

  @Test
  @DisplayName("El stock, la capacidad y la ocupación reflejan el estado de los depósitos")
  void indicadoresDeEstado() {
    String chico = nuevoDeposito(10);
    fachada.guardarSobranteEnStock(depositoID, "don-1", "arroz", 40);
    fachada.guardarSobranteEnStock(chico, "don-2", "fideos", 9);

    assertThat(indicador(MetricasLogistica.UNIDADES_EN_STOCK)).isEqualTo(49.0);
    assertThat(indicador(MetricasLogistica.CAPACIDAD_TOTAL)).isEqualTo(110.0);
    assertThat(indicador(MetricasLogistica.OCUPACION_MAXIMA)).isEqualTo(0.9);
  }

  @Test
  @DisplayName("Sin depósitos con capacidad, la ocupación es 0 y no se divide por cero")
  void ocupacionSinCapacidad() {
    fachada.limpiarBaseDeDatos();
    fachada.agregarDeposito(new DepositoDTO(null, null, "Sin tope", "Medrano 951", null, new ArrayList<>()));

    assertThat(indicador(MetricasLogistica.OCUPACION_MAXIMA)).isEqualTo(0.0);
    assertThat(indicador(MetricasLogistica.CAPACIDAD_TOTAL)).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Las unidades asignadas y entregadas se cuentan por unidad, con su origen")
  void asignacionYEntregaCuentanUnidades() {
    when(entidadesClient.getAllNecesidadesDeUnProducto("arroz")).thenReturn(List.of(
            new NecesidadMaterialDTO("nec-1", "entidad-1", 5, "arroz", 10, "arroz",
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
    double asignadas = contador(MetricasLogistica.UNIDADES_ASIGNADAS, "origen", "matchmaking");
    double entregadas = contador(MetricasLogistica.UNIDADES_ENTREGADAS);
    double completadas = contador(MetricasLogistica.ENTREGAS_COMPLETADAS);

    fachada.gestionarDonacion(donacion("don-1", depositoID, "arroz", 10));
    AsignacionDTO asignacion = fachada.buscarTodasLasAsignaciones().get(0);
    fachada.reportarEntrega(fachada.buscarPaquetePorID(asignacion.paqueteID()));

    assertThat(contador(MetricasLogistica.UNIDADES_ASIGNADAS, "origen", "matchmaking") - asignadas)
            .isEqualTo(10.0);
    assertThat(contador(MetricasLogistica.UNIDADES_ENTREGADAS) - entregadas).isEqualTo(10.0);
    assertThat(contador(MetricasLogistica.ENTREGAS_COMPLETADAS) - completadas).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Un aviso que no llega a Entidades o a Donaciones se cuenta por destino")
  void notificacionFallidaCuenta() {
    when(entidadesClient.getAllNecesidadesDeUnProducto("arroz")).thenReturn(List.of(
            new NecesidadMaterialDTO("nec-1", "entidad-1", 5, "arroz", 10, "arroz",
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
    fachada.gestionarDonacion(donacion("don-1", depositoID, "arroz", 10));
    AsignacionDTO asignacion = fachada.buscarTodasLasAsignaciones().get(0);
    doThrow(new RuntimeException("503")).when(entidadesClient)
            .postSatisfacerNecesidad(eq("nec-1"), any(Map.class));
    doThrow(new RuntimeException("404")).when(donacionesClient)
            .actualizarEstadoDonacion(eq("don-1"), any(EstadoDonacionRequest.class));
    double aEntidades = contador(MetricasLogistica.NOTIFICACIONES_FALLIDAS, "destino", "entidades");
    double aDonaciones = contador(MetricasLogistica.NOTIFICACIONES_FALLIDAS, "destino", "donaciones");

    fachada.reportarEntrega(fachada.buscarPaquetePorID(asignacion.paqueteID()));

    assertThat(contador(MetricasLogistica.NOTIFICACIONES_FALLIDAS, "destino", "entidades") - aEntidades)
            .isEqualTo(1.0);
    assertThat(contador(MetricasLogistica.NOTIFICACIONES_FALLIDAS, "destino", "donaciones") - aDonaciones)
            .isEqualTo(1.0);
  }
}
