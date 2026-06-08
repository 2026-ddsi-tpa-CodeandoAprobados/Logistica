package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/")
public class LogisticaController {

    @Autowired
    private Fachada fachada;

    @PostMapping("/depositos")
    public ResponseEntity<DepositoDTO> agregarDeposito(@RequestBody DepositoDTO depositoDTO) {
        try {
            DepositoDTO respuesta = fachada.agregarDeposito(depositoDTO);
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/depositos")
    public ResponseEntity<List<DepositoDTO>> buscarTodos() {
        List<DepositoDTO> lista = fachada.buscarTodosLosDepositos();
        if (lista.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(lista, HttpStatus.OK);
    }

    @GetMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> buscarDeposito(@PathVariable String id) {
        try {
            DepositoDTO dto = fachada.buscarDepositoPorID(id);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> eliminar(@PathVariable String id) {
        try {
            DepositoDTO eliminado = fachada.eliminarDeposito(id);
            return new ResponseEntity<>(eliminado, HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/donaciones")
    public ResponseEntity<DepositoDTO> gestionarDonacion(@RequestBody DonacionDTO donacionDTO) {
        try {
            DepositoDTO respuesta = fachada.gestionarDonacion(donacionDTO);
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> buscarAsignacion(@PathVariable String id) {
        try {
            AsignacionDTO dto = fachada.buscarAsignacionPorPaqueteID(id);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/entregas")
    public ResponseEntity<String> reportarEntrega(@RequestBody PaqueteDTO paqueteDTO) {
        try {
            fachada.reportarEntrega(paqueteDTO);
            return new ResponseEntity<>("Entrega registrada correctamente", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error al reportar: " + e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/asignaciones")
    public ResponseEntity<AsignacionDTO> crearAsignacion(@RequestBody MatchmakingRequestDTO request) {
        try {
            AsignacionDTO respuesta = fachada.ejecutarMatchmaking(
                    request.depositoID(),
                    request.paquete(),
                    request.necesidades()
            );
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/testing/reset")
    public ResponseEntity<String> limpiarBaseDeDatos() {
        try {
            fachada.limpiarBaseDeDatos();
            return new ResponseEntity<>("Base de datos de Logística limpiada exitosamente", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error al limpiar la base de datos", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    record MatchmakingRequestDTO(String depositoID, PaqueteDTO paquete, List<ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO> necesidades) {}
}