# Traductor — cómo fabricar e instalar la app

GitHub arma la app solo. Vos subís estos archivos una vez y en unos minutos tenés el instalador (APK) listo para el celular.

## Paso 1 — Crear el repositorio
1. Entrá a github.com con tu cuenta.
2. Arriba a la derecha tocá el **+** → **New repository**.
3. En "Repository name" escribí: `traductor`
4. Dejalo en **Public** (el código no tiene ninguna clave tuya, es seguro).
5. Abajo de todo, botón verde **Create repository**.

## Paso 2 — Subir los archivos
1. En la página nueva, buscá el link **uploading an existing file** y tocalo.
2. En tu PC abrí la carpeta `traductor` que descomprimiste.
3. Seleccioná **todo lo que hay adentro** (Ctrl + A) — incluida la carpeta `.github` — y arrastralo a la página de GitHub.
4. Esperá que terminen de subir y tocá el botón verde **Commit changes**.

> Importante: tiene que subir la carpeta `.github`. Es la "receta" para fabricar la app.

## Paso 3 — Esperar que se fabrique
1. Arriba en el repositorio tocá la pestaña **Actions**.
2. Vas a ver "Armar la app" con un círculo amarillo girando. Tarda entre 5 y 10 minutos.
3. Cuando termina aparece un **tilde verde** ✅.
4. Si aparece una **cruz roja** ❌: tocala, sacale captura y mandámela.

## Paso 4 — Instalar en el celular
1. En el celular abrí: `https://github.com/Rommee-11/traductor/releases`
2. Tocá **Traductor.apk** para descargarlo.
3. Abrí el archivo descargado. Android va a pedir permiso para "instalar apps desconocidas": permitilo para el navegador.
4. Tocá **Instalar**.

Para pasárselo a otro celular Android: mandale el archivo `Traductor.apk` por WhatsApp o pasale el link del paso 4.

## Paso 5 — Primera vez (con wifi)
La app te guía en 3 pasos: permiso de micrófono, descargar idiomas y voz sin internet.

## Actualizaciones
Cada vez que subas archivos nuevos, GitHub fabrica una versión nueva. Se instala encima de la anterior sin perder nada.
