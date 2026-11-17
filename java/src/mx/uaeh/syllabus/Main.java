package mx.uaeh.syllabus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.logica.Concurrencia;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.logica.Reportes;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/**
 * Punto de entrada. Sin argumentos abre la interfaz Swing; con --cli genera los reportes:
 * java -jar syllabus.jar --cli --entrada datos.json [--salida reportes] [--modo secuencial|concurrente]
 */
public final class Main {
    private Main() {
    }

    public static void ejecutarCli(String entrada, String salida, String modo) throws IOException {
        GestorEscolar gestor = new GestorEscolar();
        gestor.cargar(Archivos.cargarJson(Path.of(entrada)));
        long inicio = System.nanoTime();
        List<ResumenEstudiante> resumenes = Concurrencia.calcularResumenes(gestor, modo);
        double ms = (System.nanoTime() - inicio) / 1e6;
        List<Path> rutas = Archivos.exportarReportes(Reportes.generarTodos(gestor, resumenes), gestor.getErrores(), Path.of(salida));
        System.out.printf("Estudiantes: %d · Cursos: %d · Calificaciones: %d%n", gestor.getEstudiantes().size(),
                gestor.getCursos().size(), gestor.getCalificaciones().size());
        System.out.printf("Registros rechazados: %d (ver errores.json)%n", gestor.getErrores().size());
        System.out.printf("Cálculo de promedios (%s): %.2f ms%n", modo, ms);
        System.out.printf("Reportes generados: %d archivos en %s%n", rutas.size(), salida);
    }

    static int ejecutar(String[] args) {
        Map<String, String> op = new java.util.HashMap<>(Map.of("salida", "reportes", "modo", "secuencial"));
        boolean cli = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cli" -> cli = true;
                case "--entrada", "--salida", "--modo" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: falta el valor de " + args[i]);
                        return 2;
                    }
                    op.put(args[i].substring(2), args[++i]);
                }
                default -> {
                    System.err.println("Uso: [--cli --entrada archivo.json] [--salida carpeta] [--modo secuencial|concurrente]");
                    return 2;
                }
            }
        }
        if (!List.of("secuencial", "concurrente").contains(op.get("modo"))) {
            System.err.println("Error: --modo debe ser secuencial o concurrente");
            return 2;
        }
        try {
            if (cli) {
                if (!op.containsKey("entrada")) {
                    System.err.println("Error: --cli requiere --entrada");
                    return 2;
                }
                ejecutarCli(op.get("entrada"), op.get("salida"), op.get("modo"));
            } else {
                mx.uaeh.syllabus.ui.App.iniciar(op.get("entrada"));
            }
        } catch (ErrorValidacion e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("Error de archivo: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    public static void main(String[] args) {
        int codigo = ejecutar(args);
        if (codigo != 0) {
            System.exit(codigo);
        }
    }
}
