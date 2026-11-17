package mx.uaeh.syllabus.modelo;

public record Estudiante(String numCuenta, String apellidos, String nombre, String grupo) {
    public String nombreCompleto() {
        return apellidos + " " + nombre;
    }
}
