package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.exceptions.CapacidadInsuficienteException;
import ar.edu.utn.dds.k3003.exceptions.OperacionNoPermitidaException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Depósito donde se almacenan los paquetes donados.
 *
 * <p>Las reglas de capacidad y de consumo de stock viven acá y no en los servicios: son del
 * depósito, y tenerlas en la entidad las hace verificables sin levantar la aplicación.
 */
@Entity
public class Deposito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private String direccion;
    private Integer capacidadMaxima;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "deposito_id")
    private List<Paquete> stock = new ArrayList<>();
    private TipoAlgoritmoEnum algoritmo;

    public Deposito() {
    }

    public Deposito(String nombre, String direccion, Integer capacidad) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.capacidadMaxima = capacidad;
        this.stock = new ArrayList<>();
        this.algoritmo = null;
    }

    // ---------------- Comportamiento ----------------

    /** Unidades que hoy ocupan el depósito, sumando la cantidad de cada paquete en stock. */
    public int unidadesOcupadas() {
        return stock.stream()
                .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
                .sum();
    }

    /** Sin capacidad definida no hay límite. */
    public boolean tieneEspacioPara(int unidades) {
        return capacidadMaxima == null || unidadesOcupadas() + unidades <= capacidadMaxima;
    }

    /** Falla si las unidades no entran, detallando cuántas faltan liberar. */
    public void verificarEspacioPara(int unidades) {
        if (!tieneEspacioPara(unidades)) {
            throw new CapacidadInsuficienteException(id, capacidadMaxima, unidadesOcupadas(), unidades);
        }
    }

    /** Guarda unidades de una donación en el stock, siempre que entren. */
    public void recibirEnStock(String donacionID, String productoID, int cantidad) {
        verificarEspacioPara(cantidad);
        stock.add(new Paquete(donacionID, productoID, cantidad));
    }

    /** Unidades de un producto disponibles en este depósito. */
    public int stockDe(String productoID) {
        return stock.stream()
                .filter(p -> productoID.equals(p.getProductoID()))
                .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
                .sum();
    }

    /**
     * Saca del stock hasta {@code maximo} unidades del producto y devuelve cuántas se
     * consumieron de cada donación de origen, en el orden en que se las fue tomando.
     *
     * <p>El desglose por donación importa porque un paquete pertenece a una sola donación: el
     * que llama necesita crear un paquete y una asignación por cada una, si no, al reportar la
     * entrega sólo se marcaría aceptada una de ellas. Un paquete consumido por completo se
     * elimina, y uno consumido en parte se parte.
     */
    public Map<String, Integer> consumirDeStock(String productoID, int maximo) {
        Map<String, Integer> consumidoPorDonacion = new LinkedHashMap<>();
        int restante = maximo;

        Iterator<Paquete> it = stock.iterator();
        while (it.hasNext() && restante > 0) {
            Paquete paquete = it.next();
            if (!productoID.equals(paquete.getProductoID())) {
                continue;
            }
            int disponible = paquete.getCantidad() == null ? 0 : paquete.getCantidad();
            if (disponible <= 0) {
                it.remove(); // paquete vacío: se limpia y no cuenta como consumo
                continue;
            }
            int consumido = Math.min(disponible, restante);
            consumidoPorDonacion.merge(paquete.getDonacionID(), consumido, Integer::sum);
            if (consumido == disponible) {
                it.remove(); // orphanRemoval borra la fila
            } else {
                paquete.setCantidad(disponible - consumido);
            }
            restante -= consumido;
        }
        return consumidoPorDonacion;
    }

    /**
     * Modifica los datos propios del depósito. La capacidad no puede quedar por debajo de lo que
     * ya almacena: dejaría al depósito sobrepasado, con un stock que no entra en él.
     * El stock y el algoritmo de matchmaking no se tocan desde acá.
     */
    public void actualizarDatos(String nombre, String direccion, Integer nuevaCapacidad) {
        if (nuevaCapacidad != null && nuevaCapacidad < unidadesOcupadas()) {
            throw new OperacionNoPermitidaException("No se puede reducir la capacidad del depósito "
                    + id + " a " + nuevaCapacidad + ": ya almacena " + unidadesOcupadas() + " unidades");
        }
        this.nombre = nombre;
        this.direccion = direccion;
        this.capacidadMaxima = nuevaCapacidad;
    }

    /**
     * Un depósito sólo se elimina vacío. Con stock, la baja destruiría el registro de lo que
     * hay almacenado, mientras las donaciones de origen siguen vigentes en el resto del sistema.
     */
    public void verificarQueSePuedeEliminar() {
        if (unidadesOcupadas() > 0) {
            throw new OperacionNoPermitidaException("No se puede eliminar el depósito " + id
                    + ": todavía tiene " + unidadesOcupadas() + " unidades en stock");
        }
    }

    // ---------------- Accesores ----------------

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public Integer getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(Integer capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    /** La colección de stock se modifica in situ (agregar o quitar paquetes), nunca se reemplaza. */
    public List<Paquete> getStock() {
        return stock;
    }

    public TipoAlgoritmoEnum getAlgoritmo() {
        return algoritmo;
    }

    public void setAlgoritmo(TipoAlgoritmoEnum algoritmo) {
        this.algoritmo = algoritmo;
    }
}
