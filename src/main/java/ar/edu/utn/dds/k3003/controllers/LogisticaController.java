package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.controllers.docs.ErrorCapacidadInsuficiente;
import ar.edu.utn.dds.k3003.controllers.docs.ErrorNoEncontrado;
import ar.edu.utn.dds.k3003.controllers.docs.ErrorOperacionNoPermitida;
import ar.edu.utn.dds.k3003.controllers.docs.ErrorSolicitudInvalida;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.messaging.AltaAsignacionRequest;
import ar.edu.utn.dds.k3003.messaging.GuardarStockRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private Fachada fachada;

    // --- RECORDS ---
    @Schema(description = "Datos para dar de alta un depósito.")
    public record DepositoRequest(
            @Schema(description = "Nombre del depósito.", example = "Depósito Central") String nombre,
            @Schema(description = "Dirección física.", example = "Medrano 951") String direccion,
            @Schema(description = "Unidades máximas que puede almacenar. Nulo significa sin límite.",
                    example = "1000") Integer capacidadMaxima) {}

    @Schema(description = "Paquete cuya entrega se reporta.")
    public record PaqueteRequest(
            @Schema(description = "Id del paquete asignado, el que figura en la asignación.",
                    example = "42") String paqueteId) {}

    @Schema(description = "Algoritmo de matchmaking a usar en el depósito.")
    public record AlgoritmoRequest(
            @Schema(description = "SUB_ATENDIDOS y PRIORIDAD aplican \"Prioridad a sub-atendidos\": "
                    + "eligen la necesidad más alejada de su cantidad objetivo. "
                    + "PRIORIDAD_POR_SCORE aplica \"Prioridad por score\": urgencia dividida por el "
                    + "nivel de cobertura, eligiendo el valor más alto.",
                    example = "PRIORIDAD_POR_SCORE") TipoAlgoritmoEnum algoritmo) {}

    // ---------------- DEPOSITOS ----------------

    @Operation(tags = "Depósitos", summary = "Lista todos los depósitos",
            description = "Incluye el stock actual de cada uno.")
    @ApiResponse(responseCode = "200", description = "Lista de depósitos, vacía si no hay ninguno.",
            content = @Content(mediaType = JSON,
                    array = @ArraySchema(schema = @Schema(implementation = DepositoDTO.class))))
    @GetMapping("/depositos")
    public ResponseEntity<List<DepositoDTO>> obtenerTodosLosDepositos() {
        return ResponseEntity.ok(fachada.buscarTodosLosDepositos());
    }

    @Operation(tags = "Depósitos", summary = "Crea un depósito",
            description = "Devuelve el depósito creado con su id, que es el que hay que usar al "
                    + "enviar donaciones. Nace sin algoritmo, así que usa SUB_ATENDIDOS hasta que "
                    + "se configure otro. El nombre es obligatorio y la capacidad no puede ser "
                    + "negativa. Una capacidad vacía significa sin límite.")
    @ApiResponse(responseCode = "201", description = "Depósito creado.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = DepositoDTO.class)))
    @ErrorSolicitudInvalida
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

    @Operation(tags = "Depósitos", summary = "Busca un depósito por id")
    @ApiResponse(responseCode = "200", description = "El depósito con su stock actual.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = DepositoDTO.class)))
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @GetMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> buscarDepositoPorId(
            @Parameter(description = "Id numérico del depósito.", example = "1") @PathVariable String id) {
        return ResponseEntity.ok(fachada.buscarDepositoPorID(id));
    }

    @Operation(tags = "Depósitos", summary = "Modifica los datos de un depósito",
            description = """
                    Reemplaza el nombre, la dirección y la capacidad máxima. Lo que no se envíe \
                    queda vacío, y una capacidad vacía significa sin límite. El stock y el algoritmo \
                    de matchmaking no cambian: el algoritmo tiene su propia operación.

                    La capacidad nueva no puede quedar por debajo de las unidades que el depósito \
                    ya almacena.""")
    @ApiResponse(responseCode = "200", description = "Depósito modificado.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = DepositoDTO.class)))
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @ErrorOperacionNoPermitida
    @PutMapping("/depositos/{id}")
    public ResponseEntity<DepositoDTO> modificarDeposito(
            @Parameter(description = "Id numérico del depósito.", example = "1") @PathVariable String id,
            @RequestBody DepositoRequest request) {
        DepositoDTO cambios = new DepositoDTO(
                null, null, request.nombre(), request.direccion(), request.capacidadMaxima(), new ArrayList<>());
        return ResponseEntity.ok(fachada.modificarDeposito(id, cambios));
    }

    @Operation(tags = "Depósitos", summary = "Elimina un depósito",
            description = "Solo se puede eliminar un depósito vacío. Con unidades en stock responde "
                    + "409: la baja destruiría el registro de lo almacenado.")
    @ApiResponse(responseCode = "204", description = "Depósito eliminado.")
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @ErrorOperacionNoPermitida
    @DeleteMapping("/depositos/{id}")
    public ResponseEntity<Void> eliminarDeposito(
            @Parameter(description = "Id numérico del depósito.", example = "1") @PathVariable String id) {
        fachada.eliminarDeposito(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(tags = "Depósitos", summary = "Configura el algoritmo de matchmaking del depósito",
            description = "Define cómo se elige la necesidad a la que se asigna cada donación que "
                    + "llega a este depósito. Aplica a las donaciones posteriores.")
    @ApiResponse(responseCode = "200", description = "Algoritmo configurado.")
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @PatchMapping("/depositos/{id}/algoritmo")
    public ResponseEntity<Void> setAlgoritmo(
            @Parameter(description = "Id numérico del depósito.", example = "1") @PathVariable String id,
            @RequestBody AlgoritmoRequest request) {
        fachada.setAlgoritmoMM(id, request.algoritmo());
        return ResponseEntity.ok().build();
    }

    @Operation(tags = "Paquetes", summary = "Lista los paquetes",
            description = "Incluye tanto los que están en stock como los ya asignados a una necesidad.")
    @ApiResponse(responseCode = "200", description = "Lista de paquetes, vacía si no hay ninguno.",
            content = @Content(mediaType = JSON,
                    array = @ArraySchema(schema = @Schema(implementation = PaqueteDTO.class))))
    @GetMapping("/paquetes")
    public ResponseEntity<List<PaqueteDTO>> obtenerTodosLosPaquetes() {
        return ResponseEntity.ok(fachada.buscarTodosLosPaquetes());
    }

    // ---------------- DONACION ----------------

    @Operation(tags = "Donaciones", summary = "Recibe una donación en un depósito",
            description = """
                    Verifica que el depósito tenga espacio para el total de la donación. Después, \
                    para cada producto: si hay necesidades insatisfechas el matchmaking elige UNA y \
                    se le asigna el mínimo entre lo donado y lo que le falta, y el sobrante va al \
                    stock. Si no hay necesidades, todo va al stock.

                    Con la mensajería activa el trabajo se encola y lo termina un Worker, por lo que \
                    el depósito devuelto todavía no refleja esta donación.

                    No valida contra Donaciones que la donación exista: Donaciones llama a este \
                    endpoint dentro de su propia transacción y una consulta de vuelta no la vería.""")
    @ApiResponse(responseCode = "201", description = "Donación recibida. Devuelve el depósito.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = DepositoDTO.class)))
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @ErrorCapacidadInsuficiente
    @PostMapping("/donaciones")
    public ResponseEntity<DepositoDTO> gestionarDonacion(@RequestBody DonacionDTO donacionDTO) {
        return new ResponseEntity<>(fachada.gestionarDonacion(donacionDTO), HttpStatus.CREATED);
    }

    // ---------------- WORKER (Entrega 4 - Parte B) ----------------

    // El Worker (stateless) da de alta la asignación calculada por matchmaking.
    @Operation(tags = "Interno (Worker)", summary = "Alta de una asignación por matchmaking",
            description = "Lo usa el Worker, que no tiene base de datos, para persistir la "
                    + "porción de una donación que asignó a una necesidad. Crea el paquete y la "
                    + "asignación con origen MATCHMAKING.")
    @ApiResponse(responseCode = "201", description = "Asignación creada.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AsignacionDTO.class)))
    @ErrorSolicitudInvalida
    @PostMapping("/asignaciones")
    public ResponseEntity<AsignacionDTO> altaAsignacion(@RequestBody AltaAsignacionRequest request) {
        AsignacionDTO dto = fachada.altaAsignacionDesdeWorker(
                request.donacionID(), request.productoID(), request.cantidad(), request.necesidadID());
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    // El Worker guarda el sobrante de la donación en el stock del depósito.
    @Operation(tags = "Interno (Worker)", summary = "Guarda un sobrante en el stock de un depósito",
            description = "Lo usa el Worker para guardar lo que quedó de una donación después de "
                    + "asignar. Respeta la capacidad máxima del depósito.")
    @ApiResponse(responseCode = "200", description = "Sobrante guardado. Devuelve el depósito.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = DepositoDTO.class)))
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @ErrorCapacidadInsuficiente
    @PostMapping("/depositos/{id}/stock")
    public ResponseEntity<DepositoDTO> guardarSobranteEnStock(
            @Parameter(description = "Id numérico del depósito.", example = "1") @PathVariable String id,
            @RequestBody GuardarStockRequest request) {
        return ResponseEntity.ok(fachada.guardarSobranteEnStock(
                id, request.donacionID(), request.productoID(), request.cantidad()));
    }

    // ---------------- ENTREGAS ----------------

    @Operation(tags = "Entregas", summary = "Reporta la entrega de un paquete",
            description = "Notifica a Donadores y Entidades que se satisfizo la necesidad, "
                    + "actualiza la donación a ACEPTADA en Donaciones y marca la asignación "
                    + "como COMPLETADA. Una entrega se reporta una sola vez: repetirla responde 409.")
    @ApiResponse(responseCode = "201", description = "Entrega registrada.")
    @ErrorSolicitudInvalida
    @ErrorNoEncontrado
    @ErrorOperacionNoPermitida
    @PostMapping("/entregas")
    public ResponseEntity<Void> registrarEntrega(@RequestBody PaqueteRequest request) {
        PaqueteDTO paqueteDTO = fachada.buscarPaquetePorID(request.paqueteId());
        fachada.reportarEntrega(paqueteDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    // ---------------- STOCK (Entrega 4 - Donadores) ----------------

    // Donadores consulta cuánto stock hay de un producto (agregado de todos los depósitos).
    @Operation(tags = "Stock", summary = "Stock disponible de un producto",
            description = "Suma el stock del producto en todos los depósitos. Devuelve 0 si no "
                    + "hay, no un error.")
    @ApiResponse(responseCode = "200", description = "Cantidad disponible.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = StockDisponibleDTO.class)))
    @GetMapping("/stock/{productoID}")
    public ResponseEntity<StockDisponibleDTO> stockDisponible(
            @Parameter(description = "Id del producto, el mismo que usa Donaciones.", example = "15")
            @PathVariable String productoID) {
        int disponible = fachada.stockDisponible(productoID);
        return ResponseEntity.ok(new StockDisponibleDTO(productoID, disponible));
    }

    // Donadores pide asignar stock a una necesidad (origen SOLICITUD_DONADORES).
    // 201 -> se creó la asignación (por la cantidad que Logística pudo cubrir).
    // 204 -> no había nada para asignar (sin stock, o cantidad nula/cero). NO es un error
    // Se devuelve una LISTA porque el stock puede venir de varias donaciones y un paquete
    // pertenece a una sola: en ese caso se crea una asignación por donación de origen.
    @Operation(tags = "Stock", summary = "Asigna stock a una necesidad, a pedido de Donadores",
            description = """
                    Logística decide cuánto se asigna: consume el mínimo entre lo disponible y lo \
                    solicitado, y quien pide solo propone una cantidad.

                    Devuelve una lista porque un paquete pertenece a una sola donación: si el pedido \
                    se cubre con stock de varias, se crea una asignación por cada donación de origen. \
                    Las asignaciones nacen con origen SOLICITUD_DONADORES.""")
    @ApiResponse(responseCode = "201", description = "Asignaciones creadas, una por donación de origen.",
            content = @Content(mediaType = JSON,
                    array = @ArraySchema(schema = @Schema(implementation = AsignacionDTO.class))))
    @ApiResponse(responseCode = "204",
            description = "No había nada para asignar: sin stock del producto, o cantidad nula o "
                    + "cero. No es un error, para no tumbar el alta de la necesidad en el otro módulo.")
    @ErrorSolicitudInvalida
    @PostMapping("/stock/{productoID}/asignaciones")
    public ResponseEntity<List<AsignacionDTO>> asignarDesdeStock(
            @Parameter(description = "Id del producto a asignar.", example = "15") @PathVariable String productoID,
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

    @Operation(tags = "Asignaciones", summary = "Lista todas las asignaciones",
            description = "Cada una incluye su estado y su origen, que Incentivos usa para "
                    + "distinguir el matchmaking de las solicitudes de Donadores.")
    @ApiResponse(responseCode = "200", description = "Lista de asignaciones, vacía si no hay ninguna.",
            content = @Content(mediaType = JSON,
                    array = @ArraySchema(schema = @Schema(implementation = AsignacionDTO.class))))
    @GetMapping("/asignaciones")
    public ResponseEntity<List<AsignacionDTO>> obtenerTodasLasAsignaciones() {
        return ResponseEntity.ok(fachada.buscarTodasLasAsignaciones());
    }

    @Operation(tags = "Asignaciones", summary = "Busca una asignación por id")
    @ApiResponse(responseCode = "200", description = "La asignación.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AsignacionDTO.class)))
    @ErrorNoEncontrado
    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorId(
            @Parameter(description = "Id de la asignación (UUID).",
                    example = "3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b") @PathVariable String id) {
        return ResponseEntity.ok(fachada.buscarAsignacionPorID(id));
    }

    @Operation(tags = "Asignaciones", summary = "Busca la asignación de un paquete")
    @ApiResponse(responseCode = "200", description = "La asignación del paquete.",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AsignacionDTO.class)))
    @ErrorNoEncontrado
    @GetMapping("/asignaciones/paquete/{paqueteID}")
    public ResponseEntity<AsignacionDTO> buscarAsignacionPorPaquete(
            @Parameter(description = "Id numérico del paquete.", example = "42") @PathVariable String paqueteID) {
        return ResponseEntity.ok(fachada.buscarAsignacionPorPaqueteID(paqueteID));
    }

    // ---------------- TESTING ----------------

    @Operation(tags = "Testing", summary = "Limpia la base de Logística",
            description = "Borra asignaciones, paquetes y depósitos. Solo para preparar la demostración: "
                    + "no hay vuelta atrás.")
    @ApiResponse(responseCode = "200", description = "Base limpiada.",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(type = "string")))
    @DeleteMapping("/testing/reset")
    public ResponseEntity<String> limpiarBaseDeDatos() {
        fachada.limpiarBaseDeDatos();
        return ResponseEntity.ok("Base de datos de Logística limpiada exitosamente");
    }
}
