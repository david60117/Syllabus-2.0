/** Piezas de diseño como funciones puras que devuelven HTML (mismo diseño que Figma y que la versión Python). */
import { Dec, ordenar } from '../calculos.js';
import { LUCIDE } from './iconos.js';

export const TEMAS = {
  Rojo: ['#8b2024', '#f9eeea'],
  Verde: ['#227e68', '#e6f2ef'],
  Azul: ['#3b85df', '#eaf2fc'],
  Morado: ['#7655be', '#f0ecf9'],
};

/** (ícono Lucide, sección) */
export const MENUS = {
  Administrador: [['inicio', 'Inicio'], ['estudiantes', 'Estudiantes'], ['grupos', 'Grupos y materias'], ['docentes', 'Docentes'],
    ['inscripciones', 'Inscripciones'], ['consultas', 'Consultas'], ['reportes', 'Reportes'], ['usuarios', 'Usuarios'],
    ['ajustes', 'Personalización']],
  Docente: [['inicio', 'Inicio'], ['cursos', 'Mis cursos'], ['plan', 'Plan de evaluación'], ['captura', 'Captura'],
    ['consultas', 'Consultas'], ['reportes', 'Reportes'], ['ajustes', 'Personalización']],
  Alumno: [['inicio', 'Inicio'], ['calificaciones', 'Mis calificaciones'], ['kardex', 'Kárdex'], ['ajustes', 'Personalización']],
};

/** Ícono por sección (para las tarjetas de acceso). */
export const ICONO_DE = Object.fromEntries(Object.values(MENUS).flat().map(([i, n]) => [n, i]));

/** Ícono lineal de Lucide: 24 × 24, trazo 1.8, sin relleno; toma el color del texto (currentColor). */
export const icono = (nombre, tam = 20) => `<svg class="lucide" width="${tam}" height="${tam}" viewBox="0 0 24 24" fill="none" stroke="currentColor"
  stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${LUCIDE[nombre] ?? LUCIDE.consultas}</svg>`;

export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

/** Texto sin guiones bajos para mostrar: "SIN_RIESGO" → "Sin riesgo", "datos_cargados" → "Datos cargados". */
export const legible = (texto) => String(texto ?? '').replace(/\b[A-Za-z]+(?:_[A-Za-z0-9]+)+\b/g, (p) => {
  const s = p.replace(/_/g, ' ').toLowerCase();
  return s[0].toUpperCase() + s.slice(1);
});

/** Nombre visible de cada reporte (el archivo conserva su nombre técnico). */
export const NOMBRES_REPORTE = {
  resumen_grupos: 'Resumen por grupo', estudiantes: 'Estudiantes', parciales_por_asignatura: 'Parciales por asignatura',
  estadisticas_cursos: 'Estadísticas por curso', riesgo_academico: 'Riesgo académico', kardex: 'Kárdex',
};

export const fmt = (x) => {
  if (x === null || x === undefined) return '—';
  if (x instanceof Dec) return String(x);
  if (typeof x === 'number') return Number.isInteger(x) ? String(x) : x.toFixed(2);
  return String(x);
};

export const encabezado = (ruta, titulo, subtitulo) => `
  <div class="ruta">${esc(ruta)}</div>
  <h1 class="titulo-vista">${esc(titulo)}</h1>
  <p class="subtitulo">${esc(subtitulo)}</p>`;

export const tarjeta = (titulo, cuerpo, sub = '', clase = '') => `
  <section class="tarjeta ${clase}">${titulo ? `<h3>${esc(titulo)}</h3>` : ''}${sub ? `<p class="sub">${esc(sub)}</p>` : ''}${cuerpo}</section>`;

export const rejilla = (n, piezas) => `<div class="rejilla c${n}">${piezas.join('')}</div>`;

export const mosaicos = (datos) => `<div class="rejilla c${datos.length} mosaicos">${datos
  .map(([t, v]) => `<div class="mosaico"><span>${esc(t)}</span><strong>${esc(fmt(v))}</strong></div>`).join('')}</div>`;

/** Campo con etiqueta: texto libre o lista de opciones. */
export const campo = (etiqueta, nombre, { valor = '', opciones = null, ancho = null, atributos = '' } = {}) => {
  const estilo = ancho ? ` style="width:${ancho}ch"` : '';
  const control = opciones
    ? `<select name="${nombre}"${estilo} ${atributos}>${opciones.map((o) => {
      const [v, t] = Array.isArray(o) ? o : [o, o];
      return `<option value="${esc(v)}"${String(v) === String(valor) ? ' selected' : ''}>${esc(t)}</option>`;
    }).join('')}</select>`
    : `<input type="text" name="${nombre}" value="${esc(valor)}"${estilo} ${atributos}>`;
  return `<label class="campo"><span>${esc(etiqueta)}</span>${control}</label>`;
};

