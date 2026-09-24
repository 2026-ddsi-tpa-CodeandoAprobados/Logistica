package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock guardado en los depósitos: cuánto hay de cada producto, cómo se guarda lo que sobra
 * de una donación y cómo se consume cuando Donadores pide cubrir una necesidad.
 */
@Service
@Transactional
public class StockService {

  private final DepositoService depositoService;
  private final AsignacionService asignacionService;
  private final LogisticaDataMapper mapper;
  private final MetricasLogistica metricas;

  public StockService(DepositoService depositoService,
                      AsignacionService asignacionService,
                      LogisticaDataMapper mapper,
                      MetricasLogistica metricas) {
    this.depositoService = depositoService;
    this.asignacionService = asignacionService;
    this.mapper = mapper;
    this.metricas = metricas;
  }

  /** Guarda unidades en el stock del depósito, respetando su capacidad máxima. */
  public void guardar(Deposito deposito, String donacionID, String productoID, int cantidad) {
    deposito.recibirEnStock(donacionID, productoID, cantidad);
    depositoService.guardar(deposito);
    metricas.paqueteGuardadoEnStock();
  }

  /** Entrega 4 - Parte B. Sobrante que le indica el Worker, que no tiene base de datos. */
  public DepositoDTO guardarSobrante(String depositoID, String donacionID,
                                     String productoID, int cantidad) {
    Deposito deposito = depositoService.obtener(depositoID);
    guardar(deposito, donacionID, productoID, cantidad);
    return mapper.map(deposito);
  }

  /**
   * Unidades disponibles de un producto sumando todos los depósitos. Donadores consulta por
   * producto, sin conocer la distribución interna en depósitos.
   */
  @Transactional(readOnly = true)
  public int disponible(String productoID) {
    return depositoService.obtenerTodos().stream()
            .mapToInt(d -> d.stockDe(productoID))
            .sum();
  }

  /**
   * Asigna stock a una necesidad a pedido de Donadores. Logística decide cuánto se asigna:
   * consume el mínimo entre lo disponible y lo solicitado, y el solicitante sólo pide.
   *
   * <p>Devuelve una asignación por cada donación de origen, porque un paquete pertenece a una
   * sola donación. La lista vacía significa que no había nada para asignar, no un error: el
   * alta de la necesidad del otro módulo no se puede caer porque acá no haya stock.
   */
  public List<AsignacionDTO> asignarDesdeStock(String productoID, Integer cantidadSolicitada,
                                               String necesidadID) {
    if (necesidadID == null || necesidadID.isBlank()) {
      throw new SolicitudInvalidaException("La necesidad a asignar es obligatoria");
    }
    if (productoID == null || productoID.isBlank()) {
      throw new SolicitudInvalidaException("El producto a asignar es obligatorio");
    }
    if (cantidadSolicitada == null || cantidadSolicitada <= 0) {
      return List.of(); // no se pidió nada asignable
    }

    List<Deposito> depositos = depositoService.obtenerTodos();
    int disponible = depositos.stream().mapToInt(d -> d.stockDe(productoID)).sum();
    if (disponible <= 0) {
      return List.of(); // no hay stock del producto
    }

    int restante = Math.min(disponible, cantidadSolicitada);
    Map<String, Integer> consumidoPorDonacion = new LinkedHashMap<>();

    for (Deposito deposito : depositos) {
      Map<String, Integer> consumido = deposito.consumirDeStock(productoID, restante);
      if (!consumido.isEmpty()) {
        consumido.forEach((donacionID, unidades) ->
                consumidoPorDonacion.merge(donacionID, unidades, Integer::sum));
        restante -= consumido.values().stream().mapToInt(Integer::intValue).sum();
        depositoService.guardar(deposito);
      }
      if (restante == 0) {
        break;
      }
    }

    List<AsignacionDTO> creadas = new ArrayList<>();
    consumidoPorDonacion.forEach((donacionID, unidades) ->
            creadas.add(asignacionService.asignarPorSolicitud(
                    donacionID, productoID, unidades, necesidadID)));
    return creadas;
  }
}
