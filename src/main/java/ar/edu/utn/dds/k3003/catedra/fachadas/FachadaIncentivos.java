package ar.edu.utn.dds.k3003.catedra.fachadas;

import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import java.util.List;
import java.util.NoSuchElementException;

public interface FachadaIncentivos {

  InsigniaDTO agregarInsignia(InsigniaDTO insignia);

  MisionDTO agregarMision(MisionDTO mision);

  List<InsigniaDTO> getInsigniasDeDonador(String donadorID) throws NoSuchElementException;

  MisionDTO getMisionEnCursoDeDonador(String donadorID) throws NoSuchElementException;

  void asignarMisionADonador(String donadorID, MisionDTO misionDTO) throws NoSuchElementException;

  void asignarInsigniaADonador(String donadorID, InsigniaDTO insigniaDTO)
      throws NoSuchElementException;

  void procesarDonador(String donadorID) throws NoSuchElementException;

  void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones);

  void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades);
}
