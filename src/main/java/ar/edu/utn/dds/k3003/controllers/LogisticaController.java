package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/")
public class LogisticaController {

    @Autowired
    private Fachada fachada;

    // --- RECORDS ---
    public record DepositoRequest(String nombre, String direccion, Integer capacidadMaxima) {}
    public record DonacionRequest(String donacionID, String productoID, Integer cantidad) {}
    public record PaqueteRequest(String paqueteId) {}

    // ---------------- DEPOSITOS ----------------

    @GetMapping("/depositos")
    public ResponseEntity<List<DepositoDTO>> obtenerTodosLosDepositos() {
        List<DepositoDTO> lista = fachada.buscarTodosLosDepositos();
        return new ResponseEntity<>(lista, HttpStatus.OK);
    }

    @PostMapping("/depositos")
    public ResponseEntity<DepositoDTO> crearDeposito(@RequestBody DepositoRequest request) {
        try {
            DepositoDTO depositoDTO = new DepositoDTO(
                    null,
                    TipoAlgoritmoEnum.PRIORIDAD,
                    request.nombre(),
                    request.direccion(),
                    request.capacidadMaxima(),
                    new ArrayList<>()
            );
            DepositoDTO respuesta = fachada.agregarDeposito(depositoDTO);
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> buscarDepositoPorId(@PathVariable String id) {
        try {
            DepositoDTO dto = fachada.buscarDepositoPorID(id);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/depositos/{id}")
    public ResponseEntity<Void> eliminarDeposito(@PathVariable String id) {
        try {
            fachada.eliminarDeposito(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // ---------------- DONACION ----------------
    @PostMapping("/depositos/{id}/donacion")
    public ResponseEntity<DepositoDTO> gestionarDonacion(@PathVariable String id, @RequestBody DonacionRequest request) {
        try {
            // 1. Crea el detalle
            DetalleProductoDTO detalle = new DetalleProductoDTO(
                    null,
                    request.productoID(),
                    request.cantidad()
            );

            // 2. Instancia el DonacionDTO
            DonacionDTO donacionDTO = new DonacionDTO(
                    request.donacionID(),       // id
                    null,                       // donadorID (no lo tengo en el request)
                    id,                         // depositoID (viene del path)
                    "Donacion desde Logistica", // descripcion
                    List.of(detalle),           // detallesProductosDTO
                    null,                       // estado (se suele setear en el servicio Donaciones)
                    null                        // fechaRegistro (se suele setear en el servicio Donaciones)
            );

            // 3. Llamo a la fachada
            DepositoDTO respuesta = fachada.gestionarDonacion(donacionDTO);
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------- ENTREGAS ----------------

    @PostMapping("/entregas")
    public ResponseEntity<Void> registrarEntrega(@RequestBody PaqueteRequest request) {
        try {
            PaqueteDTO paqueteDTO = fachada.buscarPaquetePorID(request.paqueteId());
            fachada.reportarEntrega(paqueteDTO);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // ---------------- ASIGNACIONES ----------------

    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> buscarAsignacion(@PathVariable String id) {
        try {
            AsignacionDTO dto = fachada.buscarAsignacionPorPaqueteID(id);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/testing/reset")
    public ResponseEntity<String> limpiarBaseDeDatos() {
        try {
            fachada.limpiarBaseDeDatos();
            return new ResponseEntity<>("Base de datos de Logística limpiada exitosamente", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error al limpiar la base de datos: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}