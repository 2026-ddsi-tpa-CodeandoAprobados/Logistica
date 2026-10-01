package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.exceptions.CapacidadInsuficienteException;
import ar.edu.utn.dds.k3003.exceptions.SinNecesidadElegibleException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.logging.Traza;
import ar.edu.utn.dds.k3003.messaging.DonacionMessage;
import ar.edu.utn.dds.k3003.messaging.DonacionPublisher;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Matchmaker;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recepción de una donación en un depósito.
 *
 * <p>Verifica el espacio contra el total de la donación y después, según esté activa o no la
 * mensajería, encola el trabajo o lo procesa en el momento.
 *
 * <p>No valida contra Donaciones que la donación exista, y no hay que reponer esa validación:
 * Donaciones llama a Logística dentro de su propia transacción, así que una consulta de vuelta
 * no ve la fila todavía, devuelve 404 y termina en un rollback del otro lado.
 */
@Service
@Transactional
public class RecepcionDonacionService {

  private static final Logger log = LoggerFactory.getLogger(RecepcionDonacionService.class);

  private final DepositoService depositoService;
  private final StockService stockService;
  private final AsignacionService asignacionService;
  private final Matchmaker matchmaker;
  private final LogisticaDataMapper mapper;

  @Autowired(required = false)
  private EntidadesClient entidadesClient;

  @Autowired(required = false)
  private DonacionPublisher donacionPublisher;

  public RecepcionDonacionService(DepositoService depositoService,
                                  StockService stockService,
                                  AsignacionService asignacionService,
                                  Matchmaker matchmaker,
                                  LogisticaDataMapper mapper) {
    this.depositoService = depositoService;
    this.stockService = stockService;
    this.asignacionService = asignacionService;
    this.matchmaker = matchmaker;
    this.mapper = mapper;
  }

  public DepositoDTO recibir(DonacionDTO donacion) {
    if (donacion == null || donacion.detallesProductosDTO() == null
            || donacion.detallesProductosDTO().isEmpty()) {
      throw new SolicitudInvalidaException("La donación está vacía o es nula");
    }

    Deposito deposito = depositoService.obtener(donacion.depositoID());
    int unidades = totalDeUnidades(donacion);
    verificarEspacio(deposito, donacion, unidades);

    // Entrega 4 - Parte B: con mensajería activa se encola y un Worker asigna de forma async,
    // así que el stock que se devuelve todavía no refleja esta donación.
    if (donacionPublisher != null) {
      log.info("Donación {} recibida en el depósito {}: {} unidades en {} productos, se encola",
              donacion.id(), deposito.getId(), unidades, donacion.detallesProductosDTO().size());
      encolar(donacion, deposito);
      return mapper.map(deposito);
    }

    log.info("Donación {} recibida en el depósito {}: {} unidades en {} productos, se procesa",
            donacion.id(), deposito.getId(), unidades, donacion.detallesProductosDTO().size());
    for (var detalle : donacion.detallesProductosDTO()) {
      procesarDetalle(deposito, donacion.id(), detalle.productoID(), detalle.cantidadProducto());
    }
    return mapper.map(deposito);
  }

  /** Una donación rechazada por falta de espacio es un evento de negocio: se deja constancia. */
  private void verificarEspacio(Deposito deposito, DonacionDTO donacion, int unidades) {
    try {
      deposito.verificarEspacioPara(unidades);
    } catch (CapacidadInsuficienteException e) {
      log.warn("Donación {} rechazada por capacidad: {}", donacion.id(), e.getMessage());
      throw e;
    }
  }

  /** Suma las unidades de la donación y valida de paso que todas las cantidades sean positivas. */
  private int totalDeUnidades(DonacionDTO donacion) {
    int total = 0;
    for (var detalle : donacion.detallesProductosDTO()) {
      if (detalle.cantidadProducto() == null || detalle.cantidadProducto() <= 0) {
        throw new SolicitudInvalidaException(
                "La cantidad del producto " + detalle.productoID() + " debe ser mayor a cero");
      }
      total += detalle.cantidadProducto();
    }
    return total;
  }

  private void encolar(DonacionDTO donacion, Deposito deposito) {
    List<DonacionMessage.Item> items = donacion.detallesProductosDTO().stream()
            .map(d -> new DonacionMessage.Item(d.productoID(), d.cantidadProducto()))
            .toList();
    donacionPublisher.publicar(new DonacionMessage(
            donacion.id(), donacion.depositoID(), deposito.getAlgoritmo(), items, Traza.actual()));
  }

  /**
   * Para el producto donado: si no hay necesidades insatisfechas, todo va al stock. Si las hay,
   * el matchmaking elige UNA y se le asigna el mínimo entre lo donado y lo que le falta, como
   * paquete aparte que no pasa por el stock. El sobrante se guarda en el stock en ese momento,
   * de modo que una misma donación puede terminar en dos paquetes.
   */
  private void procesarDetalle(Deposito deposito, String donacionID, String productoID, int cantidad) {
    List<NecesidadMaterialDTO> necesidades = (entidadesClient != null)
            ? entidadesClient.getAllNecesidadesDeUnProducto(productoID)
            : null;

    if (necesidades == null || necesidades.isEmpty()) {
      log.info("Donación {}, producto {}: sin necesidades insatisfechas, {} unidades al stock",
              donacionID, productoID, cantidad);
      stockService.guardar(deposito, donacionID, productoID, cantidad);
      return;
    }

    NecesidadMaterialDTO necesidad;
    try {
      necesidad = matchmaker.calcularMejorOpcion(necesidades, deposito.getAlgoritmo(), cantidad);
    } catch (SinNecesidadElegibleException e) {
      // Sólo hay recurrentes que no se pueden cubrir por completo -> todo al stock.
      log.info("Donación {}, producto {}: ninguna necesidad es elegible, {} unidades al stock",
              donacionID, productoID, cantidad);
      stockService.guardar(deposito, donacionID, productoID, cantidad);
      return;
    }

    int aAsignar = matchmaker.cantidadAAsignar(necesidad, cantidad);
    if (aAsignar <= 0) {
      stockService.guardar(deposito, donacionID, productoID, cantidad);
      return;
    }

    asignacionService.asignarPorMatchmaking(donacionID, productoID, aAsignar, necesidad.id());
    log.info("Donación {}, producto {}: {} de {} unidades asignadas a la necesidad {}",
            donacionID, productoID, aAsignar, cantidad, necesidad.id());

    int sobrante = cantidad - aAsignar;
    if (sobrante > 0) {
      log.info("Donación {}, producto {}: {} unidades sobrantes al stock",
              donacionID, productoID, sobrante);
      stockService.guardar(deposito, donacionID, productoID, sobrante);
    }
  }
}
