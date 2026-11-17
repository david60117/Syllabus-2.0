/** Validación de la estructura general del JSON (sirve en Node y en el navegador). */
import { ErrorValidacion } from './errores.js';

export const LLAVES_RAIZ = ['estudiantes', 'cursos', 'parciales', 'evaluaciones', 'inscripciones', 'calificaciones'];

/** Verifica la estructura general; la validación registro por registro es de validacion.js. */
export function validarEstructura(datos) {
  if (datos === null || typeof datos !== 'object' || Array.isArray(datos)) {
    throw new ErrorValidacion('ESTRUCTURA_INVALIDA', 'la raíz del JSON debe ser un objeto');
  }
  const faltan = LLAVES_RAIZ.filter((k) => !(k in datos));
  if (faltan.length) throw new ErrorValidacion('ESTRUCTURA_INVALIDA', `faltan las llaves ${faltan.join(', ')}`);
  const noListas = LLAVES_RAIZ.filter((k) => !Array.isArray(datos[k]));
  if (noListas.length) throw new ErrorValidacion('ESTRUCTURA_INVALIDA', `deben ser listas: ${noListas.join(', ')}`);
  return datos;
}

export function parsearJson(texto, nombre = 'entrada') {
  try {
    return validarEstructura(JSON.parse(texto));
  } catch (e) {
    if (e instanceof SyntaxError) throw new ErrorValidacion('ESTRUCTURA_INVALIDA', `JSON mal formado en ${nombre}: ${e.message}`);
    throw e;
  }
}
