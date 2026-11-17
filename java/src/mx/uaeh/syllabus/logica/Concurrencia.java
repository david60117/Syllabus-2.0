package mx.uaeh.syllabus.logica;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/**
 * Cálculo de resúmenes. En modo concurrente reparte los estudiantes en bloques entre hilos
 * de un ExecutorService; la Calculadora solo se lee, así que los hilos no compiten por datos.
 */
public final class Concurrencia {
    private Concurrencia() {
    }

    public static List<ResumenEstudiante> calcularResumenes(Calculadora calc, String modo) {
        return calcularResumenes(calc, modo, Runtime.getRuntime().availableProcessors());
    }

    public static List<ResumenEstudiante> calcularResumenes(Calculadora calc, String modo, int trabajadores) {
        List<String> cuentas = calc.getEstudiantes().keySet().stream().sorted().toList();
        if (!"concurrente".equals(modo) || cuentas.size() < 2) {
            return cuentas.stream().map(calc::resumenEstudiante).toList();
        }
        int tam = (cuentas.size() + trabajadores - 1) / trabajadores;
        List<Callable<List<ResumenEstudiante>>> tareas = new ArrayList<>();
        for (int i = 0; i < cuentas.size(); i += tam) {
            List<String> bloque = cuentas.subList(i, Math.min(i + tam, cuentas.size()));
            tareas.add(() -> bloque.stream().map(calc::resumenEstudiante).toList());
        }
        calc.parcialesDe(""); // construye los índices antes de repartir el trabajo
        ExecutorService pool = Executors.newFixedThreadPool(trabajadores);
        try {
            List<ResumenEstudiante> resultado = new ArrayList<>();
            for (Future<List<ResumenEstudiante>> f : pool.invokeAll(tareas)) {
                resultado.addAll(f.get());
            }
            return resultado;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cálculo interrumpido", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("falló un hilo de cálculo", e.getCause());
        } finally {
            pool.shutdown();
        }
    }
}
