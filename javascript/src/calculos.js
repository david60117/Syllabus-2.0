/**
 * Reglas de cálculo, redondeo, orden y filtros. Funciones puras: reciben datos y devuelven datos nuevos.
 * Para evitar el ruido de IEEE 754 los valores se limpian a 10 decimales y se comparan como enteros (BigInt).
 */
const ESCALA = 10_000_000_000n; // 10 decimales
const SIETE = 7n * ESCALA;
const SIETE_CINCO = 75n * (ESCALA / 10n);

/** Valor mostrado con 2 decimales: se exporta como número en JSON y como "8.30" en CSV. */
export class Dec {
  constructor(n) { this.n = n; }
  valueOf() { return this.n; }
  toJSON() { return this.n; }
  toString() { return this.n.toFixed(2); }
}

export function limpiar10(x) {
  const [entero, frac = ''] = Math.abs(x).toFixed(10).split('.');
  const v = BigInt(entero) * ESCALA + BigInt(frac.padEnd(10, '0'));
  return x < 0 ? -v : v;
}

const aDec = (centesimos) => new Dec(Number(centesimos) / 100);
export const redondear2 = (x) => aDec((limpiar10(x) + ESCALA / 200n) / (ESCALA / 100n));
export const truncar2 = (x) => aDec(limpiar10(x) / (ESCALA / 100n));
/** Redondeo institucional: >= 7.0 mitad hacia arriba, < 7.0 truncado. */
export const mostrar = (x) => (limpiar10(x) >= SIETE ? redondear2(x) : truncar2(x));
/** Suma "limpia" a 10 decimales, para comparar pesos contra 1.0. */
export const normalizar = (x) => Number(limpiar10(x)) / Number(ESCALA);

export const evaluarEstatusCurso = (bruto) => (limpiar10(bruto) >= SIETE ? 'APROBADO' : 'REPROBADO');

export function evaluarRiesgo(promediosCursos, promGeneral) {
  const pg = limpiar10(promGeneral);
  if (promediosCursos.some((c) => limpiar10(c) < SIETE) || pg < SIETE) return 'ALTO';
  if (pg >= SIETE && pg <= SIETE_CINCO) return 'MEDIO';
  return 'SIN_RIESGO';
}

export function motivoRiesgo(promediosCursos, promGeneral) {
  const reprobados = promediosCursos.filter((c) => limpiar10(c) < SIETE).length;
  if (reprobados) return `${reprobados} curso(s) reprobado(s)`;
  if (limpiar10(promGeneral) < SIETE) return 'Promedio general menor a 7.0';
  if (limpiar10(promGeneral) <= SIETE_CINCO) return 'Promedio general entre 7.0 y 7.5';
  return 'Sin riesgo';
}

export const suma = (xs) => xs.reduce((a, b) => a + b, 0);

export function estadisticas(promedios) {
  if (!promedios.length) {
    return { estudiantes: 0, promedio: null, maximo: null, minimo: null, aprobados: 0, reprobados: 0, aprobacion: null };
  }
  const aprobados = promedios.filter((p) => limpiar10(p) >= SIETE).length;
  return {
    estudiantes: promedios.length,
    promedio: mostrar(suma(promedios) / promedios.length),
    maximo: mostrar(Math.max(...promedios)),
    minimo: mostrar(Math.min(...promedios)),
    aprobados,
    reprobados: promedios.length - aprobados,
    aprobacion: redondear2((aprobados / promedios.length) * 100),
  };
}

/** Texto sin acentos y en minúsculas; NFD también convierte la ñ en n. */
export const claveTexto = (s) => s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '');

/** Comparación por punto de código (igual que Python), no por configuración regional. */
export const comparar = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
export const compararTuplas = (a, b) => a.reduce((r, x, i) => r || comparar(x, b[i]), 0);

export function ordenar(filas, columna = 'apellidos', descendente = false) {
  const clave = ['apellidos', 'nombre'].includes(columna)
    ? (f) => claveTexto(f[columna])
    : (f) => (f[columna] === null || f[columna] === undefined ? -1 : Number(f[columna]));
  const porCuenta = [...filas].sort((a, b) => comparar(a.num_cuenta, b.num_cuenta));
  const ordenadas = porCuenta.sort((a, b) => comparar(clave(a), clave(b)));
  if (!descendente) return ordenadas;
  // el desempate sigue siendo num_cuenta ascendente: se agrupa por valor y se invierten los grupos
  const grupos = ordenadas.reduce((m, f) => m.set(clave(f), [...(m.get(clave(f)) ?? []), f]), new Map());
  return [...grupos.values()].reverse().flat();
}

const reglasEstado = {
  todos: () => true,
  aprobado: (f) => f.estatus === 'APROBADO',
  reprobado: (f) => f.estatus === 'REPROBADO',
  riesgo: (f) => ['ALTO', 'MEDIO'].includes(f.riesgo),
  pendiente: (f) => f.estatus === 'PENDIENTE',
};
export const filtroEstado = (estado) => reglasEstado[estado] ?? reglasEstado.todos;

export const filtroRango = (minima = null, maxima = null) => (f) => {
  if (f.promedio === null || f.promedio === undefined) return minima === null && maxima === null;
  const p = Number(f.promedio);
  return (minima === null || p >= minima) && (maxima === null || p <= maxima);
};

/**
 * Calificación final que se asienta en el acta (entera, regla del .5).
 * - Aprobatoria (promedio >= 7.0): se redondea al entero más próximo; .5 o más sube (8.5 → 9, 8.4 → 8).
 * - Reprobatoria (promedio < 7.0): nunca se redondea hacia arriba, se trunca (6.9 → 6).
 * Los parciales y el promedio del curso se conservan con dos decimales.
 */
export function calificacionFinal(bruto) {
  if (bruto === null || bruto === undefined) return null;
  const x = limpiar10(bruto);
  return Number(x >= SIETE ? (x + ESCALA / 2n) / ESCALA : x / ESCALA);
}

/** Promedio de las calificaciones finales asentadas (enteras), a dos decimales: el del kárdex. */
export function promedioFinales(brutos) {
  const finales = brutos.filter((b) => b !== null && b !== undefined).map(calificacionFinal);
  return finales.length ? redondear2(suma(finales) / finales.length) : null;
}
