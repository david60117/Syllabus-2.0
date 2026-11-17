package mx.uaeh.syllabus.modelo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Patrón observador: el gestor publica eventos y la interfaz se suscribe para refrescarse. */
public class BusEventos {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Map<String, List<Consumer<Evento>>> suscriptores = new HashMap<>();
    private final List<Consumer<Evento>> todos = new ArrayList<>();
    private final List<Evento> historial = new ArrayList<>();

    public void suscribir(String evento, Consumer<Evento> funcion) {
        suscriptores.computeIfAbsent(evento, k -> new ArrayList<>()).add(funcion);
    }

    public void suscribirTodos(Consumer<Evento> funcion) {
        todos.add(funcion);
    }

    public Evento emitir(String nombre, String... claveValor) {
        Map<String, String> datos = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) {
            datos.put(claveValor[i], claveValor[i + 1]);
        }
        Evento evento = new Evento(nombre, LocalTime.now().format(HORA), datos);
        historial.add(evento);
        suscriptores.getOrDefault(nombre, List.of()).forEach(f -> f.accept(evento));
        todos.forEach(f -> f.accept(evento));
        return evento;
    }

    public List<Evento> getHistorial() {
        return historial;
    }
}
