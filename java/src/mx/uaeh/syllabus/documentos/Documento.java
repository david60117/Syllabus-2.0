package mx.uaeh.syllabus.documentos;

import java.util.List;
import java.util.Map;

/**
 * Reporte institucional: encabezado (institución, programa, ciclo, título, datos del alcance, emisión y quién lo
 * emite), cuerpo en tabla, resumen y, si aplica, firmas y nota. Es lo que se ve en pantalla y lo que se exporta.
 */
public record Documento(String tipo, Encabezado encabezado, List<Columna> columnas, List<Map<String, Object>> filas,
                        List<Dato> resumen, List<String> firmas, String nota) {

    /** Par etiqueta → valor (campos del encabezado y cajas del resumen). */
    public record Dato(String etiqueta, Object valor) {
    }

    public record Columna(String clave, String titulo) {
    }

    public record Encabezado(String institucion, String programa, String ciclo, String titulo, List<Dato> campos,
                             String emision, String emitidoPor) {
        /** Valor de un campo del encabezado (por ejemplo "Grupo"); null si no está. */
        public Object campo(String etiqueta) {
            return campos.stream().filter(d -> d.etiqueta().equals(etiqueta)).map(Dato::valor).findFirst().orElse(null);
        }
    }

    public String titulo() {
        return encabezado.titulo();
    }
}
