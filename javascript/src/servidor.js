/**
 * Servidor de la interfaz web: npm start → http://localhost:3000
 * Sirve los archivos y guarda el sistema en datos/base.json (GET y PUT /api/base), el mismo respaldo que usan
 * las versiones Python y Java. Con --entrada se abre ese archivo como demostración, sin guardar cambios:
 * npm start -- --entrada ../datos/solicitudes_medias.json
 */
import { createServer } from 'node:http';
import { readFile, rename, writeFile } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const SRC = fileURLToPath(new URL('.', import.meta.url));
const DATOS = resolve(SRC, '../../datos');
const TIPOS = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json' };
const PUERTO = Number(process.env.PORT ?? 3000);
const { values: opciones } = parseArgs({ options: { entrada: { type: 'string' } }, strict: false });
const ENTRADA = opciones.entrada ? resolve(opciones.entrada) : null;

const rutaSegura = (base, relativa) => {
  const ruta = normalize(join(base, relativa));
  return ruta.startsWith(base) ? ruta : null;
};

const resolver = (url) => {
  if (url === '/') return join(SRC, 'web', 'index.html');
  if (url === '/entrada.json') return ENTRADA;
  if (url.startsWith('/datos/')) return rutaSegura(DATOS, url.slice('/datos/'.length));
  return rutaSegura(SRC, url.slice(1));
};

const BASE = join(DATOS, 'base.json');

const leerCuerpo = (req) => new Promise((ok, mal) => {
  const partes = [];
  req.on('data', (p) => partes.push(p)).on('end', () => ok(Buffer.concat(partes).toString('utf8'))).on('error', mal);
});

/** API del respaldo: GET devuelve base.json (204 si aún no existe); PUT lo reemplaza de forma atómica. */
async function api(req, res) {
  if (req.method === 'GET') {
    if (ENTRADA) return res.writeHead(404).end(JSON.stringify({ demostracion: true }));
    const contenido = await readFile(BASE).catch(() => null);
    if (!contenido) return res.writeHead(204).end();
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    return res.end(contenido);
  }
  if (req.method === 'PUT') {
    if (ENTRADA) return res.writeHead(409).end('Modo demostración: no se guardan cambios');
    const texto = await leerCuerpo(req);
    const datos = JSON.parse(texto); // valida que sea JSON antes de reemplazar el archivo
    if (!datos.datos || !Array.isArray(datos.usuarios)) return res.writeHead(400).end('Respaldo incompleto');
    const temporal = `${BASE}.tmp`;
    await writeFile(temporal, JSON.stringify(datos, null, 1), 'utf8');
    await rename(temporal, BASE);
    return res.writeHead(204).end();
  }
  return res.writeHead(405).end();
}

createServer(async (req, res) => {
  const camino = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (camino === '/entrada.json' && !ENTRADA) return res.writeHead(204).end(); // sin --entrada: no hay demostración
  if (camino === '/api/base') return api(req, res).catch((e) => res.writeHead(500).end(String(e.message)));
  const ruta = resolver(camino);
  try {
    if (!ruta) throw new Error('fuera');
    const contenido = await readFile(ruta);
    res.writeHead(200, { 'Content-Type': `${TIPOS[extname(ruta)] ?? 'application/octet-stream'}; charset=utf-8` });
    res.end(contenido);
  } catch {
    res.writeHead(404).end('No encontrado');
  }
}).listen(PUERTO, () => {
  console.log(`Syllabus 2.0 (JavaScript) en http://localhost:${PUERTO}`);
  console.log(ENTRADA ? `Demostración (sin guardar): ${ENTRADA}` : `Datos: ${BASE} (se crea con los datos de ejemplo la primera vez)`);
});
