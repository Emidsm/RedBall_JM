package com.redball.level;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.redball.entities.Enemy;
import com.redball.entities.Player;

import java.io.InputStreamReader;
import java.util.*;

public class TiledLevelBuilder {

    private static final float PIXELS_PER_UNIT = 256f;

    // -------------------------------------------------------------------------
    // Si tu tileset usa sprites más grandes que 1 tile (ej. 512×512px),
    // Tiled los posiciona en la cuadrícula pero cada uno ocupa SPRITE_SIZE px.
    // Este valor se lee del .tsx automáticamente (ver readTilesetSpriteSize).
    // Si no se puede leer el .tsx, se usa este fallback.
    // -------------------------------------------------------------------------
    private static final float FALLBACK_SPRITE_SIZE_PX = 512f;

    // Dos tiles en columnas distintas se consideran la MISMA plataforma
    // si están a menos de MERGE_GAP_TILES tiles de distancia horizontal/vertical.
    private static final int MERGE_GAP_TILES = 20;

    private final AssetManager  assetManager;
    private final PhysicsSpace  physicsSpace;
    private final Node          levelNode;

    public TiledLevelBuilder(AssetManager assetManager, PhysicsSpace physicsSpace, Node levelNode) {
        this.assetManager = assetManager;
        this.physicsSpace  = physicsSpace;
        this.levelNode     = levelNode;
    }

    // =========================================================================
    // BUILD — punto de entrada principal
    // =========================================================================

