import json
import unittest
from pathlib import Path

from src.autenticacion import cargar_usuarios, iniciar_sesion
from src.errores import ErrorValidacion
from src.gestor import GestorEscolar

DATOS = Path(__file__).resolve().parents[2] / "datos"


class TestInicioDeSesion(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.usuarios = cargar_usuarios()
        cls.gestor = GestorEscolar()
        cls.gestor.cargar(json.loads((DATOS / "solicitudes_pequenas.json").read_text(encoding="utf-8")))

    def test_un_usuario_por_rol(self):
        self.assertEqual(sorted(u["rol"] for u in self.usuarios), ["Administrador", "Alumno", "Docente"])

    def test_usuarios_de_muestra(self):
        for usuario, contrasena, rol, ref in (("admin", "Admin123", "Administrador", None),
                                              ("PAOLA.AVILA@syllabus.mx", "Docente123", "Docente", "D01"),
                                              ("500001", "Alumno123", "Alumno", "500001")):
            cuenta = iniciar_sesion(self.usuarios, usuario, contrasena, self.gestor)
            self.assertEqual((cuenta["rol"], cuenta["referencia"]), (rol, ref))

    def test_contrasena_incorrecta(self):
        with self.assertRaises(ErrorValidacion) as ctx:
            iniciar_sesion(self.usuarios, "admin", "otra", self.gestor)
        self.assertEqual(ctx.exception.codigo, "CREDENCIALES_INVALIDAS")

    def test_cuenta_que_no_esta_en_los_datos(self):
        with self.assertRaises(ErrorValidacion) as ctx:
            iniciar_sesion(self.usuarios, "pavila", "Docente123", GestorEscolar())
        self.assertEqual(ctx.exception.codigo, "REFERENCIA_INEXISTENTE")

    def test_contrasenas_no_estan_en_texto_plano(self):
        texto = (DATOS / "usuarios.json").read_text(encoding="utf-8")
        for c in ("Admin123", "Docente123", "Alumno123"):
            self.assertNotIn(c, texto)
