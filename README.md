# GensAuto · Fabric 1.21.11

Panel con el nivel y el prestigio de tu **Pico de Minería** y tu **Azada Cosechadora**, y rutas grabadas por ti que el mod repite en orden aleatorio.

> **Aviso:** el minado y la cosecha automáticos repiten tus propias acciones. Úsalo bajo tu responsabilidad: algunos servidores prohíben la automatización, así que revisa sus reglas antes de usarlo.

## Instalar

1. Minecraft Java **1.21.11** con **Fabric Loader 0.18.4+** y **Fabric API 0.141.6+1.21.11**.
2. Descarga `gensauto-1.0.0.jar` desde [Releases](https://github.com/ArangoMzl/GensAuto/releases/latest) y cópialo en la carpeta `mods` del perfil.
   - **Lunar Client:** `C:\Users\<tú>\.lunarclient\profiles\1.21\mods\fabric-1.21.11`
3. Puede convivir con TimerGod.

Es solo de cliente: el servidor no necesita instalarlo.

## Teclas

| Tecla inicial | Acción |
| --- | --- |
| **U** | Empezar a grabar una ruta (y guardarla antes de tiempo, si quieres) |
| **J** | Reproducir la lista de la herramienta en tu mano / detener |
| **Y** | Menú de rutas y listas |
| (sin asignar) | Mostrar u ocultar el panel |

Se cambian en **Opciones → Controles → Asignación de teclas → GensAuto**.

## El panel

Aparece con la herramienta en la mano (o la última que usaste):

- Nombre, **Prestigio** (P) y **Renacimiento** (R).
- **Nivel actual / nivel para prestigiar** y barra de progreso.
- **Niveles que faltan y tiempo estimado**, según tu ritmo de los últimos 15 minutos. Necesita 1 minuto de datos.
- Ganancia de la barra del servidor (`Tokens Farm.` o `Esencias Farm.`).
- Tiempo de sesión, niveles ganados y niveles por minuto.
- La lista en uso y si tiene rutas que empiecen donde estás.

El nivel y el porcentaje se leen de la barra de acción del servidor cada vez que cambia. Si la barra no llega por más de 10 s, se usa la descripción del ítem.

**Requisito de prestigio:** viene con los valores que viste (Pico 150 en Prestigio 5, Azada 125 en Prestigio 4). Cada vez que abres **Menú → Prestigio**, se lee de la campana y se guarda. Si tu prestigio cambió, el panel muestra `150?` hasta que vuelvas a abrir ese menú.

## Grabar una ruta

1. Toma la herramienta y **párate donde quieras empezar**.
2. Presiona **U**. El punto de inicio queda marcado en el mundo con **una columna de luz y un anillo verde**. El panel muestra a cuántos bloques está y hacia dónde («Inicio a 5.1 bl, atrás a la izquierda»).
3. Cosecha o mina como siempre: camina, salta, agáchate, gira, haz clic.
4. **Vuelve a pasar por el punto marcado**: la ruta **se guarda sola**. Solo pasa si antes te alejaste más de 3 bloques y llevas al menos 3 segundos grabando. Si quieres terminar antes, presiona **U**.

Si te teletransportan mientras grabas, la grabación se cancela.

## Listas Farm y Minería

Las rutas se organizan en listas, en dos pestañas: **Farm** (azada) y **Minería** (pico).

- Si grabas con la **azada**, la ruta va a la lista de Farm **en uso**. Con el **pico**, a la de Minería en uso.
- **J** reproduce la lista en uso **de la herramienta que tienes en la mano**. Si grabas en el farm, te vas a minar y presionas J con el pico, usa la de Minería sin que cambies nada.
- La lista se reproduce así: elige al azar una ruta que empiece a 3 bloques o menos de ti, la recorre y al volver elige otra. Nunca repite la misma dos veces seguidas.
- Si estás lejos del inicio, J no arranca, te dice a cuántos bloques está y lo marca con partículas por 15 segundos.

**Menú (Y):**
- **Pestañas Farm / Minería**, con el total de rutas de cada una.
- **◀ ▶**: cambiar entre las listas de esa pestaña.
- **Usar esta**: la vuelve la lista en uso (la que J reproduce y donde se guardan las rutas nuevas).
- **+ Nueva lista**, **Renombrar**, **Borrar lista** (borra también sus rutas; pide confirmación).
- En cada ruta:
  - **Sí/No**: usarla o no al reproducir.
  - **Mover**: pasarla a la siguiente lista de la misma pestaña.
  - **▶**: probarla una vez.
  - **✕**: borrarla (pide confirmación).

Todo se guarda en `config/gensauto-routes.json` en cuanto cambia, y sigue ahí al cerrar y abrir el juego. Las listas son por servidor.

## Cómo se reproduce

Usa las mismas entradas que tú. Repite tu cámara, tus clics, saltos, agacharte y correr como los grabaste. El clic pasa por el mismo paso del juego que lee tu mouse, una vez por tick, y no se envían paquetes propios.

Para caminar, en cada tick elige W, A, S o D según el punto de la grabación donde deberías estar. Si vas más rápido o más lento que al grabar (lag, un efecto de Velocidad), se corrige solo.

**Se pausa** mientras tengas cualquier menú o el chat abierto.

**Se detiene** (con aviso en el chat y un sonido) si:
- algo te saca de la ruta por más de 3 segundos,
- cambias de herramienta,
- recibes daño,
- te teletransportan,
- o sales del servidor.

Mientras está activo, desactiva «Pausar al perder el foco», así sigue funcionando si cambias de ventana. Al detenerse, lo deja como estaba. **No minimices el juego.**

## Ajustes

En `config/gensauto.json` (cierra el juego antes de editarlo):

| Campo | Por defecto | Qué hace |
| --- | --- | --- |
| `turnSpeed` | 9 | Grados por tick que gira la cámara al alinearse con el inicio de una ruta |
| `stopOnDamage` | true | Detener al recibir daño |
| `hudX`, `hudY`, `hudScale` | 0, 0.35, 1 | Posición (0 a 1) y tamaño del panel |

## Compilar

```bash
./gradlew build
```

Necesita JDK 21. La prueba automática dentro del juego se ejecuta con `./gradlew -Pqa runQa`. Es opcional y ese código no se incluye en el jar.
