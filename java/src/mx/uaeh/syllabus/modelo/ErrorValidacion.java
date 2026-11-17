package mx.uaeh.syllabus.modelo;

/** Error de validación con código del catálogo acordado (CAMPO_FALTANTE, REFERENCIA_INEXISTENTE...). */
public class ErrorValidacion extends RuntimeException {
    private final String codigo;
    private final String detalle;

    public ErrorValidacion(String codigo, String detalle) {
        super(codigo + ": " + detalle);
        this.codigo = codigo;
        this.detalle = detalle;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDetalle() {
        return detalle;
    }
}
