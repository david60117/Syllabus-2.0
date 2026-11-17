/**
 * Exportación a PDF sin dependencias (sirve en Node y en el navegador): dibuja el reporte como se ve en la
 * aplicación —encabezado institucional con banda de color, tabla con encabezado repetido en cada página,
 * renglones alternos, estados en color, resumen, firmas, nota y pie con número de página—. Usa las fuentes base
 * Helvetica con codificación WinAnsi (incluye acentos y ñ). Igual que la versión Python.
 */
import { Dec } from './calculos.js';

// anchos de Helvetica y Helvetica-Bold (1/1000 em) para los caracteres 32..255 de cp1252
const ANCHOS = {
  F1: [
  278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278,
  584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944,
  667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500,
  278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 761, 556, 0, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
  0, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 0, 500, 667, 278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556,
  584, 333, 737, 333, 400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611, 667, 667, 667, 667, 667, 667, 1000, 722,
  667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611, 556, 556, 556, 556,
  556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278, 556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500],
  F2: [
  278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333,
  584, 584, 584, 611, 975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944,
  667, 667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611, 611, 611, 389, 556,
  333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 761, 556, 0, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
  0, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 0, 500, 667, 278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556,
  584, 333, 737, 333, 400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611, 722, 722, 722, 722, 722, 722, 1000, 722,
  667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611, 556, 556, 556, 556,
  556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278, 611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556],
};

const TONOS = { Aprobado: '#247457', 'Sin riesgo': '#247457', Activa: '#247457', Reprobado: '#a32e34', Alto: '#a32e34',
  Pendiente: '#a26b34', Medio: '#a26b34', 'En curso': '#a26b34', 'Contraseña inicial': '#a26b34' };
const [TEXTO, SUAVE, BORDE, ZEBRA] = ['#2c2426', '#766e6a', '#e9dfd9', '#fafbfd'];
const MARGEN = 36;

// caracteres de cp1252 fuera de Latin-1
const CP1252 = new Map(Object.entries({ '€': 0x80, '‚': 0x82, 'ƒ': 0x83, '„': 0x84, '…': 0x85, '†': 0x86, '‡': 0x87, 'ˆ': 0x88,
  '‰': 0x89, 'Š': 0x8a, '‹': 0x8b, 'Œ': 0x8c, 'Ž': 0x8e, '‘': 0x91, '’': 0x92, '“': 0x93, '”': 0x94, '•': 0x95, '–': 0x96,
  '—': 0x97, '˜': 0x98, '™': 0x99, 'š': 0x9a, '›': 0x9b, 'œ': 0x9c, 'ž': 0x9e, 'Ÿ': 0x9f }));
const cp1252 = (s) => [...s].map((c) => CP1252.get(c) ?? (c.codePointAt(0) < 256 ? c.codePointAt(0) : 0x3f));

const rgb = (color) => [0, 2, 4].map((i) => parseInt(color.replace('#', '').slice(i, i + 2), 16) / 255);
const suave = (color, cuanto = 0.9) => `#${rgb(color).map((c) => Math.round((c + (1 - c) * cuanto) * 255).toString(16).padStart(2, '0')).join('')}`;
const f2 = (x) => x.toFixed(2);
const f3 = (x) => x.toFixed(3);

export const texto = (v) => {
  if (v === null || v === undefined) return '—';
  if (v instanceof Dec) return v.n.toFixed(2);
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : String(v);
  return String(v);
};

export const ancho = (s, tam, negrita = false) =>
  cp1252(s).reduce((t, b) => t + (b >= 32 ? ANCHOS[negrita ? 'F2' : 'F1'][b - 32] : 556), 0) * tam / 1000;

export function recortar(s, maximo, tam, negrita = false) {
  if (ancho(s, tam, negrita) <= maximo) return s;
  let r = [...s];
  while (r.length && ancho(`${r.join('')}…`, tam, negrita) > maximo) r = r.slice(0, -1);
  return `${r.join('')}…`;
}

