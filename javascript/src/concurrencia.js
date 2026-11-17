/**
 * Cálculo de resúmenes. En modo concurrente reparte los estudiantes en bloques entre hilos
 * (worker_threads) y combina los resultados con Promise.all (programación asíncrona).
 */
import { Worker } from 'node:worker_threads';
import { availableParallelism } from 'node:os';
import { comparar } from './calculos.js';
import { calcularSecuencial } from './reportes.js';

export async function calcularResumenes(gestor, modo = 'secuencial', trabajadores = availableParallelism()) {
  const cuentas = [...gestor.estudiantes.keys()].sort(comparar);
  if (modo !== 'concurrente' || cuentas.length < 2) return calcularSecuencial(gestor);
  const tam = Math.ceil(cuentas.length / trabajadores);
  const bloques = Array.from({ length: Math.ceil(cuentas.length / tam) }, (_, i) => cuentas.slice(i * tam, (i + 1) * tam));
  const datos = gestor.aObjeto();
  const partes = await Promise.all(bloques.map((cuentasBloque) => new Promise((resolver, rechazar) => {
    const w = new Worker(new URL('./trabajador.js', import.meta.url), { workerData: { datos, cuentas: cuentasBloque } });
    w.once('message', resolver);
    w.once('error', rechazar);
  })));
  return partes.flat();
}
