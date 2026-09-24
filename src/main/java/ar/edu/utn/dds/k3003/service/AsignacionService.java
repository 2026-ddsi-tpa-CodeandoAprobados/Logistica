package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.exceptions.RecursoNoEncontradoException;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.AsignacionRepository;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vínculos entre un paquete y la necesidad a la que se lo destinó.
 *
 * <p>El origen distingue si la asignación la decidió el matchmaking de Logística o si la
 * pidió el módulo de Donadores contra el stock. Incentivos usa esa diferencia.
 */
@Service
@Transactional
public class AsignacionService {

  private final AsignacionRepository asignacionRepository;
  private final PaqueteService paqueteService;
  private final LogisticaDataMapper mapper;
  private final MetricasLogistica metricas;

  public AsignacionService(AsignacionRepository asignacionRepository,
                           PaqueteService paqueteService,
                           LogisticaDataMapper mapper,
                           MetricasLogistica metricas) {
    this.asignacionRepository = asignacionRepository;
    this.paqueteService = paqueteService;
    this.mapper = mapper;
    this.metricas = metricas;
  }

  /** La necesidad la eligió el matchmaking al recibir la donación. */
  public AsignacionDTO asignarPorMatchmaking(String donacionID, String productoID,
                                             int cantidad, String necesidadID) {
    AsignacionDTO creada = crear(donacionID, productoID, cantidad, necesidadID,
            OrigenAsignacionEnum.MATCHMAKING);
    metricas.asignacionPorMatchmaking();
    return creada;
  }

  /** La necesidad la trajo el módulo de Donadores, que pidió stock para cubrirla. */
  public AsignacionDTO asignarPorSolicitud(String donacionID, String productoID,
                                            int cantidad, String necesidadID) {
    AsignacionDTO creada = crear(donacionID, productoID, cantidad, necesidadID,
            OrigenAsignacionEnum.SOLICITUD_DONADORES);
    metricas.asignacionPorSolicitud();
    return creada;
  }

  private AsignacionDTO crear(String donacionID, String productoID, int cantidad,
                              String necesidadID, OrigenAsignacionEnum origen) {
    Paquete paquete = paqueteService.crear(donacionID, productoID, cantidad);
    Asignacion asignacion = asignacionRepository.save(
            new Asignacion(String.valueOf(paquete.getId()), necesidadID, origen));
    return mapper.map(asignacion);
  }

  /** Alta para un paquete que ya existe, usada por el matchmaking de la interfaz de cátedra. */
  public AsignacionDTO asignarPaqueteExistente(String paqueteID, String necesidadID) {
    return mapper.map(asignacionRepository.save(new Asignacion(paqueteID, necesidadID)));
  }

  public void completar(Asignacion asignacion) {
    asignacion.setEstado(EstadoAsginacionEnum.COMPLETADA);
    asignacionRepository.save(asignacion);
  }

  @Transactional(readOnly = true)
  public List<AsignacionDTO> buscarTodas() {
    return asignacionRepository.findAll().stream().map(mapper::map).toList();
  }

  @Transactional(readOnly = true)
  public AsignacionDTO buscarPorID(String asignacionID) {
    return mapper.map(asignacionRepository.findById(asignacionID)
            .orElseThrow(() -> RecursoNoEncontradoException.de("Asignación", asignacionID)));
  }

  @Transactional(readOnly = true)
  public AsignacionDTO buscarPorPaqueteID(String paqueteID) {
    return mapper.map(obtenerPorPaqueteID(paqueteID));
  }

  public Asignacion obtenerPorPaqueteID(String paqueteID) {
    return asignacionRepository.findByPaqueteID(paqueteID)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                    "No hay ninguna asignación para el paquete " + paqueteID));
  }
}