const partir = (s, maximo, tam) => s.split(/\s+/).filter(Boolean).reduce((lineas, palabra) => {
  const ultima = lineas.at(-1) ?? '';
  const prueba = `${ultima} ${palabra}`.trim();
  return ancho(prueba, tam) <= maximo || !ultima ? [...lineas.slice(0, -1), prueba] : [...lineas, palabra];
}, []);

/** Página: acumula operaciones de dibujo; y se mide desde arriba como en la pantalla. */
class Pagina {
  constructor(w, h) { Object.assign(this, { w, h, ops: [] }); }

  rect(x, y, w, h, { relleno = null, borde = null, grosor = 0.6 } = {}) {
    if (relleno) this.ops.push(`${rgb(relleno).map(f3).join(' ')} rg`);
    if (borde) this.ops.push(`${rgb(borde).map(f3).join(' ')} RG ${f2(grosor)} w`);
    this.ops.push(`${f2(x)} ${f2(this.h - y - h)} ${f2(w)} ${f2(h)} re ${relleno && borde ? 'B' : relleno ? 'f' : 'S'}`);
  }

  linea(x1, y1, x2, y2, color = BORDE, grosor = 0.6) {
    this.ops.push(`${rgb(color).map(f3).join(' ')} RG ${f2(grosor)} w`, `${f2(x1)} ${f2(this.h - y1)} m ${f2(x2)} ${f2(this.h - y2)} l S`);
  }

  texto(x, y, s, { tam = 9, negrita = false, color = TEXTO, alinear = 'izq' } = {}) {
    const dx = alinear === 'der' ? ancho(s, tam, negrita) : alinear === 'centro' ? ancho(s, tam, negrita) / 2 : 0;
    const datos = cp1252(s).map((b) => ([0x5c, 0x28, 0x29].includes(b) ? `\\${String.fromCharCode(b)}` : String.fromCharCode(b))).join('');
    this.ops.push(`${rgb(color).map(f3).join(' ')} rg`, `BT /${negrita ? 'F2' : 'F1'} ${tam} Tf ${f2(x - dx)} ${f2(this.h - y)} Td (${datos}) Tj ET`);
  }
}

function anchosColumnas(doc, disponible) {
  const naturales = doc.columnas.map(([clave, titulo]) => Math.max(ancho(titulo, 8, true),
    ...doc.filas.slice(0, 400).map((f) => ancho(texto(f[clave]), 8.5))) + 12);
  const total = naturales.reduce((a, b) => a + b, 0);
  if (total <= disponible) return naturales.map((n) => n + (disponible - total) / naturales.length);
  // tope común para las columnas largas (búsqueda binaria)
  const tope = Array.from({ length: 40 }).reduce(([bajo, alto]) => {
    const medio = (bajo + alto) / 2;
    return naturales.reduce((a, n) => a + Math.min(n, medio), 0) > disponible ? [bajo, medio] : [medio, alto];
  }, [0, Math.max(...naturales)])[0];
  return naturales.map((n) => Math.min(n, tope));
}

