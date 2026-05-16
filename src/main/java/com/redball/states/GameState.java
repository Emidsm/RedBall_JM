package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.redball.entities.Enemy;
import com.redball.entities.Player;
import com.redball.level.TiledLevelBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * GameState — bucle principal del juego.
 *
 * Cambios respecto a la versión anterior:
 *  - Recibe un índice de nivel (1 = musgo, 2 = cueva) en lugar de una ruta fija.
 *  - Al ganar activa WinMenuState (si hay más niveles) o la pantalla final.
 *  - Al perder todas las vidas activa GameOverOverlay.
 *  - Se elimina la lógica de "Retry" con teclado: todo pasa por los menús GUI.
 */
public class GameState extends AbstractAppState implements PhysicsCollisionListener, ActionListener {

    // =========================================================================
    // MAPA DE NIVELES — añade aquí rutas para nuevos niveles
    // =========================================================================

    /** Rutas de los archivos .tmj, indexadas a partir de 1. */
    // =========================================================================
    // MAPA DE NIVELES — añade aquí rutas para nuevos niveles
    // =========================================================================

    /** Rutas de los archivos .tmj, indexadas a partir de 1. */
    private static final String[] LEVEL_PATHS = {
        null,                              // [0] no usado
        "Levels/nivel2_musgo.tmj",         // [1] nivel musgo
        "Levels/nivel3_cueva.tmj",         // [2] nivel cueva
        "Levels/nivel3_musgo.tmj"          // [3] NUEVO nivel 3
    };

    /** Número total de niveles disponibles. */
    public static final int TOTAL_LEVELS = LEVEL_PATHS.length - 1;

    // =========================================================================
    // CAMPOS
    // =========================================================================

    private final int levelIndex; // nivel que estamos ejecutando ahora

    private SimpleApplication app;
    private AssetManager      assetManager;
    private Node              rootNode;
    private Camera            cam;
    private InputManager      inputManager;
    private BulletAppState    bulletAppState;

    // Audio
    private com.jme3.audio.AudioNode bgm;
    private com.jme3.audio.AudioNode checkpointSfx;
    private com.jme3.audio.AudioNode winSfx;
    private com.jme3.audio.AudioNode squashSfx;
    private com.jme3.audio.AudioNode hitSfx;
    private com.jme3.audio.AudioNode deathSfx;

    private Node levelNode;

    private Player       player;
    private List<Enemy>  enemies = new ArrayList<>();

    // Input flags
    private boolean moveLeft  = false;
    private boolean moveRight = false;

    // Cámara follow
    private static final float CAM_SMOOTHING = 5f;
    private static final float CAM_OFFSET_Y  = 1.5f;

    // Fondo dinámico
    private com.jme3.scene.Geometry backgroundGeom;
    private String backgroundTexPath; // depende del nivel

    // Respawn
    private static final float RESPAWN_DELAY = 1.2f;
    private float    respawnTimer   = 0f;
    private boolean  playerDead     = false;
    private boolean  ignoreKillZone = false;
    private Vector3f spawnPoint     = new Vector3f(-8f, 1f, 0f);

    // Control de estado
    private boolean levelFinished = false; // true cuando se toca la meta

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public GameState(BulletAppState bulletAppState, int levelIndex) {
        this.bulletAppState = bulletAppState;
        this.levelIndex     = levelIndex;
    }

