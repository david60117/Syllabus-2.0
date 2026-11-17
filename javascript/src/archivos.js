/** Lectura del JSON de entrada y exportación de reportes (Node). */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, basename } from 'node:path';
import { ErrorValidacion } from './errores.js';
import { aCsv } from './reportes.js';
import { parsearJson } from './estructura.js';

export { LLAVES_RAIZ, validarEstructura, parsearJson } from './estructura.js';

export async function cargarJson(ruta) {
  const texto = await readFile(ruta, 'utf8').catch((e) => {
    throw new ErrorValidacion('ESTRUCTURA_INVALIDA', e.code === 'ENOENT' ? `no existe el archivo ${ruta}` : `no se pudo leer ${ruta}: ${e.message}`);
  });
  return parsearJson(texto.replace(/^﻿/, ''), basename(ruta));
}

export async function exportarJson(datos, ruta) {
  await writeFile(ruta, JSON.stringify(datos, null, 2), 'utf8');
  return ruta;
}

export async function exportarCsv(filas, ruta) {
  await writeFile(ruta, '﻿' + aCsv(filas), 'utf8'); // BOM para que Excel muestre los acentos
  return ruta;
}

export async function exportarReportes(reportes, errores, carpeta) {
  await mkdir(carpeta, { recursive: true });
  const rutas = await Promise.all(Object.entries(reportes).flatMap(([nombre, filas]) => [
    exportarJson(filas, join(carpeta, `${nombre}.json`)),
    exportarCsv(filas, join(carpeta, `${nombre}.csv`)),
  ]));
  return [...rutas, await exportarJson(errores, join(carpeta, 'errores.json'))];
}
