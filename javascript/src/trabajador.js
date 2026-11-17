import { parentPort, workerData } from 'node:worker_threads';
import { Calculadora } from './gestor.js';

const calc = Calculadora.desdeObjeto(workerData.datos);
parentPort.postMessage(workerData.cuentas.map((nc) => calc.resumenEstudiante(nc)));