    // =========================================================================
    // INITIALIZE
    // =========================================================================

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);

        this.app          = (SimpleApplication) application;
        this.assetManager = app.getAssetManager();
        this.rootNode     = app.getRootNode();
        this.cam          = app.getCamera();
        this.inputManager = app.getInputManager();

        levelNode = new Node("LevelNode");
        rootNode.attachChild(levelNode);

        bulletAppState.getPhysicsSpace().addCollisionListener(this);
        enemies.clear();

        // Seleccionar fondo según nivel
        if (levelIndex == 2) {
            backgroundTexPath = "Textures/Background2.png";
        } else if (levelIndex == 3) {
            backgroundTexPath = "Textures/Background3.png";
        } else {
            backgroundTexPath = "Textures/background.png"; // Fondo por defecto (Nivel 1)
        }

        // Construir el nivel mediante TiledLevelBuilder
        String levelPath = resolveLevelPath(levelIndex);
        TiledLevelBuilder builder = new TiledLevelBuilder(assetManager,
                bulletAppState.getPhysicsSpace(), levelNode);
        this.spawnPoint = builder.build(levelPath, enemies);

        buildBackground();

        // Crear al jugador
        player = new Player(assetManager, bulletAppState.getPhysicsSpace());
        player.respawn(spawnPoint.clone());
        levelNode.attachChild(player);

        registerInput();
        initAudio();
    }

    // =========================================================================
    // RESOLUCIÓN DE NIVEL
    // =========================================================================

    private String resolveLevelPath(int index) {
        if (index >= 1 && index < LEVEL_PATHS.length) {
            return LEVEL_PATHS[index];
        }
        // Fuera de rango → volver al nivel 1
        return LEVEL_PATHS[1];
    }

    // =========================================================================
    // FONDO
    // =========================================================================

    private void buildBackground() {
        float bgW = 100f, bgH = 40f;
        com.jme3.scene.shape.Quad quad =
                (com.jme3.scene.shape.Quad) Player.buildCenteredQuad(bgW, bgH);
        backgroundGeom = new com.jme3.scene.Geometry("Background", quad);

        com.jme3.material.Material mat = new com.jme3.material.Material(
                assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            com.jme3.texture.Texture tex = assetManager.loadTexture(backgroundTexPath);
            tex.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new com.jme3.math.ColorRGBA(0.05f, 0.03f, 0.10f, 1f));
        }
        backgroundGeom.setMaterial(mat);
        backgroundGeom.setLocalTranslation(0f, 0f, -9f);
        rootNode.attachChild(backgroundGeom);
    }

    private void updateBackground() {
        if (backgroundGeom == null) return;
        Vector3f camPos = cam.getLocation();
        backgroundGeom.setLocalTranslation(camPos.x, camPos.y, -9f);
    }

    // =========================================================================
    // AUDIO
    // =========================================================================

    private void initAudio() {
        try {
            bgm = new com.jme3.audio.AudioNode(assetManager, "Sounds/bgm.ogg",
                    com.jme3.audio.AudioData.DataType.Stream);
            bgm.setPositional(false);
            bgm.setLooping(true);
            bgm.setVolume(0.5f);
            rootNode.attachChild(bgm);
            bgm.play();

            checkpointSfx = loadSfx("Sounds/checkpoint.ogg");
            winSfx        = loadSfx("Sounds/win.ogg");
            squashSfx     = loadSfx("Sounds/squash.ogg");
            hitSfx        = loadSfx("Sounds/hit.ogg");
            deathSfx      = loadSfx("Sounds/death.ogg");
        } catch (Exception e) {
            System.err.println("[GameState] Error cargando audio: " + e.getMessage());
        }
    }

    private com.jme3.audio.AudioNode loadSfx(String path) {
        try {
            com.jme3.audio.AudioNode sfx = new com.jme3.audio.AudioNode(assetManager, path,
                    com.jme3.audio.AudioData.DataType.Buffer);
            sfx.setPositional(false);
            return sfx;
        } catch (Exception e) {
            System.err.println("[GameState] SFX no encontrado: " + path);
            return null;
        }
    }

    // =========================================================================
    // INPUT
    // =========================================================================

    private void registerInput() {
        inputManager.addMapping("MoveLeft",  new KeyTrigger(KeyInput.KEY_LEFT),  new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("MoveRight", new KeyTrigger(KeyInput.KEY_RIGHT), new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Jump",      new KeyTrigger(KeyInput.KEY_SPACE),
                                             new KeyTrigger(KeyInput.KEY_UP),
                                             new KeyTrigger(KeyInput.KEY_W));
        inputManager.addListener(this, "MoveLeft", "MoveRight", "Jump");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "MoveLeft":  moveLeft  = isPressed; break;
            case "MoveRight": moveRight = isPressed; break;
            case "Jump":
                if (isPressed && !playerDead && !levelFinished) player.tryJump();
                break;
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Override
    public void update(float tpf) {
        if (!isEnabled() || levelFinished) return;

        if (playerDead) {
            respawnTimer -= tpf;
            if (respawnTimer <= 0f) respawnPlayer();
            updateCamera(tpf);
            updateBackground();
            return;
        }

        if (moveLeft)  player.rollLeft(tpf);
        if (moveRight) player.rollRight(tpf);

        // Enemigos
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            e.updateEnemy(tpf);
            if (e.isReadyToRemove()) {
                levelNode.detachChild(e);
                bulletAppState.getPhysicsSpace().remove(e.getPhysicsControl());
                it.remove();
            }
        }

        updateCamera(tpf);
        updateBackground();

        if (player.isExploding()) {
            player.updateExplosion(tpf);
        }

        // Kill zone
        if (!ignoreKillZone) {
            Vector3f physPos = player.getPhysicsPosition();
            if (physPos != null && physPos.y < -20f) {
                triggerPlayerDeath();
            }
        }
        ignoreKillZone = false;
    }

    // =========================================================================
    // CÁMARA
    // =========================================================================

    private void updateCamera(float tpf) {
        Vector3f playerPos = playerDead ? cam.getLocation() : player.getPhysicsPosition();
        if (playerPos == null) return;

        float targetX = Math.max(playerPos.x, 0f);
        float targetY = playerPos.y + CAM_OFFSET_Y;

        Vector3f current = cam.getLocation();
        float smoothX = com.jme3.math.FastMath.interpolateLinear(CAM_SMOOTHING * tpf, current.x, targetX);
        float smoothY = com.jme3.math.FastMath.interpolateLinear(CAM_SMOOTHING * tpf, current.y, targetY);

        cam.setLocation(new Vector3f(smoothX, smoothY, 10f));
    }

    // =========================================================================
    // COLISIONES
    // =========================================================================

    @Override
    public void collision(PhysicsCollisionEvent event) {
        PhysicsCollisionObject pcoA = event.getObjectA();
        PhysicsCollisionObject pcoB = event.getObjectB();

        Object dataA = (pcoA != null) ? pcoA.getApplicationData() : null;
        Object dataB = (pcoB != null) ? pcoB.getApplicationData() : null;

        boolean playerInvolved = (dataA instanceof Player) || (dataB instanceof Player);
        if (!playerInvolved || playerDead) return;

        // --- Sensores (Checkpoint / Meta) ---
        Node sensorNode = null;
        if (dataA instanceof Node) {
            String n = ((Node)dataA).getName();
            if ("Checkpoint".equals(n) || "Meta".equals(n)) sensorNode = (Node) dataA;
        }
        if (sensorNode == null && dataB instanceof Node) {
            String n = ((Node)dataB).getName();
            if ("Checkpoint".equals(n) || "Meta".equals(n)) sensorNode = (Node) dataB;
        }

        if (sensorNode != null) {
            if ("Checkpoint".equals(sensorNode.getName())) {
                Vector3f pos = sensorNode.getLocalTranslation().clone();
                pos.z = 0f;
                this.spawnPoint.set(pos);

                com.jme3.scene.Geometry geom = (com.jme3.scene.Geometry) sensorNode.getChild(0);
                try {
                    com.jme3.texture.Texture tex =
                            app.getAssetManager().loadTexture("Textures/checkpoint_on.png");
                    geom.getMaterial().setTexture("ColorMap", tex);
                } catch (Exception ignored) {}

                if (checkpointSfx != null) checkpointSfx.playInstance();
                sensorNode.setName("Checkpoint_Guardado");

            } else if ("Meta".equals(sensorNode.getName())) {
                onLevelComplete();
                sensorNode.setName("Meta_Tocada");
            }
            return;
        }

        // --- Enemigos ---
        Enemy enemyHit = findEnemy(dataA, dataB);
        if (enemyHit == null || enemyHit.isDying()) return;

        Vector3f playerPos = player.getPhysicsPosition();
        Vector3f enemyPos  = enemyHit.getWorldTranslation();
        if (playerPos == null) return;

        float playerBottom = playerPos.y - player.getHalfSize();
        float enemyTop     = enemyPos.y  + enemyHit.getHalfHeight();
        boolean stomped    = playerBottom >= (enemyTop - 0.35f);

        if (stomped) {
            if (squashSfx != null) squashSfx.playInstance();
            enemyHit.startDying();
            player.bounce();
            HUDState hud = app.getStateManager().getState(HUDState.class);
            if (hud != null) hud.onEnemyKilled();
        } else {
            if (hitSfx != null) hitSfx.playInstance();
            triggerPlayerDeath();
        }
    }

    private Enemy findEnemy(Object dataA, Object dataB) {
        for (Enemy e : enemies) {
            if (dataA == e || dataB == e) return e;
        }
        return null;
    }

    // =========================================================================
    // NIVEL COMPLETADO
    // =========================================================================

    private void onLevelComplete() {
        if (levelFinished) return;
        levelFinished = true;

        System.out.println("[GameState] ¡Nivel " + levelIndex + " completado!");

        if (bgm != null) bgm.stop();
        if (winSfx != null) winSfx.playInstance();

        HUDState hud = app.getStateManager().getState(HUDState.class);
        if (hud != null) hud.onLevelComplete();

        // Mostrar menú de victoria (ligeramente retrasado lo manejamos con
        // un AppState encolado; JME aplica los cambios de estado al final del frame)
        app.getStateManager().attach(new WinMenuState(bulletAppState, levelIndex));
    }

    // =========================================================================
    // MUERTE Y RESPAWN
    // =========================================================================

    private void triggerPlayerDeath() {
        if (playerDead) return;
        playerDead   = true;
        respawnTimer = RESPAWN_DELAY;

        if (bgm != null) bgm.stop();
        if (deathSfx != null) deathSfx.playInstance();

        player.die();

        HUDState hud = app.getStateManager().getState(HUDState.class);
        if (hud != null) {
            hud.onPlayerDied();
            // ¿Game Over?
            if (hud.getLives() <= 0) {
                levelFinished = true; // detener el bucle de update
                app.getStateManager().attach(
                        new GameOverOverlay(bulletAppState, levelIndex));
            }
        }
    }

    private void respawnPlayer() {
        playerDead     = false;
        ignoreKillZone = true;
        player.respawn(spawnPoint.clone());
        if (bgm != null && !levelFinished) bgm.play();
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Override
    public void cleanup() {
        super.cleanup();

        if (bgm != null) bgm.stop();

        if (bulletAppState.getPhysicsSpace() != null) {
            bulletAppState.getPhysicsSpace().removeCollisionListener(this);
            levelNode.depthFirstTraversal(new com.jme3.scene.SceneGraphVisitor() {
                @Override
                public void visit(com.jme3.scene.Spatial spatial) {
                    com.jme3.bullet.control.PhysicsControl ctrl =
                            spatial.getControl(com.jme3.bullet.control.PhysicsControl.class);
                    if (ctrl != null) bulletAppState.getPhysicsSpace().remove(ctrl);
                }
            });
        }

        if (backgroundGeom != null) {
            rootNode.detachChild(backgroundGeom);
            backgroundGeom = null;
        }

        safeDeleteMapping("MoveLeft");
        safeDeleteMapping("MoveRight");
        safeDeleteMapping("Jump");
        inputManager.removeListener(this);

        rootNode.detachChild(levelNode);
    }

    private void safeDeleteMapping(String name) {
        try { inputManager.deleteMapping(name); } catch (Exception ignored) {}
    }

    // =========================================================================
    // GETTER PÚBLICO (para WinMenuState / GameOverOverlay si lo necesitan)
    // =========================================================================
    public int getLevelIndex() { return levelIndex; }
}
