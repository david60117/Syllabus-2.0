/**
 * Respaldo del sistema en datos/base.json (datos escolares + cuentas + encabezado de reportes). Es el mismo archivo y el
 * mismo formato que usan las versiones Python y Java. Aquí solo hay transformaciones puras; el servidor lo lee y escribe.
 */
import { sincronizar } from './autenticacion.js';
import { INSTITUCION } from './documentos.js';

/** Eventos que no cambian datos: no disparan un guardado. */
export const NO_GUARDAN = new Set(['error_validacion', 'datos_restaurados']);

export const paraGuardar = (gestor, usuarios, institucion = {}) => ({ institucion, datos: gestor.aObjeto(), usuarios });

/** Restaura un respaldo en el gestor y devuelve { usuarios, institucion }. */
export function abrir(gestor, contenido) {
  gestor.restaurar(contenido.datos);
  const usuarios = contenido.usuarios.map((u) => ({ ...u }));
  sincronizar(usuarios, gestor);
  return { usuarios, institucion: { ...INSTITUCION, ...(contenido.institucion ?? {}) } };
}

/** Primera vez: datos de ejemplo y usuarios de muestra, con una cuenta para cada alumno y docente. */
export function desdeSemilla(gestor, datos, usuariosMuestra) {
  gestor.cargar(datos);
  const usuarios = usuariosMuestra.map((u) => ({ ...u }));
  sincronizar(usuarios, gestor);
  return { usuarios, institucion: { ...INSTITUCION } };
}
