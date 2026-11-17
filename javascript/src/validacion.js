import { ErrorValidacion } from './errores.js';

export const RE_CUENTA = /^\d{6}$/;
export const RE_GRUPO = /^\d{3}$/;
export const RE_CURSO = /^C\d{2,}$/;
export const RE_PARCIAL = /^P\d{2,}$/;
export const RE_EVALUACION = /^EV\d{2,}$/;
export const RE_DOCENTE = /^D\d{2,}$/;
export const RE_NOMBRE = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$/;

const esObjeto = (r) => r !== null && typeof r === 'object' && !Array.isArray(r);
const vacio = (x) => x === null || x === undefined || (typeof x === 'string' && !x.trim());

/** Formatea un número como lo imprime Python (8 → "8.0") para que los mensajes coincidan entre versiones. */
export const pyNum = (v) => (typeof v === 'number' && Number.isInteger(v) ? v.toFixed(1) : String(v));

export function campos(registro, ...nombres) {
  if (!esObjeto(registro)) throw new ErrorValidacion('TIPO_INVALIDO', 'el registro debe ser un objeto');
  const faltan = nombres.filter((n) => vacio(registro[n]));
  if (faltan.length) throw new ErrorValidacion('CAMPO_FALTANTE', `falta ${faltan.join(', ')}`);
}

export function texto(valor, nombre) {
  if (typeof valor !== 'string') throw new ErrorValidacion('TIPO_INVALIDO', `${nombre} debe ser texto`);
  return valor.trim();
}

export function numero(valor, nombre) {
  if (typeof valor !== 'number' || !Number.isFinite(valor)) {
    const repr = typeof valor === 'string' ? `'${valor}'` : JSON.stringify(valor);
    throw new ErrorValidacion('TIPO_INVALIDO', `${nombre} debe ser numérico: ${repr}`);
  }
  return valor;
}

export function formato(valor, patron, nombre) {
  if (!patron.test(valor)) throw new ErrorValidacion('FORMATO_INVALIDO', `${nombre} con formato inválido: ${valor}`);
  return valor;
}

export function decimales(valor, maximo, nombre) {
  if (Math.abs(Number(valor.toFixed(maximo)) - valor) > 1e-9) {
    throw new ErrorValidacion('FORMATO_INVALIDO', `${nombre} admite máximo ${maximo} decimal(es): ${pyNum(valor)}`);
  }
}

export function peso(valor, nombre = 'peso') {
  const v = numero(valor, nombre);
  if (!(v > 0 && v <= 1)) {
    throw new ErrorValidacion('PORCENTAJES_INVALIDOS', `${nombre} debe ser mayor que 0 y hasta 1: ${pyNum(v)}`);
  }
  decimales(v, 2, nombre);
  return v;
}

export function calificacion(valor) {
  if (valor === null || valor === undefined) throw new ErrorValidacion('CAMPO_FALTANTE', 'falta valor');
  const v = numero(valor, 'valor');
  if (!(v >= 0 && v <= 10)) {
    throw new ErrorValidacion('CALIFICACION_FUERA_DE_RANGO', `la calificación debe estar entre 0 y 10: ${pyNum(v)}`);
  }
  decimales(v, 1, 'valor');
  return v;
}
