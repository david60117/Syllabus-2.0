import json
import unittest
from pathlib import Path

from src.errores import ErrorValidacion
from src.gestor import GestorEscolar

DATOS = Path(__file__).resolve().parents[2] / "datos"


class TestRegistrosInvalidos(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.datos = json.loads((DATOS / "invalidos.json").read_text(encoding="utf-8"))
        cls.gestor = GestorEscolar()
        cls.gestor.cargar(cls.datos)

    def test_errores_esperados(self):
        obtenidos = sorted((e["caso"], e["codigo"]) for e in self.gestor.errores)
        esperados = sorted((e["caso"], e["codigo"]) for e in self.datos["errores_esperados"])
        self.assertEqual(obtenidos, esperados)

    def test_val01_continua_despues_de_errores(self):
        self.assertIn("545049", self.gestor.estudiantes)
        self.assertEqual(str(__import__("src.calculos", fromlist=["mostrar"]).mostrar(
            self.gestor.promedio_curso("545049", "C01"))), "8.34")

    def test_inv20_conserva_calificacion_anterior(self):
        self.assertEqual(self.gestor.calificaciones[("545049", "EV01")], 8.0)


class TestAltasIndividuales(unittest.TestCase):
    def setUp(self):
        self.g = GestorEscolar()
        self.g.registrar_estudiante({"num_cuenta": "500001", "apellidos": "García López", "nombre": "Ana", "grupo": "301"})
        self.g.registrar_curso({"id": "C01", "asignatura": "Bases de Datos", "grupo": "301"})

    def test_parciales_no_superan_uno(self):
        self.g.registrar_parcial({"id": "P01", "curso_id": "C01", "numero": 1, "peso": 0.6})
        with self.assertRaises(ErrorValidacion) as ctx:
            self.g.registrar_parcial({"id": "P02", "curso_id": "C01", "numero": 2, "peso": 0.5})
        self.assertEqual(ctx.exception.codigo, "PORCENTAJES_INVALIDOS")

    def test_inscripcion_de_otro_grupo(self):
        self.g.registrar_estudiante({"num_cuenta": "500002", "apellidos": "Ruiz Mora", "nombre": "Luis", "grupo": "302"})
        with self.assertRaises(ErrorValidacion) as ctx:
            self.g.inscribir({"num_cuenta": "500002", "curso_id": "C01"})
        self.assertEqual(ctx.exception.codigo, "REFERENCIA_INEXISTENTE")

    def test_eventos_emitidos(self):
        vistos = []
        self.g.bus.suscribir("inscripcion_registrada", lambda **d: vistos.append(d["detalle"]))
        self.g.inscribir({"num_cuenta": "500001", "curso_id": "C01"})
        self.assertEqual(vistos, ["500001 → C01"])


if __name__ == "__main__":
    unittest.main()
