import argparse
import sys
import time

from .archivos import cargar_json, exportar_reportes
from .errores import ErrorValidacion


def ejecutar_cli(entrada: str, salida: str, modo: str) -> None:
    from . import reportes
    from .concurrencia import calcular_resumenes
    from .gestor import GestorEscolar

    datos = cargar_json(entrada)
    gestor = GestorEscolar()
    gestor.cargar(datos)

    inicio = time.perf_counter()
    resumenes = calcular_resumenes(gestor, modo)
    ms = (time.perf_counter() - inicio) * 1000

    rutas = exportar_reportes(reportes.generar_todos(gestor, resumenes=resumenes), gestor.errores, salida)
    print(f"Estudiantes: {len(gestor.estudiantes)} · Cursos: {len(gestor.cursos)} · Calificaciones: {len(gestor.calificaciones)}")
    print(f"Registros rechazados: {len(gestor.errores)} (ver errores.json)")
    print(f"Cálculo de promedios ({modo}): {ms:.2f} ms")
    print(f"Reportes generados: {len(rutas)} archivos en {salida}")


def crear_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Syllabus 2.0 - sistema de calificaciones (versión Python)")
    parser.add_argument("--cli", action="store_true", help="ejecutar sin interfaz gráfica")
    parser.add_argument("--entrada", help="archivo JSON de entrada")
    parser.add_argument("--salida", default="reportes", help="carpeta para los reportes (por defecto: reportes)")
    parser.add_argument("--modo", choices=["secuencial", "concurrente"], default="secuencial")
    return parser


def main(argv=None) -> int:
    parser = crear_parser()
    args = parser.parse_args(argv)
    if args.cli and not args.entrada:
        parser.error("--cli requiere --entrada")
    try:
        if args.cli:
            ejecutar_cli(args.entrada, args.salida, args.modo)
        else:
            from .interfaz import iniciar
            iniciar(args.entrada)
    except ErrorValidacion as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1
    except OSError as e:
        print(f"Error de archivo: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