/** Devuelve el PDF del documento como Uint8Array. */
export function generar(doc, acento = '#8b2024') {
  const enc = doc.encabezado;
  const natural = doc.columnas.reduce((t, [, titulo]) => t + ancho(titulo, 8, true) + 40, 0);
  const horizontal = doc.columnas.length > 7 || natural > 595 - 2 * MARGEN;
  const [W, H] = horizontal ? [842, 595] : [595, 842];
  const util = W - 2 * MARGEN;
  const paginas = [];
  const nueva = () => { const p = new Pagina(W, H); paginas.push(p); return p; };

  // ---------- encabezado institucional ----------
  let p = nueva();
  const filasCampos = Math.ceil(enc.campos.length / 3);
  const altoBanda = 92 + filasCampos * 26;
  p.rect(MARGEN, MARGEN, util, altoBanda, { relleno: suave(acento, 0.9) });
  p.rect(MARGEN, MARGEN, 4, altoBanda, { relleno: acento });
  const x0 = MARGEN + 18;
  p.texto(x0, MARGEN + 24, enc.institucion, { tam: 13, negrita: true });
  p.texto(x0, MARGEN + 38, enc.programa, { tam: 8.5, color: SUAVE });
  p.texto(MARGEN + util - 16, MARGEN + 22, 'CICLO ESCOLAR', { tam: 7, negrita: true, color: SUAVE, alinear: 'der' });
  p.texto(MARGEN + util - 16, MARGEN + 36, enc.ciclo, { tam: 10, negrita: true, alinear: 'der' });
  p.texto(x0, MARGEN + 64, enc.titulo.toUpperCase(), { tam: 15, negrita: true, color: acento });
  const col = (util - 36) / 3;
  enc.campos.forEach(([etiqueta, valor], i) => {
    const [cx, cy] = [x0 + (i % 3) * col, MARGEN + 84 + Math.floor(i / 3) * 26];
    p.texto(cx, cy, etiqueta, { tam: 7, color: SUAVE });
    p.texto(cx, cy + 11, recortar(texto(valor), col - 12, 9.5, true), { tam: 9.5, negrita: true });
  });
  p.texto(MARGEN + util - 16, MARGEN + altoBanda - 8, `Emitido el ${enc.emision}${enc.emitido_por ? ` por ${enc.emitido_por}` : ''}`,
    { tam: 7, color: SUAVE, alinear: 'der' });
  let y = MARGEN + altoBanda + 18;

  // ---------- tabla ----------
  const anchos = anchosColumnas(doc, util);
  const xs = anchos.reduce((a, w) => [...a, a.at(-1) + w], [MARGEN]);
  const [FILA, CABEZA, LIMITE] = [17, 20, H - MARGEN - 28];
  const cabeza = (pag, yy) => {
    pag.rect(MARGEN, yy, util, CABEZA, { relleno: '#ffffff' });
    doc.columnas.forEach(([, titulo], j) => pag.texto(xs[j] + 6, yy + 13.5, recortar(titulo, anchos[j] - 10, 8, true), { tam: 8, negrita: true, color: SUAVE }));
    pag.linea(MARGEN, yy + CABEZA, MARGEN + util, yy + CABEZA, BORDE, 0.8);
    return yy + CABEZA;
  };
  let inicio = y;
  y = cabeza(p, y);
  if (!doc.filas.length) {
    p.texto(MARGEN + util / 2, y + 13, 'Sin registros', { color: SUAVE, alinear: 'centro' });
    y += FILA;
  }
  doc.filas.forEach((fila, i) => {
    if (y + FILA > LIMITE) {
      p.rect(MARGEN, inicio, util, y - inicio, { borde: BORDE });
      p = nueva();
      p.texto(MARGEN, MARGEN + 12, `${enc.titulo} (continuación)`, { tam: 10, negrita: true, color: acento });
      inicio = MARGEN + 22;
      y = cabeza(p, inicio);
    }
    if (i % 2) p.rect(MARGEN, y, util, FILA, { relleno: ZEBRA });
    doc.columnas.forEach(([clave], j) => {
      const s = texto(fila[clave]);
      p.texto(xs[j] + 6, y + 12, recortar(s, anchos[j] - 10, 8.5), { tam: 8.5, color: TONOS[s] ?? TEXTO });
    });
    y += FILA;
  });
  p.rect(MARGEN, inicio, util, y - inicio, { borde: BORDE });
  y += 14;

  // ---------- resumen, firmas y nota ----------
  if (doc.resumen.length) {
    if (y + 40 > LIMITE) { p = nueva(); y = MARGEN; }
    const caja = (util - 8 * (doc.resumen.length - 1)) / doc.resumen.length;
    doc.resumen.forEach(([etiqueta, valor], i) => {
      const x = MARGEN + i * (caja + 8);
      p.rect(x, y, caja, 36, { relleno: '#fbfaf9', borde: BORDE });
      p.texto(x + 8, y + 13, recortar(etiqueta, caja - 14, 7), { tam: 7, color: SUAVE });
      p.texto(x + 8, y + 28, recortar(texto(valor), caja - 14, 11, true), { tam: 11, negrita: true, color: acento });
    });
    y += 50;
  }
  if (doc.firmas.length) {
    if (y + 70 > LIMITE) { p = nueva(); y = MARGEN; }
    const tramo = util / doc.firmas.length;
    doc.firmas.forEach((f, i) => {
      const cx = MARGEN + tramo * i + tramo / 2;
      const medio = Math.min(110, tramo / 2 - 20);
      p.linea(cx - medio, y + 46, cx + medio, y + 46, TEXTO, 0.7);
      p.texto(cx, y + 58, f, { tam: 8, color: SUAVE, alinear: 'centro' });
    });
    y += 72;
  }
  if (doc.nota) {
    partir(doc.nota, util, 7.5).forEach((linea) => {
      if (y + 10 > LIMITE) { p = nueva(); y = MARGEN; }
      p.texto(MARGEN, y + 8, linea, { tam: 7.5, color: SUAVE });
      y += 11;
    });
  }

  // ---------- pie de página ----------
  paginas.forEach((pag, i) => {
    pag.linea(MARGEN, H - MARGEN - 14, W - MARGEN, H - MARGEN - 14);
    pag.texto(MARGEN, H - MARGEN, `Syllabus 2.0 · ${enc.titulo}`, { tam: 7, color: SUAVE });
    pag.texto(W - MARGEN, H - MARGEN, `Página ${i + 1} de ${paginas.length}`, { tam: 7, color: SUAVE, alinear: 'der' });
  });
  return escribir(paginas, enc.titulo);
}

