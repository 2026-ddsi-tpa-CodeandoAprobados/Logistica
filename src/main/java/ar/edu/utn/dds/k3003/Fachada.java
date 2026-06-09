package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.NoSuchElementException;

@Component
@Transactional
public class Fachada implements FachadaLogistica {

  @Autowired private DepositoRepository depositoRepository;
  @Autowired private AsignacionRepository asignacionRepository;
  @Autowired private LogisticaDataMapper mapper;
  @Autowired private Matchmaker matchmaker;
  @Autowired(required = false) private MeterRegistry meterRegistry;

  private Counter entregasCompletadasCounter;
  private FachadaDonadoresYEntidades fachadaDonadores;
  private FachadaDonaciones fachadaDonaciones;

  public Fachada() {}

  @PostConstruct
  public void initMetrics() {
    if (meterRegistry != null) {
      this.entregasCompletadasCounter = Counter.builder("logistica.entregas_completadas")
              .tag("modulo", "logistica")
              .register(meterRegistry);
    }
  }

  @Override
  public DepositoDTO gestionarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null || donacionDTO.detallesProductosDTO() == null || donacionDTO.detallesProductosDTO().isEmpty()) {
      throw new RuntimeException("La donación está vacía o es nula");
    }

    Deposito deposito = depositoRepository.findById(Integer.valueOf(donacionDTO.depositoID()))
            .orElseThrow(() -> new NoSuchElementException("Depósito no encontrado"));

    List<Paquete> nuevosPaquetes = donacionDTO.detallesProductosDTO().stream()
            .map(detalle -> {
              if (detalle.cantidad() <= 0) throw new RuntimeException("Cantidad inválida");
              return new Paquete(donacionDTO.id(), detalle.productoId(), detalle.cantidad());
            })
            .toList();

    deposito.getStock().addAll(nuevosPaquetes);
    deposito = depositoRepository.save(deposito);

    if (this.fachadaDonadores != null) {
      nuevosPaquetes.forEach(paquete ->
              this.fachadaDonadores.obtenerNecesidadesInsatisfechasDe(paquete.getExternalId())
      );
    }
    return mapper.map(deposito);
  }

  @Override public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades f) { this.fachadaDonadores = f; }
  @Override public void setFachadaDonaciones(FachadaDonaciones f) { this.fachadaDonaciones = f; }
  @Override public DepositoDTO agregarDeposito(DepositoDTO dto) { return mapper.map(depositoRepository.save(mapper.map(dto))); }
  @Override public DepositoDTO buscarDepositoPorID(String id) { return mapper.map(depositoRepository.findById(Integer.valueOf(id)).orElseThrow()); }
  @Override public void setAlgoritmoMM(String id, TipoAlgoritmoEnum alg) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    d.setAlgoritmo(alg);
    depositoRepository.save(d);
  }
  @Override public AsignacionDTO ejecutarMatchmaking(String id, PaqueteDTO p, List<NecesidadMaterialDTO> n) {
    NecesidadMaterialDTO e = matchmaker.calcularMejorOpcion(n);
    return mapper.map(asignacionRepository.save(new Asignacion(p.id(), e.id())));
  }
  @Override public void reportarEntrega(PaqueteDTO p) {
    Asignacion a = asignacionRepository.findByPaqueteID(p.id()).orElseThrow();
    if(this.fachadaDonadores != null) this.fachadaDonadores.satisfacerNecesidad(a.getNecesidadID(), p.cantidad());
    if(this.fachadaDonaciones != null) this.fachadaDonaciones.cambiarEstadoDeDonacion(p.donacionID(), EstadoDonacionEnum.ACEPTADA);
    a.setEstado(EstadoAsginacionEnum.COMPLETADA);
    asignacionRepository.save(a);
    if(entregasCompletadasCounter != null) entregasCompletadasCounter.increment();
  }
  @Override public AsignacionDTO buscarAsignacionPorPaqueteID(String id) { return mapper.map(asignacionRepository.findByPaqueteID(id).orElseThrow()); }
  public List<DepositoDTO> buscarTodosLosDepositos() { return depositoRepository.findAll().stream().map(mapper::map).toList(); }
  public DepositoDTO eliminarDeposito(String id) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    depositoRepository.deleteById(Integer.valueOf(id));
    return mapper.map(d);
  }
  public void limpiarBaseDeDatos() { asignacionRepository.deleteAll(); depositoRepository.deleteAll(); }
}