export const boton = (texto, atributos = '', secundario = false) =>
  `<button type="button" class="boton${secundario ? ' secundario' : ''}" ${atributos}>${esc(texto)}</button>`;

/** Separa "HH:MM:SS  evento · detalle" en sus partes. */
export const partesEvento = (linea) => {
  const m = /^(\d{2}:\d{2}:\d{2})\s+(.*)$/.exec(linea);
  const [evento, ...resto] = (m ? m[2] : linea).split(' · ');
  return { hora: m ? m[1] : '', evento, detalle: resto.join(' · ') };
};

/** Lista de actividad como en Figma: punto de color, evento, detalle y hora. */
export const actividad = (lineas, n, vacio = 'Sin eventos todavía.') => (lineas.length
  ? `<ul class="actividad">${lineas.slice(0, n).map((l) => {
    const { hora, evento, detalle } = partesEvento(l);
    return `<li class="${evento === 'error_validacion' ? 'error' : 'ok'}"><span class="punto"></span>
      <div><strong>${esc(legible(evento))}</strong><small>${esc(legible(detalle))}</small></div><time>${esc(hora)}</time></li>`;
  }).join('')}</ul>`
  : `<p class="nota">${esc(vacio)}</p>`);

/** Tono del texto de estado, igual en las tres versiones: verde, rojo o ámbar. */
const TONOS = {
  APROBADO: 'verde', SIN_RIESGO: 'verde', GUARDADA: 'verde', CAPTURADA: 'verde', INSCRITO: 'verde',
  REPROBADO: 'rojo', ALTO: 'rojo', RECHAZADA: 'rojo',
  PENDIENTE: 'ambar', 'PENDIENTE DE CIERRE': 'ambar', MEDIO: 'ambar', ASIGNADO: 'verde',
  ACTIVA: 'verde', 'CONTRASEÑA INICIAL': 'ambar', 'EN CURSO': 'ambar', COMPLETO: 'verde',
};
export const COLUMNAS_ESTADO = ['estatus', 'riesgo', 'estado'];
export const tono = (valor) => TONOS[String(valor ?? '').toUpperCase()] ?? '';

/** Ícono de orden: dos chevrones simétricos; el de la dirección activa se resalta. */
const iconoOrden = (activo, desc) => `<svg class="icono-orden" viewBox="0 0 10 14" width="10" height="14" aria-hidden="true">
  <path class="${activo && !desc ? 'on' : ''}" d="M2 5.5 L5 2.5 L8 5.5"/><path class="${activo && desc ? 'on' : ''}" d="M2 8.5 L5 11.5 L8 8.5"/></svg>`;

const ORDENABLES = ['num_cuenta', 'apellidos', 'nombre', 'promedio'];

/**
 * Tabla con encabezados ordenables. orden = { col, desc } guardado por la app para cada tabla.
 * Las columnas de estado muestran su texto en verde, rojo o ámbar (como en Figma).
 */
export function tabla(id, columnas, filas, { orden = { col: 'apellidos', desc: false }, ordenables = ORDENABLES,
  seleccionable = false, seleccionada = null, clave = 'num_cuenta', sinOrden = false } = {}) {
  const conCuenta = filas.length > 0 && 'num_cuenta' in filas[0];
  const visibles = !sinOrden && conCuenta && orden.col in filas[0] ? ordenar(filas, orden.col, orden.desc) : filas;
  const cabeza = columnas.map(([k, t]) => {
    if (!(ordenables.includes(k) && (conCuenta || !filas.length))) return `<th>${esc(t)}</th>`;
    return `<th class="ordenable${k === orden.col ? ' activa' : ''}" data-orden="${id}" data-col="${k}"><span>${esc(t)}</span>${iconoOrden(k === orden.col, orden.desc)}</th>`;
  }).join('');
  const celda = (k, v) => {
    const clase = COLUMNAS_ESTADO.includes(k) ? tono(v) : '';
    return `<td${clase ? ` class="tono-${clase}"` : ''}>${esc(legible(fmt(v)))}</td>`;
  };
  const cuerpo = visibles.length
    ? visibles.map((f) => `<tr class="${seleccionable ? 'seleccionable' : ''}${seleccionada && f[clave] === seleccionada ? ' seleccionada' : ''}"
        ${seleccionable ? `data-fila="${id}" data-cuenta="${esc(f[clave])}"` : ''}>${columnas.map(([k]) => celda(k, f[k])).join('')}</tr>`).join('')
    : `<tr class="vacio"><td colspan="${columnas.length}">Sin resultados</td></tr>`;
  return `<div class="tabla-caja" id="tabla-${id}"><table><thead><tr>${cabeza}</tr></thead><tbody>${cuerpo}</tbody></table></div>`;
}

export const pestanas = (id, titulos, activa) => `<div class="pestanas">${titulos
  .map((t, i) => `<button type="button" class="pestana${i === activa ? ' activa' : ''}" data-pestana="${id}" data-i="${i}">${esc(t)}</button>`).join('')}</div>`;
