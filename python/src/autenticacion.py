"""Cuentas de usuario e inicio de sesión (contraseñas guardadas como SHA-256, nunca en texto plano).

Como en un sistema escolar real, cada alumno y cada docente tiene su cuenta: se crea sola al darlo de
alta, con usuario = número de cuenta (alumno) o ID en minúsculas (docente) y contraseña inicial igual
al usuario. La primera vez que entra, el sistema le pide cambiarla.
"""
import hashlib
import json
import re
from pathlib import Path

from .errores import ErrorValidacion

USUARIOS = Path(__file__).resolve().parents[2] / "datos" / "usuarios.json"
RE_CONTRASENA = re.compile(r"^(?=.*[A-Za-z])(?=.*\d).{8,}$")


def resumen(contrasena: str) -> str:
    return hashlib.sha256(contrasena.encode("utf-8")).hexdigest()


def cargar_usuarios(ruta=USUARIOS) -> list[dict]:
    try:
        return json.loads(Path(ruta).read_text(encoding="utf-8"))["usuarios"]
    except (OSError, ValueError, KeyError) as e:
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", f"no se pudieron leer los usuarios: {e}") from None


def buscar(usuarios: list[dict], clave: str):
    clave = clave.strip().lower()
    return next((u for u in usuarios if clave in (u["usuario"].lower(), (u.get("correo") or "").lower())), None)


def iniciar_sesion(usuarios: list[dict], usuario: str, contrasena: str, gestor) -> dict:
    """Devuelve el usuario si la contraseña coincide y su cuenta existe en los datos cargados."""
    if not usuario.strip() or not contrasena:
        raise ErrorValidacion("CAMPO_FALTANTE", "escribe tu usuario y tu contraseña")
    cuenta = buscar(usuarios, usuario)
    if cuenta is None or cuenta["contrasena_sha256"] != resumen(contrasena):
        raise ErrorValidacion("CREDENCIALES_INVALIDAS", "usuario o contraseña incorrectos")
    ref = cuenta.get("referencia")
    if cuenta["rol"] == "Docente" and ref not in gestor.docentes:
        raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"el docente {ref} no está en los datos cargados")
    if cuenta["rol"] == "Alumno" and ref not in gestor.estudiantes:
        raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"el estudiante {ref} no está en los datos cargados")
    return cuenta


def crear_cuenta(usuarios: list[dict], usuario: str, rol: str, referencia, nombre: str) -> dict:
    if buscar(usuarios, usuario):
        raise ErrorValidacion("ID_DUPLICADO", f"el usuario {usuario} ya existe")
    cuenta = {"usuario": usuario, "correo": None, "contrasena_sha256": resumen(usuario), "rol": rol,
              "referencia": referencia, "nombre": nombre, "debe_cambiar": True}
    usuarios.append(cuenta)
    return cuenta


def sincronizar(usuarios: list[dict], gestor) -> int:
    """Crea la cuenta de cada alumno y docente que no tenga una, quita las de quienes ya no existen
    y actualiza los nombres. Devuelve cuántas cuentas se crearon."""
    con_cuenta = {(u["rol"], u.get("referencia")) for u in usuarios}
    vigentes = {("Alumno", n) for n in gestor.estudiantes} | {("Docente", d) for d in gestor.docentes}
    usuarios[:] = [u for u in usuarios if u["rol"] == "Administrador" or (u["rol"], u.get("referencia")) in vigentes]
    nombres = {("Alumno", n): e.nombre_completo for n, e in gestor.estudiantes.items()}
    nombres |= {("Docente", d): x.nombre for d, x in gestor.docentes.items()}
    for u in usuarios:
        u["nombre"] = nombres.get((u["rol"], u.get("referencia")), u["nombre"])
    creadas = 0
    for rol, ref in sorted(vigentes - con_cuenta):
        crear_cuenta(usuarios, ref if rol == "Alumno" else ref.lower(), rol, ref, nombres[(rol, ref)])
        creadas += 1
    return creadas


def cambiar_contrasena(cuenta: dict, actual: str, nueva: str, confirmacion: str) -> None:
    if cuenta["contrasena_sha256"] != resumen(actual):
        raise ErrorValidacion("CREDENCIALES_INVALIDAS", "la contraseña actual no es correcta")
    if nueva != confirmacion:
        raise ErrorValidacion("FORMATO_INVALIDO", "la confirmación no coincide con la nueva contraseña")
    if not RE_CONTRASENA.match(nueva):
        raise ErrorValidacion("FORMATO_INVALIDO", "la contraseña debe tener al menos 8 caracteres, con letras y números")
    if nueva == actual or nueva.lower() == cuenta["usuario"].lower():
        raise ErrorValidacion("FORMATO_INVALIDO", "elige una contraseña distinta a la anterior y a tu usuario")
    cuenta["contrasena_sha256"] = resumen(nueva)
    cuenta["debe_cambiar"] = False


def restablecer(cuenta: dict) -> str:
    """La contraseña vuelve a ser igual al usuario y se pedirá cambiarla al entrar."""
    cuenta["contrasena_sha256"] = resumen(cuenta["usuario"])
    cuenta["debe_cambiar"] = True
    return cuenta["usuario"]


def contrasena_visible(cuenta: dict):
    """La contraseña solo se puede mostrar mientras sea la inicial (igual al usuario). Una contraseña elegida
    por el usuario se guarda cifrada (SHA-256) y no se puede recuperar: se restablece."""
    return cuenta["usuario"] if cuenta["contrasena_sha256"] == resumen(cuenta["usuario"]) else None
