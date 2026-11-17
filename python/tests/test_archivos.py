import contextlib
import csv
import io
import json
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

from src.archivos import cargar_json, exportar_csv, exportar_json, exportar_reportes
from src.errores import ErrorValidacion
from src.main import main

DATOS = Path(__file__).resolve().parents[2] / "datos"


class TestCargarJson(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.carpeta = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def escribir(self, nombre, texto):
        ruta = self.carpeta / nombre
        ruta.write_text(texto, encoding="utf-8")
        return ruta

    def test_carga_ejemplo_valido(self):
        datos = cargar_json(DATOS / "ejemplo_entrada.json")
        self.assertEqual(len(datos["estudiantes"]), 4)
        self.assertEqual(len(datos["parciales"]), 3)

    def test_carga_invalidos_sin_error_de_estructura(self):
        datos = cargar_json(DATOS / "invalidos.json")
        self.assertIn("calificaciones", datos)

    def test_archivo_inexistente(self):
        with self.assertRaises(ErrorValidacion) as ctx:
            cargar_json(self.carpeta / "no_existe.json")
        self.assertEqual(ctx.exception.codigo, "ESTRUCTURA_INVALIDA")

    def test_json_mal_formado(self):
        ruta = self.escribir("roto.json", '{"estudiantes": [')
        with self.assertRaises(ErrorValidacion) as ctx:
            cargar_json(ruta)
        self.assertEqual(ctx.exception.codigo, "ESTRUCTURA_INVALIDA")
        self.assertIn("línea", ctx.exception.detalle)

    def test_faltan_llaves(self):
        ruta = self.escribir("incompleto.json", '{"estudiantes": [], "cursos": []}')
        with self.assertRaises(ErrorValidacion) as ctx:
            cargar_json(ruta)
        self.assertIn("parciales", ctx.exception.detalle)

    def test_llave_que_no_es_lista(self):
        llaves = ["estudiantes", "cursos", "parciales", "evaluaciones", "inscripciones", "calificaciones"]
        contenido = {llave: [] for llave in llaves}
        contenido["cursos"] = {}
        ruta = self.escribir("tipo.json", json.dumps(contenido))
        with self.assertRaises(ErrorValidacion) as ctx:
            cargar_json(ruta)
        self.assertIn("cursos", ctx.exception.detalle)

    def test_raiz_que_no_es_objeto(self):
        ruta = self.escribir("lista.json", "[]")
        with self.assertRaises(ErrorValidacion):
            cargar_json(ruta)


class TestExportar(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.carpeta = Path(self.tmp.name)
        self.filas = [
            {"num_cuenta": "500005", "apellidos": "Álvarez Ruiz", "nombre": "Elena", "promedio": Decimal("9.00")},
            {"num_cuenta": "500007", "apellidos": "Peña Flores", "nombre": "Gloria", "promedio": Decimal("7.40")},
        ]

    def tearDown(self):
        self.tmp.cleanup()

    def test_json_convierte_decimal_y_conserva_acentos(self):
        ruta = exportar_json(self.filas, self.carpeta / "sub" / "r.json")
        texto = ruta.read_text(encoding="utf-8")
        self.assertIn("Álvarez", texto)
        self.assertEqual(json.loads(texto)[1]["promedio"], 7.4)

    def test_csv_con_dos_decimales_y_bom(self):
        ruta = exportar_csv(self.filas, self.carpeta / "r.csv")
        self.assertTrue(ruta.read_bytes().startswith(b"\xef\xbb\xbf"))
        with ruta.open(encoding="utf-8-sig", newline="") as f:
            filas = list(csv.DictReader(f))
        self.assertEqual(filas[0]["apellidos"], "Álvarez Ruiz")
        self.assertEqual(filas[1]["promedio"], "7.40")

    def test_exportar_reportes_genera_todos_los_archivos(self):
        rutas = exportar_reportes({"grupo_estudiantes": self.filas}, [{"codigo": "ID_DUPLICADO"}], self.carpeta)
        nombres = sorted(r.name for r in rutas)
        self.assertEqual(nombres, ["errores.json", "grupo_estudiantes.csv", "grupo_estudiantes.json"])


class TestReportes(unittest.TestCase):
    def test_cli_genera_reportes_con_ejemplo(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            codigo = main(["--cli", "--entrada", str(DATOS / "ejemplo_entrada.json"), "--salida", tmp])
            nombres = sorted(p.name for p in Path(tmp).iterdir())
            resumen = json.loads((Path(tmp) / "resumen_grupos.json").read_text(encoding="utf-8"))
        self.assertEqual(codigo, 0)
        self.assertIn("parciales_por_asignatura.csv", nombres)
        self.assertEqual(resumen[0]["promedio"], 7.6)  # (9 + 9 + 5 + 7.4) / 4


class TestMain(unittest.TestCase):
    def test_archivo_inexistente_devuelve_1_sin_traceback(self):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            codigo = main(["--cli", "--entrada", "no_existe.json"])
        self.assertEqual(codigo, 1)
        self.assertIn("ESTRUCTURA_INVALIDA", stderr.getvalue())
        self.assertNotIn("Traceback", stderr.getvalue())

    def test_cli_sin_entrada_es_error_de_uso(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            main(["--cli"])


if __name__ == "__main__":
    unittest.main()
