package mx.uaeh.syllabus.modelo;

import java.util.List;

/** Resultado de un estudiante en un curso. Los valores son brutos (sin redondear); null = pendiente. */
public record ResumenCurso(String cursoId, String asignatura, String grupo, List<Double> parciales, Double bruto,
                           String estatus) {
}
