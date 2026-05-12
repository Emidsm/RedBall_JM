package com.redball.level;

/*
 * Level2Builder — NO es necesario si usas TiledLevelBuilder directamente
 * con el .tmj del nivel cueva. Este archivo existe como referencia y
 * documentación de los ajustes específicos del nivel 2 (cueva oscura).
 *
 * ¿Cuándo usar este builder en lugar del TiledLevelBuilder genérico?
 *   - Si el tileset de la cueva tiene un PIXELS_PER_UNIT diferente.
 *   - Si quieres añadir lógica de parallax con múltiples capas de fondo.
 *   - Si los sensores (checkpoint/meta) necesitan sprites distintos.
 *
 * En la configuración actual, GameState ya llama a TiledLevelBuilder con
 * "Levels/nivel3_cueva.tmj" y funciona sin ninguna clase extra.
 * Ese .tmj debe tener:
 *
 *   PROPIEDADES DEL MAPA (Map Properties en Tiled):
 *     - nivel_visual  →  Textures/cave_level.png   (o el nombre de tu export)
 *
 *   CAPAS REQUERIDAS:
 *     1. tilelayer con class = "plataforma"
 *        → tiles del tileset Maaot (plataformas de roca/cueva, 512×512 px)
 *        → el builder fusionará tiles contiguos en una sola BoxCollisionShape
 *
 *     2. objectgroup con objetos de tipo:
 *        - "spawn"      → punto de aparición del jugador
 *        - "enemigo"    → rango de patrulla (width = margen de patrulla)
 *        - "checkpoint" → área de checkpoint
 *        - "meta"       → área de meta (fin del nivel)
 *
 *   TILESET (.tsx) REQUERIDO:
 *     El .tmj debe referenciar el .tsx del pack Maaot. El builder leerá
 *     automáticamente el tilewidth del .tsx (1024 px según descripción)
 *     para calcular las cajas de colisión correctamente.
 *     Si el .tsx no está disponible, usará FALLBACK_SPRITE_SIZE_PX = 512f.
 *     Ajusta ese fallback en TiledLevelBuilder si tus sprites son de 1024 px:
 *
 *       private static final float FALLBACK_SPRITE_SIZE_PX = 1024f;
 *
 * FONDO (Background2.png):
 *   GameState elige automáticamente "Textures/Background2.png" cuando
 *   levelIndex == 2. No necesitas nada extra aquí.
 *   Coloca el archivo en:  src/main/resources/Textures/Background2.png
 *
 * ASSETS DEL PACK MAAOT QUE NECESITAS EN src/main/resources/:
 *   Textures/Background2.png       ← el fondo de cueva (oscuro)
 *   Textures/cave_level.png        ← export visual del mapa desde Tiled
 *   Levels/nivel3_cueva.tmj        ← el mapa exportado desde Tiled
 *   Levels/cueva/musgoPiso.tsx     ← (o la ruta relativa que use tu .tmj)
 *
 * NOTAS DEL TILESET MAAOT:
 *   - Resolución mínima: 1024 px por sprite (algunos hasta 2048 px)
 *   - PIXELS_PER_UNIT en TiledLevelBuilder = 256f  → 1 unidad JME = 256 px
 *     Con sprites de 1024 px, cada sprite ocupa 4 unidades JME (≈ 4 metros)
 *   - Las plataformas del pack incluyen composiciones de pared + suelo + decoración.
 *     Úsalas en capas separadas: una para colisiones, otra sólo visual.
 *   - Las plantas/vegetación son secuencias PNG animadas; JME no las anima
 *     automáticamente — de momento úsalas como sprites estáticos (primer frame).
 *
 * CÓMO AÑADIR UN NIVEL 3, 4, …:
 *   1. Diseñar el mapa en Tiled y exportar .tmj + visual .png.
 *   2. Añadir la ruta del .tmj en GameState.LEVEL_PATHS[].
 *   3. Añadir el fondo correspondiente y seleccionarlo en GameState.buildBackground()
 *      según el levelIndex.
 *   4. No necesitas ninguna clase Builder extra.
 */
public class Level2Builder {
    // Ver comentario de archivo arriba — no se instancia directamente.
    private Level2Builder() {}
}
