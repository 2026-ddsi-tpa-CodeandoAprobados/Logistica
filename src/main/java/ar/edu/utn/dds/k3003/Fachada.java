package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Matchmaker;
import ar.edu.utn.dds.k3003.service.AsignacionService;
import ar.edu.utn.dds.k3003.service.DepositoService;
import ar.edu.utn.dds.k3003.service.EntregaService;
import ar.edu.utn.dds.k3003.service.MantenimientoService;
import ar.edu.utn.dds.k3003.service.PaqueteService;
import ar.edu.utn.dds.k3003.service.RecepcionDonacionService;
import ar.edu.utn.dds.k3003.service.StockService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el contrato {@code FachadaLogistica} de la cátedra y
 * suma las operaciones propias que consumen el controller, el Worker y los otros módulos.
 *
 * <p>No tiene lógica de negocio. Cada operación delega en el servicio dueño de esa
 * responsabilidad, y la transacción abarca la operación completa para que una recepción de
 * donación, que hace varias escrituras, siga siendo atómica.
 *
 * <ul>
 *   <li>{@code RecepcionDonacionService} — alta de donación y matchmaking.
 *   <li>{@code StockService} — stock de los depósitos y asignación a pedido de Donadores.
 *   <li>{@code AsignacionService} — altas y consultas de asignaciones.
 *   <li>{@code EntregaService} — reporte de entrega y avisos a los otros módulos.
 *   <li>{@code DepositoService}, {@code PaqueteService} — ABM y consultas.
 * </ul>
 */
@Service
@Transactional
public class Fachada implements FachadaLogistica {

  private final RecepcionDonacionService recepcionDonacionService;
  private final DepositoService depositoService;
  private final StockService stockService;
  private final AsignacionService asignacionService;
  private final PaqueteService paqueteService;
  private final EntregaService entregaService;
  private final MantenimientoService mantenimientoService;
  private final Matchmaker matchmaker;

  public Fachada(RecepcionDonacionService recepcionDonacionService,
                 DepositoService depositoService,
                 StockService stockService,
                 AsignacionService asignacionService,
                 PaqueteService paqueteService,
                 EntregaService entregaService,
                 MantenimientoService mantenimientoService,
                 Matchmaker matchmaker) {
    this.recepcionDonacionService = recepcionDonacionService;
    this.depositoService = depositoService;
    this.stockService = stockService;
    this.asignacionService = asignacionService;
    this.paqueteService = paqueteService;
    this.entregaService = entregaService;
    this.mantenimientoService = mantenimientoService;
    this.matchmaker = matchmaker;
  }

  // ---------------- Donaciones ----------------

  @Override
  public DepositoDTO gestionarDonacion(DonacionDTO donacion) {
    return recepcionDonacionService.recibir(donacion);
  }

  /** Entrega 4 - Parte B. Alta de asignación que pide el Worker, que no tiene base de datos. */
  public AsignacionDTO altaAsignacionDesdeWorker(String donacionID, String productoID,
                                                 int cantidad, String necesidadID) {
    return asignacionService.asignarPorMatchmaking(donacionID, productoID, cantidad, necesidadID);
  }

  /** Entrega 4 - Parte B. Sobrante que el Worker manda a guardar. */
  public DepositoDTO guardarSobranteEnStock(String depositoID, String donacionID,
                                            String productoID, int cantidad) {
    return stockService.guardarSobrante(depositoID, donacionID, productoID, cantidad);
  }

  // ---------------- Stock ----------------

  public int stockDisponible(String productoID) {
    return stockService.disponible(productoID);
  }

  public List<AsignacionDTO> asignarDesdeStock(String productoID, Integer cantidad,
                                               String necesidadID) {
    return stockService.asignarDesdeStock(productoID, cantidad, necesidadID);
  }

  // ---------------- Entregas ----------------

  @Override
  public void reportarEntrega(PaqueteDTO paquete) {
    entregaService.reportar(paquete);
  }

  // ---------------- Depósitos ----------------

  @Override
  public DepositoDTO agregarDeposito(DepositoDTO deposito) {
    return depositoService.crear(deposito);
  }

  public DepositoDTO modificarDeposito(String depositoID, DepositoDTO deposito) {
    return depositoService.modificar(depositoID, deposito);
  }

  @Override
  public DepositoDTO buscarDepositoPorID(String depositoID) {
    return depositoService.buscarPorID(depositoID);
  }

  @Override
  public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum algoritmo) {
    depositoService.configurarAlgoritmo(depositoID, algoritmo);
  }

  public List<DepositoDTO> buscarTodosLosDepositos() {
    return depositoService.buscarTodos();
  }

  public DepositoDTO eliminarDeposito(String depositoID) {
    return depositoService.eliminar(depositoID);
  }

  // ---------------- Paquetes y asignaciones ----------------

  public PaqueteDTO buscarPaquetePorID(String paqueteID) {
    return paqueteService.buscarPorID(paqueteID);
  }

  public List<PaqueteDTO> buscarTodosLosPaquetes() {
    return paqueteService.buscarTodos();
  }

  public List<AsignacionDTO> buscarTodasLasAsignaciones() {
    return asignacionService.buscarTodas();
  }

  public AsignacionDTO buscarAsignacionPorID(String asignacionID) {
    return asignacionService.buscarPorID(asignacionID);
  }

  @Override
  public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) {
    return asignacionService.buscarPorPaqueteID(paqueteID);
  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paquete,
                                           List<NecesidadMaterialDTO> necesidades) {
    Deposito deposito = depositoService.obtener(depositoID);
    NecesidadMaterialDTO elegida =
            matchmaker.calcularMejorOpcion(necesidades, deposito.getAlgoritmo(), paquete.cantidad());
    return asignacionService.asignarPaqueteExistente(paquete.id(), elegida.id());
  }

  // ---------------- Mantenimiento ----------------

  public void limpiarBaseDeDatos() {
    mantenimientoService.limpiarBaseDeDatos();
  }

  // ---------------- Contrato de la cátedra ----------------
  // La integración con los otros módulos es por HTTP (Feign), no por referencias en memoria,
  // así que estos dos setters existen sólo para cumplir la interfaz.

  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachada) {
    // sin efecto: ver EntidadesClient
  }

  @Override
  public void setFachadaDonaciones(FachadaDonaciones fachada) {
    // sin efecto: ver DonacionesClient
  }
}
