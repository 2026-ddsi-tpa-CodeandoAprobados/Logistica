package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.messaging.DonacionMessage;
import ar.edu.utn.dds.k3003.messaging.DonacionPublisher;
import feign.FeignException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class Fachada implements FachadaLogistica {

  @Autowired private PaqueteRepository paqueteRepository;
  @Autowired private DepositoRepository depositoRepository;
  @Autowired private AsignacionRepository asignacionRepository;
  @Autowired private LogisticaDataMapper mapper;
  @Autowired private Matchmaker matchmaker;
  @Autowired(required = false) private MeterRegistry meterRegistry;
  @Autowired(required = false) private EntidadesClient entidadesClient;
  @Autowired(required = false) private DonacionesClient donacionesClient;
  @Autowired(required = false) private DonacionPublisher donacionPublisher;

  private Counter entregasCompletadasCounter;
  private Counter asignacionesMatchmakingCounter;
  private Counter asignacionesSolicitudCounter;
  private Counter paquetesEnStockCounter;

  public Fachada() {}

  @PostConstruct
  public void initMetrics() {
    if (meterRegistry != null) {
      this.entregasCompletadasCounter = Counter.builder("logistica.entregas_completadas")
              .tag("modulo", "logistica")
              .register(meterRegistry);
      this.asignacionesMatchmakingCounter = Counter.builder("logistica.asignaciones_matchmaking")
              .tag("modulo", "logistica")
              .register(meterRegistry);
      this.asignacionesSolicitudCounter = Counter.builder("logistica.asignaciones_solicitud_donadores")
              .tag("modulo", "logistica")
              .register(meterRegistry);
      this.paquetesEnStockCounter = Counter.builder("logistica.paquetes_en_stock")
              .tag("modulo", "logistica")
              .register(meterRegistry);
    }
  }

  @Override
  public DepositoDTO gestionarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null || donacionDTO.detallesProductosDTO() == null || donacionDTO.detallesProductosDTO().isEmpty()) {
      throw new RuntimeException("La donación está vacía o es nula");
    }

    if (this.donacionesClient != null) {
      try {
        this.donacionesClient.buscarDonacionPorId(donacionDTO.id());
      } catch (FeignException.NotFound e) {
        // 404 real: la donación no existe en Donaciones.
        throw new NoSuchElementException(
                "La donación " + donacionDTO.id() + " no existe en el módulo de Donaciones");
      } catch (Exception e) {
        // Timeout / 5xx / caída de red: NO es "no existe", es un fallo de integración.
        throw new RuntimeException(
                "No se pudo validar la donación con el módulo de Donaciones: " + e.getMessage(), e);
      }
    }

    Deposito deposito = depositoRepository.findById(Integer.valueOf(donacionDTO.depositoID()))
            .orElseThrow(() -> new NoSuchElementException("Depósito no encontrado"));

    // Entrega 4 - Parte A/B: verificar espacio ANTES de procesar, contra el total de la donación.
    int totalUnidades = 0;
    for (var detalle : donacionDTO.detallesProductosDTO()) {
      if (detalle.cantidadProducto() == null || detalle.cantidadProducto() <= 0) {
        throw new RuntimeException("Cantidad inválida");
      }
      totalUnidades += detalle.cantidadProducto();
    }
    verificarEspacio(deposito, totalUnidades);

    // Entrega 4 - Parte B: si la mensajería está activa, se encola y un Worker asigna async.
    if (donacionPublisher != null) {
      List<DonacionMessage.Item> items = donacionDTO.detallesProductosDTO().stream()
              .map(d -> new DonacionMessage.Item(d.productoID(), d.cantidadProducto()))
              .toList();
      donacionPublisher.publicar(new DonacionMessage(
              donacionDTO.id(), donacionDTO.depositoID(), deposito.getAlgoritmo(), items));
      return mapper.map(deposito);
    }

    //(Parte A): se procesa en el momento.
    for (var detalle : donacionDTO.detallesProductosDTO()) {
      procesarDetalle(deposito, donacionDTO.id(), detalle.productoID(), detalle.cantidadProducto());
    }

    return mapper.map(deposito);
  }

  /**
   * Entrega 4 - Parte B. Alta de asignación solicitada por el Worker (que no tiene BD).
   * Crea el paquete de la porción asignada y la asignación por matchmaking.
   */
  public AsignacionDTO altaAsignacionDesdeWorker(String donacionID, String productoID,
                                                 int cantidad, String necesidadID) {
    Paquete paquete = paqueteRepository.save(new Paquete(donacionID, productoID, cantidad));
    Asignacion asignacion = asignacionRepository.save(
            new Asignacion(String.valueOf(paquete.getId()), necesidadID,
                    OrigenAsignacionEnum.MATCHMAKING));
    if (asignacionesMatchmakingCounter != null) asignacionesMatchmakingCounter.increment();
    return mapper.map(asignacion);
  }

  /** Entrega 4 - Parte B. Guarda en stock el sobrante que le indica el Worker. */
  public DepositoDTO guardarSobranteEnStock(String depositoID, String donacionID,
                                            String productoID, int cantidad) {
    Deposito deposito = depositoRepository.findById(Integer.valueOf(depositoID))
            .orElseThrow(() -> new NoSuchElementException("Depósito no encontrado"));
    guardarEnStock(deposito, donacionID, productoID, cantidad);
    return mapper.map(deposito);
  }

  /**
   * Entrega 4 - Donadores. Stock disponible de un producto sumando el stock de TODOS
   * los depósitos (Donadores consulta por producto, sin conocer depósitos).
   */
  public int stockDisponible(String productoID) {
    return depositoRepository.findAll().stream()
            .flatMap(d -> d.getStock().stream())
            .filter(p -> productoID.equals(p.getProductoID()))
            .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
            .sum();
  }

  /**
   * Entrega 4 - Donadores. Asigna stock a una necesidad por solicitud de "Donadores y
   * Entidades": asigna min(disponible, solicitada), consume ese stock y crea la asignación
   * con origen SOLICITUD_DONADORES (para diferenciarla del matchmaking en Incentivos).
   **/
  public AsignacionDTO asignarDesdeStock(String productoID, Integer cantidadSolicitada, String necesidadID) {
    if (necesidadID == null || necesidadID.isBlank()) {
      throw new RuntimeException("La necesidad a asignar es obligatoria");
    }
    if (productoID == null || productoID.isBlank()) {
      throw new RuntimeException("El producto a asignar es obligatorio");
    }
    if (cantidadSolicitada == null || cantidadSolicitada <= 0) {
      return null; // no se pidió nada asignable -> 204, sin romper al solicitante
    }

    List<Deposito> depositos = depositoRepository.findAll();
    int disponible = depositos.stream()
            .flatMap(d -> d.getStock().stream())
            .filter(p -> productoID.equals(p.getProductoID()))
            .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
            .sum();
    if (disponible <= 0) {
      return null; // no hay stock del producto
    }

    int aAsignar = Math.min(disponible, cantidadSolicitada);
    int restante = aAsignar;
    String donacionOrigen = null;

    // Consume aAsignar unidades del stock (paquete completo o parcial).
    for (Deposito d : depositos) {
      boolean modificado = false;
      Iterator<Paquete> it = d.getStock().iterator();
      while (it.hasNext() && restante > 0) {
        Paquete p = it.next();
        if (!productoID.equals(p.getProductoID())) continue;
        if (donacionOrigen == null) donacionOrigen = p.getDonacionID();
        int c = p.getCantidad() == null ? 0 : p.getCantidad();
        if (c <= restante) {
          restante -= c;
          it.remove(); // consume el paquete completo (orphanRemoval lo borra)
        } else {
          p.setCantidad(c - restante); // consumo parcial
          restante = 0;
        }
        modificado = true;
      }
      if (modificado) depositoRepository.save(d);
      if (restante == 0) break;
    }

    Paquete asignado = paqueteRepository.save(new Paquete(donacionOrigen, productoID, aAsignar));
    Asignacion asignacion = asignacionRepository.save(
            new Asignacion(String.valueOf(asignado.getId()), necesidadID,
                    OrigenAsignacionEnum.SOLICITUD_DONADORES));
    if (asignacionesSolicitudCounter != null) asignacionesSolicitudCounter.increment();
    return mapper.map(asignacion);
  }

  /** Unidades actualmente ocupadas en el stock del depósito (1 unidad por producto). */
  private int ocupadoDe(Deposito deposito) {
    return deposito.getStock().stream()
            .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
            .sum();
  }

  /**
   * Verifica que entren {@code unidadesRequeridas} en el depósito (regla de recepción 1A/1B).
   * Reutilizable por Parte B antes de encolar la donación.
   */
  private void verificarEspacio(Deposito deposito, int unidadesRequeridas) {
    Integer capacidad = deposito.getCapacidadMaxima();
    if (capacidad == null) {
      return; // sin capacidad definida -> sin límite
    }
    int ocupado = ocupadoDe(deposito);
    if (ocupado + unidadesRequeridas > capacidad) {
      throw new RuntimeException("El depósito " + deposito.getId()
              + " no tiene espacio para la donación (capacidad " + capacidad
              + ", ocupado " + ocupado + ", requiere " + unidadesRequeridas + ")");
    }
  }

  /**
   * Entrega 4 - Parte A. Para el producto donado:
   *  - Si no hay necesidades insatisfechas -> las unidades van al stock.
   *  - Si hay necesidades -> el matchmaking elige la mejor; se asigna min(donado, faltante)
   *    (esa porción NO pasa por el stock) y el sobrante se guarda en el stock.
   *  Puede generar 2 paquetes con el mismo donacionID (uno asignado y otro en stock).
   */
  private void procesarDetalle(Deposito deposito, String donacionID, String productoID, int cantidad) {
    List<NecesidadMaterialDTO> necesidades = (entidadesClient != null)
            ? entidadesClient.getAllNecesidadesDeUnProducto(productoID)
            : null;

    if (necesidades == null || necesidades.isEmpty()) {
      guardarEnStock(deposito, donacionID, productoID, cantidad);
      return;
    }

    NecesidadMaterialDTO necesidad;
    try {
      necesidad = matchmaker.calcularMejorOpcion(necesidades, deposito.getAlgoritmo(), cantidad);
    } catch (RuntimeException e) {
      // No hay necesidad elegible (p.ej. sólo recurrentes que no se pueden cubrir por completo) -> stock
      guardarEnStock(deposito, donacionID, productoID, cantidad);
      return;
    }

    int aAsignar = matchmaker.cantidadAAsignar(necesidad, cantidad);
    if (aAsignar <= 0) {
      guardarEnStock(deposito, donacionID, productoID, cantidad);
      return;
    }

    // Porción asignada a la necesidad: paquete separado, no ocupa stock.
    Paquete paqueteAsignado = paqueteRepository.save(new Paquete(donacionID, productoID, aAsignar));
    asignacionRepository.save(
            new Asignacion(String.valueOf(paqueteAsignado.getId()), necesidad.id(),
                    OrigenAsignacionEnum.MATCHMAKING));
    if (asignacionesMatchmakingCounter != null) asignacionesMatchmakingCounter.increment();

    int sobrante = cantidad - aAsignar;
    if (sobrante > 0) {
      guardarEnStock(deposito, donacionID, productoID, sobrante);
    }
  }

  /**
   * Guarda unidades en el stock del depósito respetando la capacidad máxima
   * (1 unidad por producto). La cascada de Deposito -> stock persiste el Paquete
   * y le setea el deposito_id.
   */
  private void guardarEnStock(Deposito deposito, String donacionID, String productoID, int cantidad) {
    verificarEspacio(deposito, cantidad);
    deposito.getStock().add(new Paquete(donacionID, productoID, cantidad));
    depositoRepository.save(deposito);
    if (paquetesEnStockCounter != null) paquetesEnStockCounter.increment();
  }

  public List<AsignacionDTO> buscarTodasLasAsignaciones() {
    return asignacionRepository.findAll().stream().map(mapper::map).toList();
  }

  @Override
  public void reportarEntrega(PaqueteDTO p) {
    Asignacion a = asignacionRepository.findByPaqueteID(p.id()).orElseThrow();

    if (this.entidadesClient != null) {
      try {
        Map<String, Integer> requestBody = new HashMap<>();
        requestBody.put("cantidad", p.cantidad());
        this.entidadesClient.postSatisfacerNecesidad(a.getNecesidadID(), requestBody);
      } catch (Exception ex) {
        System.err.println("Error al satisfacer necesidad: " + ex.getMessage());
      }
    }

    if (this.donacionesClient != null) {
      try {
        EstadoDonacionRequest request =
                new EstadoDonacionRequest(String.valueOf(EstadoDonacionEnum.ACEPTADA));
        this.donacionesClient.actualizarEstadoDonacion(p.donacionID().toString(), request);
      } catch (Exception e) {
        System.err.println("Error al actualizar estado en Donaciones: " + e.getMessage());
      }
    }

    a.setEstado(EstadoAsginacionEnum.COMPLETADA);
    asignacionRepository.save(a);
    if (entregasCompletadasCounter != null) entregasCompletadasCounter.increment();
  }

  @Override public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades f) {}
  @Override public void setFachadaDonaciones(FachadaDonaciones f) {}
  @Override public DepositoDTO agregarDeposito(DepositoDTO dto) { return mapper.map(depositoRepository.save(mapper.map(dto))); }
  @Override public DepositoDTO buscarDepositoPorID(String id) { return mapper.map(depositoRepository.findById(Integer.valueOf(id)).orElseThrow()); }
  @Override public void setAlgoritmoMM(String id, TipoAlgoritmoEnum alg) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    d.setAlgoritmo(alg);
    depositoRepository.save(d);
  }

  public PaqueteDTO buscarPaquetePorID(String id) {
    Paquete paquete = paqueteRepository.findById(Integer.valueOf(id))
            .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));
    return new PaqueteDTO(
            String.valueOf(paquete.getId()),
            paquete.getDonacionID(),
            paquete.getProductoID(),
            paquete.getCantidad()
    );
  }
  public List<PaqueteDTO> buscarTodosLosPaquetes() {
    return paqueteRepository.findAll().stream()
            .map(p -> new PaqueteDTO(String.valueOf(p.getId()), p.getDonacionID(), p.getProductoID(), p.getCantidad()))
            .toList();
  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(String id, PaqueteDTO p,
                                           List<NecesidadMaterialDTO> n) {

    Deposito deposito = depositoRepository.findById(Integer.valueOf(id))
            .orElseThrow(() -> new NoSuchElementException("Depósito no encontrado"));

    NecesidadMaterialDTO e = matchmaker.calcularMejorOpcion(n, deposito.getAlgoritmo(), p.cantidad());
    Asignacion asignacion = asignacionRepository.save(new Asignacion(p.id(), e.id()));
    return mapper.map(asignacion);
  }

  @Override public AsignacionDTO buscarAsignacionPorPaqueteID(String id) { return mapper.map(asignacionRepository.findByPaqueteID(id).orElseThrow()); }
  public AsignacionDTO buscarAsignacionPorID(String id) { return mapper.map(asignacionRepository.findById(id).orElseThrow()); }
  public List<DepositoDTO> buscarTodosLosDepositos() { return depositoRepository.findAll().stream().map(mapper::map).toList(); }
  public DepositoDTO eliminarDeposito(String id) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    depositoRepository.deleteById(Integer.valueOf(id));
    return mapper.map(d);
  }
  public void limpiarBaseDeDatos() {
    asignacionRepository.deleteAll();
    paqueteRepository.deleteAll();
    depositoRepository.deleteAll();
  }

  public Object debugNecesidades(String productoID) {
    if (entidadesClient == null) {
      return "PROBLEMA: entidadesClient es NULL — el cliente Feign nunca se inyectó";
    }
    try {
      List<NecesidadMaterialDTO> resultado = entidadesClient.getAllNecesidadesDeUnProducto(productoID);
      return resultado;
    } catch (Exception e) {
      return "ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage();
    }
  }

}

