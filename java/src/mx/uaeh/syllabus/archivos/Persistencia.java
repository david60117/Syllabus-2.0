package mx.uaeh.syllabus.archivos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

/**
 * Guardado del sistema en datos/base.json (datos escolares + cuentas + datos de la institución), el mismo archivo
 * y formato que usan las versiones de Python y JavaScript. La primera vez se parte de los datos de ejemplo.
 */
public class Persistencia {
    /** Eventos que no cambian datos y por tanto no provocan guardado. */
    public static final Set<String> NO_GUARDAN = Set.of("error_validacion", "datos_restaurados");

    private final Path base;
    private final Path semilla;
    private final Path usuariosMuestra;

    public Persistencia(Path carpetaDatos) {
        this(carpetaDatos.resolve("base.json"), carpetaDatos.resolve("solicitudes_pequenas.json"), carpetaDatos.resolve("usuarios.json"));
    }

    public Persistencia(Path base, Path semilla, Path usuariosMuestra) {
        this.base = base;
        this.semilla = semilla;
        this.usuariosMuestra = usuariosMuestra;
    }

    /** Carpeta datos/ junto a la carpeta de trabajo o en la superior (se ejecuta desde java/). */
    public static Persistencia predeterminada() {
        for (String c : new String[]{"datos", "../datos"}) {
            if (Files.isDirectory(Path.of(c))) {
                return new Persistencia(Path.of(c));
            }
        }
        return new Persistencia(Path.of("..", "datos"));
    }

    public Path getBase() {
        return base;
    }

    /** Lo que se abrió: cuentas y datos de la institución. */
    public record Estado(Autenticacion cuentas, Map<String, Object> institucion) {
    }

    /** Restaura la base guardada o, si no existe, carga los datos de ejemplo; sincroniza las cuentas. */
    @SuppressWarnings("unchecked")
    public Estado abrir(GestorEscolar gestor) {
        Autenticacion cuentas;
        Map<String, Object> institucion = new LinkedHashMap<>();
        if (Files.exists(base)) {
            Map<String, Object> contenido;
            try {
                contenido = (Map<String, Object>) Json.leer(Files.readString(base, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "no se pudo leer " + base + ": " + e.getMessage());
            }
            gestor.restaurar((Map<String, Object>) contenido.get("datos"));
            cuentas = new Autenticacion(Autenticacion.desdeLista((List<Object>) contenido.get("usuarios")));
            if (contenido.get("institucion") instanceof Map<?, ?> m) {
                institucion.putAll((Map<String, Object>) m);
            }
        } else {
            gestor.cargar(Archivos.cargarJson(semilla));
            cuentas = new Autenticacion(usuariosMuestra);
        }
        cuentas.sincronizar(gestor);
        return new Estado(cuentas, institucion);
    }

    /** Escritura atómica: primero a un temporal y luego se reemplaza, para no dejar el archivo a medias. */
    public Path guardar(GestorEscolar gestor, Autenticacion cuentas, Map<String, Object> institucion) throws IOException {
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("institucion", institucion == null ? Map.of() : institucion);
        todo.put("datos", gestor.aDiccionario());
        todo.put("usuarios", cuentas.aLista());
        if (base.toAbsolutePath().getParent() != null) {
            Files.createDirectories(base.toAbsolutePath().getParent());
        }
        Path temporal = base.resolveSibling(base.getFileName() + ".tmp");
        Files.writeString(temporal, Json.escribir(todo), StandardCharsets.UTF_8);
        try {
            Files.move(temporal, base, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporal, base, StandardCopyOption.REPLACE_EXISTING);
        }
        return base;
    }
}
