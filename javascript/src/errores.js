/** Error de validación con código del catálogo acordado (CAMPO_FALTANTE, REFERENCIA_INEXISTENTE...). */
export class ErrorValidacion extends Error {
  constructor(codigo, detalle) {
    super(`${codigo}: ${detalle}`);
    this.name = 'ErrorValidacion';
    this.codigo = codigo;
    this.detalle = detalle;
  }
}
