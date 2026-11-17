/**
 * Cuentas de usuario e inicio de sesión (contraseñas guardadas como SHA-256, nunca en texto plano).
 * Sirve en Node y en el navegador. Como en un sistema escolar real, cada alumno y docente tiene su cuenta:
 * usuario = número de cuenta (alumno) o ID en minúsculas (docente) y contraseña inicial igual al usuario;
 * la primera vez que entra se le pide cambiarla.
 */
import { ErrorValidacion } from './errores.js';

// ---------- SHA-256 (FIPS 180-4) en funciones puras: sincrónico para usarse al dibujar la interfaz ----------
const K = Uint32Array.from([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01,
  0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
  0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08,
  0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2]);
const rotr = (x, n) => (x >>> n) | (x << (32 - n));

export function resumen(texto) {
  const datos = new TextEncoder().encode(texto);
  const largo = Math.ceil((datos.length + 9) / 64) * 64;
  const m = new Uint8Array(largo);
  m.set(datos);
  m[datos.length] = 0x80;
  new DataView(m.buffer).setUint32(largo - 4, datos.length * 8);
  const h = Uint32Array.from([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const w = new Uint32Array(64);
  const vista = new DataView(m.buffer);
  for (let bloque = 0; bloque < largo; bloque += 64) {
    for (let i = 0; i < 16; i++) w[i] = vista.getUint32(bloque + i * 4);
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let i = 0; i < 64; i++) {
      const t1 = (hh + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + K[i] + w[i]) >>> 0;
      const t2 = ((rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      [hh, g, f, e, d, c, b, a] = [g, f, e, (d + t1) >>> 0, c, b, a, (t1 + t2) >>> 0];
    }
    [a, b, c, d, e, f, g, hh].forEach((x, i) => { h[i] = (h[i] + x) >>> 0; });
  }
  return [...h].map((x) => x.toString(16).padStart(8, '0')).join('');
}

const RE_CONTRASENA = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;

export const buscar = (usuarios, clave) => {
  const k = clave.trim().toLowerCase();
  return usuarios.find((u) => [u.usuario.toLowerCase(), (u.correo ?? '').toLowerCase()].includes(k)) ?? null;
};

export async function iniciarSesion(usuarios, usuario, contrasena, gestor) {
  if (!usuario.trim() || !contrasena) throw new ErrorValidacion('CAMPO_FALTANTE', 'escribe tu usuario y tu contraseña');
  const cuenta = buscar(usuarios, usuario);
  if (!cuenta || cuenta.contrasena_sha256 !== resumen(contrasena)) {
    throw new ErrorValidacion('CREDENCIALES_INVALIDAS', 'usuario o contraseña incorrectos');
  }
  const ref = cuenta.referencia;
  if (cuenta.rol === 'Docente' && !gestor.docentes.has(ref)) {
    throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `el docente ${ref} no está en los datos cargados`);
  }
  if (cuenta.rol === 'Alumno' && !gestor.estudiantes.has(ref)) {
    throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `el estudiante ${ref} no está en los datos cargados`);
  }
  return cuenta;
}

export function crearCuenta(usuarios, usuario, rol, referencia, nombre) {
  if (buscar(usuarios, usuario)) throw new ErrorValidacion('ID_DUPLICADO', `el usuario ${usuario} ya existe`);
  const cuenta = { usuario, correo: null, contrasena_sha256: resumen(usuario), rol, referencia, nombre, debe_cambiar: true };
  usuarios.push(cuenta);
  return cuenta;
}

/**
 * Crea la cuenta de cada alumno y docente que no tenga una, quita las de quienes ya no existen y actualiza
 * los nombres. Modifica la lista recibida y devuelve cuántas cuentas se crearon.
 */
export function sincronizar(usuarios, gestor) {
  const llave = (rol, ref) => `${rol}|${ref}`;
  const nombres = new Map([
    ...[...gestor.estudiantes.values()].map((e) => [llave('Alumno', e.num_cuenta), `${e.apellidos} ${e.nombre}`]),
    ...[...gestor.docentes.values()].map((d) => [llave('Docente', d.id), d.nombre]),
  ]);
  const vigentes = usuarios.filter((u) => u.rol === 'Administrador' || nombres.has(llave(u.rol, u.referencia)))
    .map((u) => ({ ...u, nombre: nombres.get(llave(u.rol, u.referencia)) ?? u.nombre }));
  usuarios.splice(0, usuarios.length, ...vigentes);
  const conCuenta = new Set(usuarios.map((u) => llave(u.rol, u.referencia)));
  const faltan = [...nombres.keys()].filter((k) => !conCuenta.has(k)).sort();
  faltan.forEach((k) => {
    const [rol, ref] = k.split('|');
    crearCuenta(usuarios, rol === 'Alumno' ? ref : ref.toLowerCase(), rol, ref, nombres.get(k));
  });
  return faltan.length;
}

export function cambiarContrasena(cuenta, actual, nueva, confirmacion) {
  if (cuenta.contrasena_sha256 !== resumen(actual)) throw new ErrorValidacion('CREDENCIALES_INVALIDAS', 'la contraseña actual no es correcta');
  if (nueva !== confirmacion) throw new ErrorValidacion('FORMATO_INVALIDO', 'la confirmación no coincide con la nueva contraseña');
  if (!RE_CONTRASENA.test(nueva)) throw new ErrorValidacion('FORMATO_INVALIDO', 'la contraseña debe tener al menos 8 caracteres, con letras y números');
  if (nueva === actual || nueva.toLowerCase() === cuenta.usuario.toLowerCase()) {
    throw new ErrorValidacion('FORMATO_INVALIDO', 'elige una contraseña distinta a la anterior y a tu usuario');
  }
  Object.assign(cuenta, { contrasena_sha256: resumen(nueva), debe_cambiar: false });
}

/** La contraseña vuelve a ser igual al usuario y se pedirá cambiarla al entrar. */
export function restablecer(cuenta) {
  Object.assign(cuenta, { contrasena_sha256: resumen(cuenta.usuario), debe_cambiar: true });
  return cuenta.usuario;
}

/**
 * La contraseña solo se puede mostrar mientras sea la inicial (igual al usuario). Una contraseña elegida por el
 * usuario se guarda cifrada (SHA-256) y no se puede recuperar: se restablece.
 */
export const contrasenaVisible = (cuenta) => (cuenta.contrasena_sha256 === resumen(cuenta.usuario) ? cuenta.usuario : null);
