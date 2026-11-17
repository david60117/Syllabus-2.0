package mx.uaeh.syllabus.logica;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import mx.uaeh.syllabus.archivos.Json;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

/**
 * Cuentas de usuario e inicio de sesión. Como en un sistema escolar real, cada alumno y docente tiene su cuenta:
 * se crea sola al darlo de alta, con usuario = número de cuenta (alumno) o ID en minúsculas (docente) y contraseña
 * inicial igual al usuario; la primera vez que entra, el sistema le pide cambiarla.
 */
public final class Autenticacion {
    private static final Pattern RE_CONTRASENA = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private final List<Cuenta> usuarios;

    public Autenticacion(List<Cuenta> usuarios) {
        this.usuarios = new ArrayList<>(usuarios);
    }

    @SuppressWarnings("unchecked")
    public Autenticacion(Path archivo) {
        try {
            Map<String, Object> datos = (Map<String, Object>) Json.leer(Files.readString(archivo, StandardCharsets.UTF_8));
            usuarios = desdeLista((List<Object>) datos.get("usuarios"));
        } catch (IOException | RuntimeException e) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "no se pudieron leer los usuarios: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Cuenta> desdeLista(List<Object> lista) {
        List<Cuenta> cuentas = new ArrayList<>();
        lista.forEach(m -> cuentas.add(Cuenta.desdeMapa((Map<String, Object>) m)));
        return cuentas;
    }

    /** Busca datos/usuarios.json junto a la carpeta de trabajo o en la carpeta superior. */
    public static Autenticacion predeterminada() {
        for (String base : new String[]{"datos", "../datos"}) {
            Path p = Path.of(base, "usuarios.json");
            if (Files.exists(p)) {
                return new Autenticacion(p);
            }
        }
        throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "no se encontró datos/usuarios.json");
    }

    public List<Cuenta> getUsuarios() {
        return usuarios;
    }

    public List<Map<String, Object>> aLista() {
        return usuarios.stream().map(Cuenta::aMapa).toList();
    }

    public List<String> roles() {
        return usuarios.stream().map(Cuenta::rol).toList();
    }

    public static String resumen(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Cuenta buscar(String clave) {
        String c = clave == null ? "" : clave.strip().toLowerCase();
        return usuarios.stream().filter(u -> c.equals(u.usuario().toLowerCase())
                || (u.correo() != null && c.equals(u.correo().toLowerCase()))).findFirst().orElse(null);
    }

    /** Devuelve la cuenta si la contraseña coincide y la persona existe en los datos cargados. */
    public Cuenta iniciarSesion(String usuario, String contrasena, GestorEscolar gestor) {
        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty()) {
            throw new ErrorValidacion("CAMPO_FALTANTE", "escribe tu usuario y tu contraseña");
        }
        Cuenta c = buscar(usuario);
        if (c == null || !resumen(contrasena).equals(c.contrasenaSha256())) {
            throw new ErrorValidacion("CREDENCIALES_INVALIDAS", "usuario o contraseña incorrectos");
        }
        if ("Docente".equals(c.rol()) && !gestor.getDocentes().containsKey(c.referencia())) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "el docente " + c.referencia() + " no está en los datos cargados");
        }
        if ("Alumno".equals(c.rol()) && !gestor.getEstudiantes().containsKey(c.referencia())) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "el estudiante " + c.referencia() + " no está en los datos cargados");
        }
        return c;
    }

    public Cuenta crearCuenta(String usuario, String rol, String referencia, String nombre) {
        if (buscar(usuario) != null) {
            throw new ErrorValidacion("ID_DUPLICADO", "el usuario " + usuario + " ya existe");
        }
        Cuenta c = new Cuenta(usuario, null, resumen(usuario), rol, referencia, nombre, true);
        usuarios.add(c);
        return c;
    }

    private static String llave(String rol, String ref) {
        return rol + "|" + ref;
    }

    /**
     * Crea la cuenta de cada alumno y docente que no tenga una, quita las de quienes ya no existen y actualiza
     * los nombres. Devuelve cuántas cuentas se crearon.
     */
    public int sincronizar(GestorEscolar gestor) {
        Map<String, String> nombres = new HashMap<>();
        gestor.getEstudiantes().forEach((n, e) -> nombres.put(llave("Alumno", n), e.nombreCompleto()));
        gestor.getDocentes().forEach((d, x) -> nombres.put(llave("Docente", d), x.nombre()));
        usuarios.removeIf(u -> !"Administrador".equals(u.rol()) && !nombres.containsKey(llave(u.rol(), u.referencia())));
        Set<String> conCuenta = new TreeSet<>();
        for (Cuenta u : usuarios) {
            conCuenta.add(llave(u.rol(), u.referencia()));
            String nombre = nombres.get(llave(u.rol(), u.referencia()));
            if (nombre != null) {
                u.setNombre(nombre);
            }
        }
        int creadas = 0;
        for (String k : new TreeSet<>(nombres.keySet())) {
            if (!conCuenta.contains(k)) {
                String[] p = k.split("\\|", 2);
                crearCuenta("Alumno".equals(p[0]) ? p[1] : p[1].toLowerCase(), p[0], p[1], nombres.get(k));
                creadas++;
            }
        }
        return creadas;
    }

    public static void cambiarContrasena(Cuenta cuenta, String actual, String nueva, String confirmacion) {
        if (!resumen(actual).equals(cuenta.contrasenaSha256())) {
            throw new ErrorValidacion("CREDENCIALES_INVALIDAS", "la contraseña actual no es correcta");
        }
        if (!nueva.equals(confirmacion)) {
            throw new ErrorValidacion("FORMATO_INVALIDO", "la confirmación no coincide con la nueva contraseña");
        }
        if (!RE_CONTRASENA.matcher(nueva).matches()) {
            throw new ErrorValidacion("FORMATO_INVALIDO", "la contraseña debe tener al menos 8 caracteres, con letras y números");
        }
        if (nueva.equals(actual) || nueva.equalsIgnoreCase(cuenta.usuario())) {
            throw new ErrorValidacion("FORMATO_INVALIDO", "elige una contraseña distinta a la anterior y a tu usuario");
        }
        cuenta.setContrasena(resumen(nueva), false);
    }

    /** La contraseña vuelve a ser igual al usuario y se pedirá cambiarla al entrar. */
    public static String restablecer(Cuenta cuenta) {
        cuenta.setContrasena(resumen(cuenta.usuario()), true);
        return cuenta.usuario();
    }

    /**
     * Solo se puede mostrar la contraseña inicial (igual al usuario). Una contraseña elegida por el usuario
     * se guarda cifrada (SHA-256) y no se puede recuperar: se restablece.
     */
    public static String contrasenaVisible(Cuenta cuenta) {
        return resumen(cuenta.usuario()).equals(cuenta.contrasenaSha256()) ? cuenta.usuario() : null;
    }
}