    public Vector3f build(String assetPath, List<Enemy> enemyList) {
        Vector3f spawnPoint = new Vector3f(0f, 2f, 0f);

        try {
            AssetInfo info = assetManager.locateAsset(new AssetKey<>(assetPath));
            if (info == null) throw new IllegalArgumentException("Nivel no encontrado: " + assetPath);

            InputStreamReader reader = new InputStreamReader(info.openStream());
            TiledMap mapData = new Gson().fromJson(reader, TiledMap.class);
            reader.close();

            // ------------------------------------------------------------------
            // 1. Leer propiedad nivel_visual (o "background" — acepta ambos)
            // ------------------------------------------------------------------
            String nivelVisualTex = "Textures/fallback.png";
            if (mapData.properties != null) {
                for (TiledProperty prop : mapData.properties) {
                    if ("nivel_visual".equals(prop.name) || "background".equals(prop.name)) {
                        nivelVisualTex = prop.value;
                    }
                }
            }

            float mapWidthMeters  = (mapData.width  * mapData.tilewidth)  / PIXELS_PER_UNIT;
            float mapHeightMeters = (mapData.height * mapData.tileheight) / PIXELS_PER_UNIT;

            createVisualMap(mapWidthMeters, mapHeightMeters, nivelVisualTex);

            // ------------------------------------------------------------------
            // 2. Detectar el tamaño real de los sprites del tileset
            //    (necesario para calcular bien las cajas de colisión)
            // ------------------------------------------------------------------
            float spriteSizePx = FALLBACK_SPRITE_SIZE_PX;
            if (mapData.tilesets != null && mapData.tilesets.length > 0) {
                String tsxSource = mapData.tilesets[0].source; // ej. "musgo/musgoPiso.tsx"
                float detected = readTilesetSpriteSize(tsxSource, assetPath);
                if (detected > 0) spriteSizePx = detected;
            }

            // ------------------------------------------------------------------
            // 3. Generar colisiones desde el tilelayer — filtrando por clase
            // ------------------------------------------------------------------
            if (mapData.layers != null) {
                for (TiledLayer layer : mapData.layers) {
                    String capaClass = layer.tiledClass != null ? layer.tiledClass : "";
                    
                    if ("tilelayer".equals(layer.type) && layer.data != null && "plataforma".equals(capaClass)) {
                        buildCollisionsFromTileLayer(layer.data, mapData.width,
                                mapData.tilewidth, mapData.tileheight, spriteSizePx); 
                    }
                }
            }

            // ------------------------------------------------------------------
            // 4. Leer la capa de objetos — SOLO para spawn y enemigos
            //    (los rectángulos "plataforma" ya no se usan aquí)
            // ------------------------------------------------------------------
            if (mapData.layers != null) {
                for (TiledLayer layer : mapData.layers) {
                    if ("objectgroup".equals(layer.type) && layer.objects != null) {
                        for (TiledObject obj : layer.objects) {

                            String objectType = resolveType(obj).toLowerCase();

                            float width   = obj.width  / PIXELS_PER_UNIT;
                            float height  = obj.height / PIXELS_PER_UNIT;
                            float centerX = (obj.x + (obj.width  / 2f)) / PIXELS_PER_UNIT;
                            float centerY = -(obj.y + (obj.height / 2f)) / PIXELS_PER_UNIT;

                            switch (objectType) {
                                case "spawn":
                                    spawnPoint = new Vector3f(centerX, centerY, 0f);
                                    break;
                                case "enemigo":
                                    Enemy e = new Enemy(assetManager, physicsSpace,
                                            centerX - (width / 2f), centerX + (width / 2f));
                                    e.setLocalTranslation(centerX, centerY, 0f);
                                    levelNode.attachChild(e);
                                    enemyList.add(e);
                                    break;
                                case "checkpoint":
                                    createSensor(centerX, centerY, width, height, "Textures/checkpoint.png", com.jme3.math.ColorRGBA.Blue, "Checkpoint");
                                    break;
                                case "meta":
                                    createSensor(centerX, centerY, width, height, "Textures/flag.png", com.jme3.math.ColorRGBA.Yellow, "Meta");
                                    break;
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[TiledLevelBuilder] Error cargando el nivel:");
            e.printStackTrace();
        }

        return spawnPoint;
    }

    // =========================================================================
    // COLISIONES AUTOMÁTICAS DESDE EL TILELAYER
    // =========================================================================

    /**
     * Lee todos los tiles no-vacíos del layer, los agrupa por proximidad
     * y crea un BoxCollisionShape por grupo.
     *
     * Así un "piso" hecho de varios tiles adyacentes se convierte en
     * UNA sola caja sólida, igual que si hubieras dibujado el rectángulo
     * en la capa de objetos — pero alineada pixel-perfect con el visual.
     */
    private void buildCollisionsFromTileLayer(int[] data, int mapWidth, int tileW, int tileH, float spriteSizePx) {
        int mapHeight = data.length / mapWidth;
        int colStep = (int) (spriteSizePx / tileW); 

        for (int row = 0; row < mapHeight; row++) {
            for (int col = 0; col < mapWidth; col++) {
                int index = row * mapWidth + col;
                
                if (data[index] != 0) {
                    int startCol = col;
                    int endCol = col;
                    
                    while (endCol + colStep < mapWidth && data[row * mapWidth + (endCol + colStep)] != 0) {
                        endCol += colStep;
                        data[row * mapWidth + endCol] = 0; 
                    }
                    
                    // Solo un toque estético para hundirnos en el pasto visual, sin romper el juego
                    float PADDING_TOP = 16f; 
                    float PADDING_SIDES = 6f; 
                    float PADDING_BOTTOM = 0f;

                    float xLeft_px = startCol * tileW + PADDING_SIDES;
                    float xRight_px = endCol * tileW + spriteSizePx - PADDING_SIDES;
                    
                    float yBottom_px = (row + 1) * tileH - PADDING_BOTTOM; 
                    float yTop_px = ((row + 1) * tileH - spriteSizePx) + PADDING_TOP; 

                    float x1 = xLeft_px / PIXELS_PER_UNIT;
                    float x2 = xRight_px / PIXELS_PER_UNIT;
                    float yTop_jme = -yTop_px / PIXELS_PER_UNIT;
                    float yBottom_jme = -yBottom_px / PIXELS_PER_UNIT;

                    float cx = (x1 + x2) / 2f;
                    float cy = (yTop_jme + yBottom_jme) / 2f;
                    float w = x2 - x1;
                    float h = yTop_jme - yBottom_jme;

                    createInvisiblePlatform(cx, cy, w, h);
                }
            }
        }
    }

    // =========================================================================
    // LEER TAMAÑO DE SPRITE DESDE EL .TSX
    // =========================================================================

    /**
     * Intenta leer el .tsx para obtener el tilewidth/tileheight real del tileset,
     * que puede ser mayor que el tilewidth del mapa (ej. sprites de 512px en un
     * mapa con cuadrícula de 32px).
     *
     * Retorna el tilewidth del tileset, o -1 si no se pudo leer.
     */
    private float readTilesetSpriteSize(String tsxRelativePath, String tmjAssetPath) {
        try {
            // El .tsx está relativo al .tmj — construir la ruta completa
            String tmjDir = "";
            int lastSlash = tmjAssetPath.lastIndexOf('/');
            if (lastSlash >= 0) tmjDir = tmjAssetPath.substring(0, lastSlash + 1);

            String tsxPath = tmjDir + tsxRelativePath;
            AssetInfo info = assetManager.locateAsset(new AssetKey<>(tsxPath));
            if (info == null) {
                System.out.println("[TiledLevelBuilder] .tsx no encontrado en assets: " + tsxPath);
                return -1;
            }

            InputStreamReader reader = new InputStreamReader(info.openStream());
            TilesetData ts = new Gson().fromJson(reader, TilesetData.class);
            reader.close();

            if (ts != null && ts.tilewidth > 0) {
                System.out.println("[TiledLevelBuilder] Sprite size desde .tsx: " + ts.tilewidth + "px");
                return ts.tilewidth;
            }
        } catch (Exception e) {
            System.err.println("[TiledLevelBuilder] No se pudo leer el .tsx: " + e.getMessage());
        }
        System.out.println("[TiledLevelBuilder] Usando fallback sprite size: " + FALLBACK_SPRITE_SIZE_PX + "px");
        return -1;
    }

    // =========================================================================
    // HELPERS VISUALES Y FÍSICOS
    // =========================================================================

    private void createVisualMap(float mapWidthMeters, float mapHeightMeters, String texPath) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        
        float imgWidthMeters = mapWidthMeters;
        float imgHeightMeters = mapHeightMeters;

        try {
            com.jme3.texture.Texture tex = assetManager.loadTexture(texPath);
            tex.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
            mat.setTexture("ColorMap", tex);
            
            // ¡LA MAGIA ESTÁ AQUÍ! Leer el tamaño REAL de la imagen exportada para que no se aplaste
            imgWidthMeters = tex.getImage().getWidth() / PIXELS_PER_UNIT;
            imgHeightMeters = tex.getImage().getHeight() / PIXELS_PER_UNIT;
            
        } catch (Exception e) {
            System.err.println("[TiledLevelBuilder] Textura no encontrada: " + texPath);
        }

        Quad quad = Player.buildCenteredQuad(imgWidthMeters, imgHeightMeters);
        Geometry geom = new Geometry("LevelVisuals", quad);
        geom.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        geom.setMaterial(mat);

        // Alineamos la esquina inferior-izquierda de la IMAGEN con la esquina inferior-izquierda del MAPA LÓGICO.
        // Como los sprites grandes en Tiled desbordan hacia arriba y a la derecha, esto jamás fallará.
        float centerX = imgWidthMeters / 2f;
        float centerY = -mapHeightMeters + (imgHeightMeters / 2f);
        
        geom.setLocalTranslation(centerX, centerY, -2f);
        levelNode.attachChild(geom);
    }

    private void createInvisiblePlatform(float cx, float cy, float w, float h) {
        BoxCollisionShape shape = new BoxCollisionShape(w / 2f, h / 2f, 1.0f);
        RigidBodyControl rb = new RigidBodyControl(shape, 0f);

        Node physicsNode = new Node("PlatformPhysics");
        
        // 1. PRIMERO movemos el Node a su posición en el mundo
        physicsNode.setLocalTranslation(cx, cy, 0f);
        
        // 2. LUEGO le pegamos el control para que lea esa posición
        physicsNode.addControl(rb);

        levelNode.attachChild(physicsNode);
        physicsSpace.add(rb);
    }

    private static String resolveType(TiledObject obj) {
        if (obj.tiledClass != null && !obj.tiledClass.isEmpty()) return obj.tiledClass;
        if (obj.type       != null && !obj.type.isEmpty())       return obj.type;
        if (obj.name       != null && !obj.name.isEmpty())       return obj.name;
        return "";
    }

    // =========================================================================
    // DTOs — espejo exacto del JSON que exporta Tiled
    // =========================================================================

    private static class TiledMap {
        int width, height;
        int tilewidth, tileheight;
        TiledLayer[]    layers;
        TiledProperty[] properties;
        TilesetRef[]    tilesets;
    }

    private static class TilesetRef {
        int    firstgid;
        String source;   // ruta relativa al .tsx
    }

    private static class TilesetData {
        int    tilewidth;
        int    tileheight;
        // otros campos del .tsx que no necesitamos ignorados por Gson
    }

    private static class TiledProperty {
        String name;
        String type;
        String value;
    }

    private static class TiledLayer {
        String type;
        int[]        data;     // tilelayer
        TiledObject[] objects; // objectgroup
        
        // ¡Faltaba esto para que detecte la clase "plataforma" de Tiled!
        @SerializedName("class") String tiledClass; 
    }

    private static class TiledObject {
        float  x, y, width, height;
        String type;
        String name;
        @SerializedName("class") String tiledClass;
    }

    private void createSensor(float cx, float cy, float w, float h, String texPath, com.jme3.math.ColorRGBA fallbackColor, String tag) {
        Quad quad = Player.buildCenteredQuad(w, h);
        Geometry geom = new Geometry(tag + "Visual", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        
        try {
            com.jme3.texture.Texture tex = assetManager.loadTexture(texPath);
            tex.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", fallbackColor);
        }
        
        geom.setMaterial(mat);
        geom.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);

        // Física Fantasma (Detecta colisión pero no bloquea el paso)
        com.jme3.bullet.collision.shapes.BoxCollisionShape shape = new com.jme3.bullet.collision.shapes.BoxCollisionShape(w / 2f, h / 2f, 1.0f);
        com.jme3.bullet.control.GhostControl ghost = new com.jme3.bullet.control.GhostControl(shape);
        
        Node sensorNode = new Node(tag); 
        sensorNode.setLocalTranslation(cx, cy, -1f); 
        sensorNode.attachChild(geom);
        sensorNode.addControl(ghost);
        
        // El truco de magia: Guardamos el Node en las físicas para identificarlo después
        ghost.setApplicationData(sensorNode);

        levelNode.attachChild(sensorNode);
        physicsSpace.add(ghost);
    }
}