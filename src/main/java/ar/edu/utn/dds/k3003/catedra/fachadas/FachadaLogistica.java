package ar.edu.utn.dds.k3003.catedra.fachadas;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import java.util.List;
import java.util.NoSuchElementException;

public interface FachadaLogistica {

  DepositoDTO agregarDeposito(DepositoDTO deposito);

  DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException;

  AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException;

  DepositoDTO gestionarDonacion(DonacionDTO donacion) throws NoSuchElementException;

  DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad)
          throws NoSuchElementException;

  void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo);

  AsignacionDTO ejecutarMatchmaking(
          String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidades);

  void reportarEntrega(PaqueteDTO paqueteDTO);

  void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades);

  void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones);
}