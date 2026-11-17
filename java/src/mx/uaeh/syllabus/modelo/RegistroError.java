package mx.uaeh.syllabus.modelo;

/** Registro rechazado durante la carga o en la interfaz; se exporta en errores.json. */
public record RegistroError(Object caso, String tipo, String codigo, String detalle, Object registro) {
}
