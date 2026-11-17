package mx.uaeh.syllabus.modelo;

import java.util.List;

public record ResumenEstudiante(String numCuenta, String apellidos, String nombre, String grupo,
                                List<ResumenCurso> cursos, Double general, String estatus, String riesgo,
                                String motivo, int pendientes) {

    public ResumenEstudiante conCursos(List<ResumenCurso> otros) {
        return new ResumenEstudiante(numCuenta, apellidos, nombre, grupo, otros, general, estatus, riesgo, motivo, pendientes);
    }
}
