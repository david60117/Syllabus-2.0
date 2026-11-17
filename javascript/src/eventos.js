/** Bus de eventos mínimo: el gestor publica y la interfaz se suscribe para refrescarse. */
export class BusEventos {
  #suscriptores = new Map();
  #todos = [];
  historial = [];

  suscribir(evento, funcion) {
    this.#suscriptores.set(evento, [...(this.#suscriptores.get(evento) ?? []), funcion]);
  }

  suscribirTodos(funcion) {
    this.#todos = [...this.#todos, funcion];
  }

  emitir(evento, datos = {}) {
    const mensaje = { evento, hora: new Date().toTimeString().slice(0, 8), ...datos };
    this.historial.push(mensaje);
    [...(this.#suscriptores.get(evento) ?? []), ...this.#todos].forEach((f) => f(mensaje));
    return mensaje;
  }
}
