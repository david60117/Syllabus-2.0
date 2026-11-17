package mx.uaeh.syllabus.modelo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuenta de acceso. La contraseña se guarda como SHA-256 (hex), nunca en texto plano.
 * referencia es el id del docente o el número de cuenta del alumno (null para administración).
 */
public class Cuenta {
    private final String usuario;
    private final String correo;
    private String contrasenaSha256;
    private final String rol;
    private final String referencia;
    private String nombre;
    private boolean debeCambiar;

    public Cuenta(String usuario, String correo, String contrasenaSha256, String rol, String referencia, String nombre,
                  boolean debeCambiar) {
        this.usuario = usuario;
        this.correo = correo;
        this.contrasenaSha256 = contrasenaSha256;
        this.rol = rol;
        this.referencia = referencia;
        this.nombre = nombre;
        this.debeCambiar = debeCambiar;
    }

    private static String texto(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static Cuenta desdeMapa(Map<String, Object> m) {
        return new Cuenta(texto(m.get("usuario")), texto(m.get("correo")), texto(m.get("contrasena_sha256")),
                texto(m.get("rol")), texto(m.get("referencia")), texto(m.get("nombre")), Boolean.TRUE.equals(m.get("debe_cambiar")));
    }

    /** Mismo formato que guardan las versiones de Python y JavaScript. */
    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuario", usuario);
        m.put("correo", correo);
        m.put("contrasena_sha256", contrasenaSha256);
        m.put("rol", rol);
        m.put("referencia", referencia);
        m.put("nombre", nombre);
        m.put("debe_cambiar", debeCambiar);
        return m;
    }

    public String usuario() {
        return usuario;
    }

    public String correo() {
        return correo;
    }

    public String contrasenaSha256() {
        return contrasenaSha256;
    }

    public String rol() {
        return rol;
    }

    public String referencia() {
        return referencia;
    }

    public String nombre() {
        return nombre;
    }

    public boolean debeCambiar() {
        return debeCambiar;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setContrasena(String sha256, boolean debeCambiar) {
        this.contrasenaSha256 = sha256;
        this.debeCambiar = debeCambiar;
    }
}
