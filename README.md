# PDF Quiz (Android)

Aplicación Android (Jetpack Compose) que convierte un PDF con resaltados de colores en un cuestionario interactivo. Las preguntas deben estar resaltadas con un tono fucsia y las respuestas con un naranja brillante (por ejemplo `RGB(255, 97, 0)`).

## Características principales

- Selector de documentos para cargar cualquier PDF almacenado en el dispositivo.
- Analizador basado en [pdfbox-android](https://github.com/TomRoush/PdfBox-Android) que extrae las anotaciones tipo *highlight* y clasifica preguntas y respuestas según su color.
- Generación automática de preguntas tipo test, barajando las respuestas detectadas y registrando la puntuación.
- Interfaz moderna con Jetpack Compose y Material 3, compatible con modo claro/oscuro.
- Compartir resultados y reiniciar el cuestionario rápidamente.

## Colores esperados

| Tipo | Rango aproximado |
|------|------------------|
| Pregunta | Rojo ≥ 220, Verde ≤ 140, Azul ≥ 200 |
| Respuesta | Rojo ≥ 220, 60 ≤ Verde ≤ 160, Azul ≤ 80 |

Puedes ajustar estos rangos en `PdfQuizParser.determineType` si tus resaltados utilizan valores distintos.

## Requisitos

- Android Studio Giraffe o posterior.
- Gradle 8.2 y Android Gradle Plugin 8.2.2.
- Dispositivo o emulador con Android 7.0 (API 24) o superior.

## Ejecución

1. Clona este repositorio y ábrelo con Android Studio.
2. Sincroniza los gradle scripts para descargar dependencias.
3. Compila y ejecuta la app en un dispositivo o emulador.
4. Usa el botón **Seleccionar PDF** para cargar tu archivo resaltado.

## Notas

- Cada pregunta se construye con el primer resaltado naranja que siga a un resaltado fucsia. Se pueden registrar varias alternativas de respuesta resaltadas consecutivamente.
- Si no se detectan resaltados válidos se mostrará un mensaje informativo; asegúrate de que el PDF incluye anotaciones tipo highlight creadas con Adobe Acrobat u otra herramienta compatible.