/** Serializa las páginas a bytes PDF 1.4 (objetos, tabla xref y trailer). */
function escribir(paginas, titulo) {
  const latin1 = (s) => Uint8Array.from([...s].map((c) => c.charCodeAt(0) & 0xff));
  const objetos = [];
  const agregar = (cuerpo) => objetos.push(cuerpo) && objetos.length;
  const catalogo = agregar('');
  const raiz = agregar('');
  const f1 = agregar('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>');
  const f2_ = agregar('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>');
  const hijos = paginas.map((pag) => {
    const flujo = pag.ops.join('\n');
    const contenido = agregar(`<< /Length ${flujo.length} >>\nstream\n${flujo}\nendstream`);
    return agregar(`<< /Type /Page /Parent ${raiz} 0 R /MediaBox [0 0 ${pag.w} ${pag.h}] /Contents ${contenido} 0 R `
      + `/Resources << /Font << /F1 ${f1} 0 R /F2 ${f2_} 0 R >> >> >>`);
  });
  objetos[catalogo - 1] = `<< /Type /Catalog /Pages ${raiz} 0 R >>`;
  objetos[raiz - 1] = `<< /Type /Pages /Kids [${hijos.map((h) => `${h} 0 R`).join(' ')}] /Count ${hijos.length} >>`;
  const info = agregar(`<< /Title (${cp1252(titulo).filter((b) => b !== 0x28 && b !== 0x29).map((b) => String.fromCharCode(b)).join('')}) /Producer (Syllabus 2.0) >>`);
  const partes = ['%PDF-1.4\n%\xe2\xe3\xcf\xd3\n'];
  const posiciones = objetos.map((obj, i) => {
    const pos = partes.reduce((t, x) => t + x.length, 0);
    partes.push(`${i + 1} 0 obj\n${obj}\nendobj\n`);
    return pos;
  });
  const xref = partes.reduce((t, x) => t + x.length, 0);
  partes.push(`xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`, ...posiciones.map((p) => `${String(p).padStart(10, '0')} 00000 n \n`),
    `trailer\n<< /Size ${objetos.length + 1} /Root ${catalogo} 0 R /Info ${info} 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
  return latin1(partes.join(''));
}
