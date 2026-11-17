package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

class AutenticacionTest {
    private final Autenticacion auth = new Autenticacion(Path.of("..", "datos", "usuarios.json"));

    private static GestorEscolar gestor() {
        GestorEscolar g = new GestorEscolar();
        g.cargar(Archivos.cargarJson(Path.of("..", "datos", "solicitudes_pequenas.json")));
        return g;
    }

    @Test
    void unUsuarioPorRol() {
        assertEquals(List.of("Administrador", "Alumno", "Docente"), auth.roles().stream().sorted().toList());
    }

    @Test
    void usuariosDeMuestra() {
        GestorEscolar g = gestor();
        assertEquals("Administrador", auth.iniciarSesion("admin", "Admin123", g).rol());
        assertEquals("D01", auth.iniciarSesion("PAOLA.AVILA@syllabus.mx", "Docente123", g).referencia());
        assertEquals("500001", auth.iniciarSesion("500001", "Alumno123", g).referencia());
    }

    @Test
    void contrasenaIncorrecta() {
        assertEquals("CREDENCIALES_INVALIDAS",
                assertThrows(ErrorValidacion.class, () -> auth.iniciarSesion("admin", "otra", gestor())).getCodigo());
    }

    @Test
    void cuentaQueNoEstaEnLosDatos() {
        assertEquals("REFERENCIA_INEXISTENTE",
                assertThrows(ErrorValidacion.class, () -> auth.iniciarSesion("pavila", "Docente123", new GestorEscolar())).getCodigo());
    }
}
