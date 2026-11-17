#!/usr/bin/env node
/** Línea de comandos: node src/main.js --cli --entrada <json> [--salida reportes] [--modo secuencial|concurrente] */
import { parseArgs } from 'node:util';
import { performance } from 'node:perf_hooks';
import { cargarJson, exportarReportes } from './archivos.js';
import { ErrorValidacion } from './errores.js';
import { GestorEscolar } from './gestor.js';
import { calcularResumenes } from './concurrencia.js';
import { generarTodos } from './reportes.js';

export async function ejecutarCli(entrada, salida, modo) {
  const gestor = new GestorEscolar();
  gestor.cargar(await cargarJson(entrada));
  const inicio = performance.now();
  const resumenes = await calcularResumenes(gestor, modo);
  const ms = performance.now() - inicio;
  const rutas = await exportarReportes(generarTodos(gestor, resumenes), gestor.errores, salida);
  console.log(`Estudiantes: ${gestor.estudiantes.size} · Cursos: ${gestor.cursos.size} · Calificaciones: ${gestor.calificaciones.size}`);
  console.log(`Registros rechazados: ${gestor.errores.length} (ver errores.json)`);
  console.log(`Cálculo de promedios (${modo}): ${ms.toFixed(2)} ms`);
  console.log(`Reportes generados: ${rutas.length} archivos en ${salida}`);
}

async function main() {
  const { values } = parseArgs({
    options: {
      cli: { type: 'boolean', default: false },
      entrada: { type: 'string' },
      salida: { type: 'string', default: 'reportes' },
      modo: { type: 'string', default: 'secuencial' },
    },
  });
  if (!['secuencial', 'concurrente'].includes(values.modo)) throw new Error('--modo debe ser secuencial o concurrente');
  if (!values.cli) {
    await import('./servidor.js');
    return;
  }
  if (!values.entrada) throw new Error('--cli requiere --entrada');
  await ejecutarCli(values.entrada, values.salida, values.modo);
}

main().catch((e) => {
  console.error(e instanceof ErrorValidacion ? `Error: ${e.message}` : `Error: ${e.message}`);
  process.exitCode = 1;
});
