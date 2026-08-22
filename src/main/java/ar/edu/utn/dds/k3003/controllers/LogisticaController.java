package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.messaging.AltaAsignacionRequest;
import ar.edu.utn.dds.k3003.messaging.GuardarStockRequest;
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
    public record PaqueteRequest(String paqueteId) {}
    public record AlgoritmoRequest(TipoAlgoritmoEnum algoritmo) {}

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
                    null,
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

    @PatchMapping("/depositos/{id}/algoritmo")
    public ResponseEntity<Void> setAlgoritmo(@PathVariable String id, @RequestBody AlgoritmoRequest request) {
        try {
            fachada.setAlgoritmoMM(id, request.algoritmo());
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/paquetes")
    public ResponseEntity<List<PaqueteDTO>> obtenerTodosLosPaquetes() {
        return new ResponseEntity<>(fachada.buscarTodosLosPaquetes(), HttpStatus.OK);
    }

    // ---------------- DONACION ----------------
    @PostMapping("/donaciones")
    public ResponseEntity<DepositoDTO> gestionarDonacion(@RequestBody DonacionDTO donacionDTO) {
        try {
            DepositoDTO respuesta = fachada.gestionarDonacion(donacionDTO);
            return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    // ---------------- WORKER (Entrega 4 - Parte B) ----------------

    // El Worker (stateless) da de alta la asignación calculada por matchmaking.
    @PostMapping("/asignaciones")
    public ResponseEntity<AsignacionDTO> altaAsignacion(@RequestBody AltaAsignacionRequest request) {
        try {
            AsignacionDTO dto = fachada.altaAsignacionDesdeWorker(
                    request.donacionID(), request.productoID(), request.cantidad(), request.necesidadID());
            return new ResponseEntity<>(dto, HttpStatus.CREATED);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    // El Worker guarda el sobrante de la donación en el stock del depósito.
    @PostMapping("/depositos/{id}/stock")
    public ResponseEntity<DepositoDTO> guardarSobranteEnStock(@PathVariable String id,
                                                              @RequestBody GuardarStockRequest request) {
        try {
            DepositoDTO dto = fachada.guardarSobranteEnStock(
                    id, request.donacionID(), request.productoID(), request.cantidad());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
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

    // ---------------- STOCK (Entrega 4 - Donadores) ----------------

    // Donadores consulta cuánto stock hay de un producto (agregado de todos los depósitos).
    @GetMapping("/stock/{productoID}")
    public ResponseEntity<StockDisponibleDTO> stockDisponible(@PathVariable String productoID) {
        int disponible = fachada.stockDisponible(productoID);
        return ResponseEntity.ok(new StockDisponibleDTO(productoID, disponible));
    }

    // Donadores pide asignar stock a una necesidad (origen SOLICITUD_DONADORES).
    // 201 -> se creó la asignación (por la cantidad que Logística pudo cubrir).
    // 204 -> no había nada para asignar (sin stock, o cantidad nula/cero). NO es un error
    // Se devuelve una LISTA porque el stock puede venir de varias donaciones y un paquete
    // pertenece a una sola: en ese caso se crea una asignación por donación de origen.
    @PostMapping("/stock/{productoID}/asignaciones")
    public ResponseEntity<List<AsignacionDTO>> asignarDesdeStock(@PathVariable String productoID,
                                                                 @RequestBody(required = false) AsignacionDesdeStockRequest request) {
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            List<AsignacionDTO> asignaciones = fachada.asignarDesdeStock(
                    productoID, request.cantidad(), request.necesidadID());
            if (asignaciones.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return new ResponseEntity<>(asignaciones, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------- ASIGNACIONES ----------------

    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorId(@PathVariable String id) {
        try {
            AsignacionDTO dto = fachada.buscarAsignacionPorID(id);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/asignaciones/paquete/{paqueteID}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorPaquete(@PathVariable String paqueteID) {
        try {
            AsignacionDTO dto = fachada.buscarAsignacionPorPaqueteID(paqueteID);
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

    @GetMapping("/debug/necesidades/{productoID}")
    public ResponseEntity<Object> debugNecesidades(@PathVariable String productoID) {
        return ResponseEntity.ok(fachada.debugNecesidades(productoID));
    }
    @GetMapping("/asignaciones")
    public ResponseEntity<List<AsignacionDTO>> obtenerTodasLasAsignaciones() {
        return new ResponseEntity<>(fachada.buscarTodasLasAsignaciones(), HttpStatus.OK);
    }
}
