# Los agentes de RecyCol

Esta carpeta guarda **la memoria de trabajo de cada agente**: quién es, qué
carpetas puede tocar, y todas las lecciones que este proyecto aprendió rompiendo
cosas.

Antes había que pegar dos páginas de contexto cada vez que se lanzaba un agente.
Ahora no: basta con **pedirlo por su nombre**. Claude Code lee el archivo que le
corresponde y arranca sabiendo dónde está.

## Cómo se lanza uno

Escribe una frase normal, diciendo el nombre del agente y qué quieres:

> «Usa el agente **front** para arreglar la issue #157, la de girar la pantalla.»

> «Lanza **ml** y que evalúe el último checkpoint contra el control.»

> «Que **qa** revise si `main` está verde y fusione lo que esté listo.»

Eso es todo. No hace falta explicarle el proyecto: **lo primero que hace cada
agente es leer `CONTEXTO.md`**, que es el documento único con el estado real. Por
eso estas definiciones no se quedan obsoletas — el estado vive en `CONTEXTO.md`,
no aquí.

Se pueden lanzar **varios a la vez** si trabajan en carpetas distintas. Cada uno
crea su propia rama y su propio PR.

## Quién es quién

| Agente | De qué se ocupa | Pídeselo cuando… |
|---|---|---|
| **core** | Los contratos que comparten todos: puertos de dominio, dependencias, Gradle, Docker, CI | hay que cambiar una interfaz común, añadir una librería o tocar el build |
| **front** | La interfaz: pantallas, design system, Liquid Glass, animaciones | algo se ve mal, falta una pantalla o hay que cambiar un texto |
| **camara** | La cámara y si una foto sirve o no (nitidez, luz, mancha de lente) | la app no captura bien, o pide «acércate» sin parar |
| **inferencia** | Que los modelos corran en el teléfono: LiteRT, gamas, velocidad | hay que cablear un modelo nuevo o medir la latencia |
| **ml** | Datasets, entrenamiento y evaluación de los modelos | hay que entrenar, evaluar o mejorar el acierto |
| **reglas** | La norma: en qué caneca va cada cosa, por país e institución | hay que añadir un país o cambiar una regla de caneca |
| **auth** | Login y lo que se guarda en el teléfono (historial, preferencias) | falla el historial o hay que tocar el almacenamiento local |
| **qa** | CI, runners, fusionar PRs y probar en el teléfono de verdad | algo está rojo, hay PRs esperando o hay que auditar la app |
| **datos** | RecyCol Aporta: la web donde la gente sube fotos, y Azure | hay que cambiar la web de aportación o bajar el dataset |

## Lo que todos tienen metido dentro

No hace falta recordárselo, ya lo llevan escrito:

- Leer `CONTEXTO.md` antes de tocar nada.
- Trabajar **en su propia rama**, nunca directamente sobre `main`.
- **Nunca `git add -A`** — una vez arrastró trabajo sin terminar de otro agente.
- **Una issue, una rama, un PR.**
- **CI en verde antes de fusionar**, sin excepciones.
- **No terminar el turno dejando algo a medias**, y si dejaron algo largo
  corriendo, ir a comprobarlo en vez de dar por hecho que sigue vivo.
- Cada uno sabe **qué carpetas son suyas** y cuáles no debe tocar.

## Si quieres cambiar algo

Cada archivo `.md` de esta carpeta es un agente. Se editan como texto normal: la
cabecera de arriba dice el nombre y qué modelo usa, y el resto es lo que el
agente lee al arrancar. Si una lección nueva vale para todos, va en `CONTEXTO.md`;
si es solo de un rol, va en su archivo.
