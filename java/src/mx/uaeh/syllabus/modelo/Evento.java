package mx.uaeh.syllabus.modelo;

import java.util.Map;

public record Evento(String nombre, String hora, Map<String, String> datos) {
    public String dato(String clave) {
        return datos.getOrDefault(clave, "");
    }
}
