package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.exceptions.RecursoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración de depósitos: alta, baja, modificación, consulta y algoritmo de matchmaking. */
@Service
@Transactional
public class DepositoService {

  private static final Logger log = LoggerFactory.getLogger(DepositoService.class);

  private final DepositoRepository depositoRepository;
  private final LogisticaDataMapper mapper;

  public DepositoService(DepositoRepository depositoRepository, LogisticaDataMapper mapper) {
    this.depositoRepository = depositoRepository;
    this.mapper = mapper;
  }

  public DepositoDTO crear(DepositoDTO dto) {
    validarDatos(dto.nombre(), dto.capacidadMaxima());
    DepositoDTO creado = mapper.map(depositoRepository.save(mapper.map(dto)));
    log.info("Depósito {} creado: {}, capacidad {}", creado.id(), creado.nombre(), creado.capacidadMaxima());
    return creado;
  }

  /**
   * Modificación completa de los datos propios del depósito: lo que no se envía queda vacío.
   * El stock y el algoritmo de matchmaking no cambian, este último tiene su propia operación.
   */
  public DepositoDTO modificar(String depositoID, DepositoDTO dto) {
    validarDatos(dto.nombre(), dto.capacidadMaxima());
    Deposito deposito = obtener(depositoID);
    deposito.actualizarDatos(dto.nombre(), dto.direccion(), dto.capacidadMaxima());
    depositoRepository.save(deposito);
    log.info("Depósito {} modificado: {}, capacidad {}", depositoID, dto.nombre(), dto.capacidadMaxima());
    return mapper.map(deposito);
  }

  /** El nombre es obligatorio, y una capacidad negativa no tiene sentido. Sin capacidad no hay límite. */
  private void validarDatos(String nombre, Integer capacidadMaxima) {
    if (nombre == null || nombre.isBlank()) {
      throw new SolicitudInvalidaException("El nombre del depósito es obligatorio");
    }
    if (capacidadMaxima != null && capacidadMaxima < 0) {
      throw new SolicitudInvalidaException(
              "La capacidad máxima no puede ser negativa, llegó " + capacidadMaxima);
    }
  }

  @Transactional(readOnly = true)
  public DepositoDTO buscarPorID(String depositoID) {
    return mapper.map(obtener(depositoID));
  }

  @Transactional(readOnly = true)
  public List<DepositoDTO> buscarTodos() {
    return depositoRepository.findAll().stream().map(mapper::map).toList();
  }

  public DepositoDTO eliminar(String depositoID) {
    Deposito deposito = obtener(depositoID);
    deposito.verificarQueSePuedeEliminar();
    depositoRepository.delete(deposito);
    log.info("Depósito {} eliminado", depositoID);
    return mapper.map(deposito);
  }

  public void configurarAlgoritmo(String depositoID, TipoAlgoritmoEnum algoritmo) {
    Deposito deposito = obtener(depositoID);
    deposito.setAlgoritmo(algoritmo);
    depositoRepository.save(deposito);
    log.info("Depósito {}: algoritmo de matchmaking configurado en {}", depositoID, algoritmo);
  }

  // ---------------- Para los demás servicios del módulo ----------------

  /** Trae la entidad, validando el formato del id, que llega como String desde la API. */
  public Deposito obtener(String depositoID) {
    return depositoRepository.findById(parsearID(depositoID))
            .orElseThrow(() -> RecursoNoEncontradoException.de("Depósito", depositoID));
  }

  public List<Deposito> obtenerTodos() {
    return depositoRepository.findAll();
  }

  public void guardar(Deposito deposito) {
    depositoRepository.save(deposito);
  }

  private Integer parsearID(String depositoID) {
    if (depositoID == null || depositoID.isBlank()) {
      throw new SolicitudInvalidaException("Falta el id de depósito");
    }
    try {
      return Integer.valueOf(depositoID);
    } catch (NumberFormatException e) {
      throw new SolicitudInvalidaException(
              "El id de depósito debe ser numérico, llegó \"" + depositoID + "\"");
    }
  }
}
