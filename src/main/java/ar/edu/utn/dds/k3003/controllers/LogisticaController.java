package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.messaging.AltaAsignacionRequest;
import ar.edu.utn.dds.k3003.messaging.GuardarStockRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Única puerta HTTP del módulo. No tiene lógica propia: valida lo mínimo, delega en la
 * {@link Fachada} y traduce el resultado a un código de respuesta.
 *
 * <p>Los errores no se atrapan acá. Las excepciones de dominio viajan hasta
 * {@link ManejadorDeErrores}, que es el único lugar que decide el código HTTP y arma el cuerpo.
 */
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
        return ResponseEntity.ok(fachada.buscarTodosLosDepositos());
    }

    @PostMapping("/depositos")
    public ResponseEntity<DepositoDTO> crearDeposito(@RequestBody DepositoRequest request) {
        DepositoDTO depositoDTO = new DepositoDTO(
                null,
                null,
                request.nombre(),
                request.direccion(),
                request.capacidadMaxima(),
                new ArrayList<>()
        );
        return new ResponseEntity<>(fachada.agregarDeposito(depositoDTO), HttpStatus.CREATED);
    }

    @GetMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> buscarDepositoPorId(@PathVariable String id) {
        return ResponseEntity.ok(fachada.buscarDepositoPorID(id));
    }

    @DeleteMapping("/depositos/{id}")
    public ResponseEntity<Void> eliminarDeposito(@PathVariable String id) {
        fachada.eliminarDeposito(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/depositos/{id}/algoritmo")
    public ResponseEntity<Void> setAlgoritmo(@PathVariable String id, @RequestBody AlgoritmoRequest request) {
        fachada.setAlgoritmoMM(id, request.algoritmo());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/paquetes")
    public ResponseEntity<List<PaqueteDTO>> obtenerTodosLosPaquetes() {
        return ResponseEntity.ok(fachada.buscarTodosLosPaquetes());
    }

    // ---------------- DONACION ----------------

    @PostMapping("/donaciones")
    public ResponseEntity<DepositoDTO> gestionarDonacion(@RequestBody DonacionDTO donacionDTO) {
        return new ResponseEntity<>(fachada.gestionarDonacion(donacionDTO), HttpStatus.CREATED);
    }

    // ---------------- WORKER (Entrega 4 - Parte B) ----------------

    // El Worker (stateless) da de alta la asignación calculada por matchmaking.
    @PostMapping("/asignaciones")
    public ResponseEntity<AsignacionDTO> altaAsignacion(@RequestBody AltaAsignacionRequest request) {
        AsignacionDTO dto = fachada.altaAsignacionDesdeWorker(
                request.donacionID(), request.productoID(), request.cantidad(), request.necesidadID());
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    // El Worker guarda el sobrante de la donación en el stock del depósito.
    @PostMapping("/depositos/{id}/stock")
    public ResponseEntity<DepositoDTO> guardarSobranteEnStock(@PathVariable String id,
                                                              @RequestBody GuardarStockRequest request) {
        return ResponseEntity.ok(fachada.guardarSobranteEnStock(
                id, request.donacionID(), request.productoID(), request.cantidad()));
    }

    // ---------------- ENTREGAS ----------------

    @PostMapping("/entregas")
    public ResponseEntity<Void> registrarEntrega(@RequestBody PaqueteRequest request) {
        PaqueteDTO paqueteDTO = fachada.buscarPaquetePorID(request.paqueteId());
        fachada.reportarEntrega(paqueteDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
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
            throw new SolicitudInvalidaException("Falta el cuerpo con la cantidad y la necesidad");
        }
        List<AsignacionDTO> asignaciones = fachada.asignarDesdeStock(
                productoID, request.cantidad(), request.necesidadID());
        if (asignaciones.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return new ResponseEntity<>(asignaciones, HttpStatus.CREATED);
    }

    // ---------------- ASIGNACIONES ----------------

    @GetMapping("/asignaciones")
    public ResponseEntity<List<AsignacionDTO>> obtenerTodasLasAsignaciones() {
        return ResponseEntity.ok(fachada.buscarTodasLasAsignaciones());
    }

    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorId(@PathVariable String id) {
        return ResponseEntity.ok(fachada.buscarAsignacionPorID(id));
    }

    @GetMapping("/asignaciones/paquete/{paqueteID}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorPaquete(@PathVariable String paqueteID) {
        return ResponseEntity.ok(fachada.buscarAsignacionPorPaqueteID(paqueteID));
    }

    // ---------------- TESTING ----------------

    @DeleteMapping("/testing/reset")
    public ResponseEntity<String> limpiarBaseDeDatos() {
        fachada.limpiarBaseDeDatos();
        return ResponseEntity.ok("Base de datos de Logística limpiada exitosamente");
    }
}
