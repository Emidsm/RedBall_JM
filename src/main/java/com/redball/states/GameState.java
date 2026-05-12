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
import com.redball.level.Level1Builder;
import com.redball.level.TiledLevelBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameState extends AbstractAppState implements PhysicsCollisionListener, ActionListener {

    private SimpleApplication app;
    private AssetManager      assetManager;
    private Node              rootNode;
    private Camera            cam;
    private InputManager      inputManager;
    private BulletAppState    bulletAppState;
    // Variables para el audio
    private com.jme3.audio.AudioNode bgm;
    private com.jme3.audio.AudioNode checkpointSfx;
    private com.jme3.audio.AudioNode winSfx;
    // --- NUEVOS NODOS ---
    private com.jme3.audio.AudioNode squashSfx;
    private com.jme3.audio.AudioNode hitSfx;
    private com.jme3.audio.AudioNode deathSfx;
    

    private Node levelNode;

    private Player       player;
    private List<Enemy>  enemies = new ArrayList<>();

    // --- Input flags ---
    private boolean moveLeft  = false;
    private boolean moveRight = false;

    // --- Cámara follow ---
    private static final float CAM_SMOOTHING = 5f;
    private static final float CAM_OFFSET_Y  = 1.5f;

    // --- Fondo (vive en rootNode para seguir la cámara automáticamente) ---
    // Se actualiza en updateBackground() para centrarse en la cámara.
    private com.jme3.scene.Geometry backgroundGeom;

    // --- Respawn ---
    private static final float RESPAWN_DELAY = 1.2f;
    private float   respawnTimer    = 0f;
    private boolean playerDead      = false;
    private boolean ignoreKillZone  = false; // 1-frame grace tras respawn (BUG 3)
    private Vector3f spawnPoint = new Vector3f(-8f, 1f, 0f);

    // --- Game Over ---
    private boolean gameOver = false;

    public GameState(BulletAppState bulletAppState) {
        this.bulletAppState = bulletAppState;
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

    
        // En initialize(), después de adjuntar levelNode, agrega esta línea:
        levelNode = new Node("LevelNode");
        rootNode.attachChild(levelNode);

        bulletAppState.getPhysicsSpace().addCollisionListener(this);
        enemies.clear();

        TiledLevelBuilder builder = new TiledLevelBuilder(assetManager, bulletAppState.getPhysicsSpace(), levelNode);
        this.spawnPoint = builder.build("Levels/nivel2_musgo.tmj", enemies);

        buildBackground(); // ← ESTA LÍNEA FALTA — sin ella nunca hay fondo

        // 2. Creamos al jugador
        player = new Player(assetManager, bulletAppState.getPhysicsSpace());
        player.respawn(spawnPoint.clone());
        levelNode.attachChild(player);
        
        registerInput();
        // --- INICIALIZAR AUDIO ---
        // Debes meter estos archivos en la ruta: src/main/resources/Sounds/
        // --- INICIALIZAR AUDIO ---
        try {
            bgm = new com.jme3.audio.AudioNode(assetManager, "Sounds/bgm.ogg", com.jme3.audio.AudioData.DataType.Stream);
            bgm.setPositional(false); 
            bgm.setLooping(true); 
            bgm.setVolume(0.5f);
            rootNode.attachChild(bgm);
            bgm.play();

            checkpointSfx = new com.jme3.audio.AudioNode(assetManager, "Sounds/checkpoint.ogg", com.jme3.audio.AudioData.DataType.Buffer);
            checkpointSfx.setPositional(false);

            winSfx = new com.jme3.audio.AudioNode(assetManager, "Sounds/win.ogg", com.jme3.audio.AudioData.DataType.Buffer);
            winSfx.setPositional(false);
            
            // --- CARGAMOS LOS NUEVOS SFX ---
            squashSfx = new com.jme3.audio.AudioNode(assetManager, "Sounds/squash.ogg", com.jme3.audio.AudioData.DataType.Buffer);
            squashSfx.setPositional(false);

            hitSfx = new com.jme3.audio.AudioNode(assetManager, "Sounds/hit.ogg", com.jme3.audio.AudioData.DataType.Buffer);
            hitSfx.setPositional(false);

            deathSfx = new com.jme3.audio.AudioNode(assetManager, "Sounds/death.ogg", com.jme3.audio.AudioData.DataType.Buffer);
            deathSfx.setPositional(false);

        } catch (Exception e) {
            System.err.println("¡Error cargando el audio!");
            e.printStackTrace();
        }
    }

    // =========================================================================
    // FONDO (BUG 5)
    // =========================================================================

    private void buildBackground() {
        // Ancho generoso: cubre el frustum completo sin importar aspect ratio
        float bgW = 100f, bgH = 40f;
        com.jme3.scene.shape.Quad q = Player.buildCenteredQuad(bgW, bgH) instanceof com.jme3.scene.shape.Quad
                ? (com.jme3.scene.shape.Quad) Player.buildCenteredQuad(bgW, bgH)
                : null;

        // buildCenteredQuad retorna Quad, cast seguro
        com.jme3.scene.shape.Quad quad = (com.jme3.scene.shape.Quad) Player.buildCenteredQuad(bgW, bgH);
        backgroundGeom = new com.jme3.scene.Geometry("Background", quad);

        com.jme3.material.Material mat = new com.jme3.material.Material(
                assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            com.jme3.texture.Texture tex = assetManager.loadTexture("Textures/background.png");
            tex.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new com.jme3.math.ColorRGBA(0.15f, 0.12f, 0.10f, 1f)); // apocalíptico marrón oscuro
        }
        backgroundGeom.setMaterial(mat);
        // Z profundo negativo: queda detrás de todo en espacio mundial
        backgroundGeom.setLocalTranslation(0f, 0f, -9f);
        rootNode.attachChild(backgroundGeom);
    }

    /**
     * Centra el fondo en la posición actual de la cámara (X, Y).
     * Al estar en rootNode (no en levelNode), solo necesitamos igualar la posición de la cámara.
     */
    private void updateBackground() {
        if (backgroundGeom == null) return;
        Vector3f camPos = cam.getLocation();
        backgroundGeom.setLocalTranslation(camPos.x, camPos.y, -9f);
    }

    // =========================================================================
    // ENEMIGOS
    // =========================================================================

    private void spawnEnemies() {
    }

    private void addEnemy(float startX, float startY, float patrolMinX, float patrolMaxX) {
        Enemy e = new Enemy(assetManager, bulletAppState.getPhysicsSpace(), patrolMinX, patrolMaxX);
        e.setLocalTranslation(startX, startY, 0f);
        levelNode.attachChild(e);
        enemies.add(e);
    }

    // =========================================================================
    // INPUT
    // =========================================================================

    private void registerInput() {
        inputManager.addMapping("MoveLeft",  new KeyTrigger(KeyInput.KEY_LEFT),  new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("MoveRight", new KeyTrigger(KeyInput.KEY_RIGHT), new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Jump",      new KeyTrigger(KeyInput.KEY_SPACE), new KeyTrigger(KeyInput.KEY_UP), new KeyTrigger(KeyInput.KEY_W));
        // BUG 4: Retry para game-over
        inputManager.addMapping("Retry",     new KeyTrigger(KeyInput.KEY_R),     new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addListener(this, "MoveLeft", "MoveRight", "Jump", "Retry");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "MoveLeft":  moveLeft  = isPressed; break;
            case "MoveRight": moveRight = isPressed; break;
            case "Jump":
                if (isPressed && !playerDead && !gameOver) player.tryJump();
                break;
            case "Retry":
                // BUG 4: solo actuar en game-over y al soltar la tecla (evitar doble disparo)
                if (isPressed && gameOver) restartLevel();
                break;
        }
    }

    // =========================================================================
    // UPDATE PRINCIPAL
    // =========================================================================

    @Override
    public void update(float tpf) {
        if (!isEnabled() || gameOver) return;

        // --- Respawn timer ---
        if (playerDead) {
            respawnTimer -= tpf;
            if (respawnTimer <= 0f) respawnPlayer();
            updateCamera(tpf);      // la cámara sigue moviéndose aunque el jugador muera
            updateBackground();
            return;
        }

        // --- Input ---
        if (moveLeft)  player.rollLeft(tpf);
        if (moveRight) player.rollRight(tpf);

        // --- Enemigos ---
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

        // --- Cámara ---
        updateCamera(tpf);
        updateBackground();

        // --- Animación de explosión post-muerte ---
        if (player.isExploding()) {
            player.updateExplosion(tpf);
        }

        // --- Kill zone (BUG 3): usar posición física, no Node.getWorldTranslation() ---
        if (!ignoreKillZone) {
            Vector3f physPos = player.getPhysicsPosition();
            if (physPos != null && physPos.y < -20f) {
                triggerPlayerDeath();
            }
        }
        ignoreKillZone = false; // resetear flag después de 1 frame
    }

    // =========================================================================
    // CÁMARA FOLLOW
    // =========================================================================

    private void updateCamera(float tpf) {
        Vector3f playerPos = playerDead ? cam.getLocation() : player.getPhysicsPosition();
        if (playerPos == null) return;

        float targetX = playerPos.x + 0f;
        float targetY = playerPos.y + CAM_OFFSET_Y;

        // Evita que la cámara vea la nada a la izquierda (inicio del nivel)
        targetX = Math.max(targetX, 0f); 
        
        // BORRAMOS el Math.min(targetX, 25f) que tenías aquí. ¡Libertad total!

        Vector3f current = cam.getLocation();
        float smoothX = com.jme3.math.FastMath.interpolateLinear(CAM_SMOOTHING * tpf, current.x, targetX);
        float smoothY = com.jme3.math.FastMath.interpolateLinear(CAM_SMOOTHING * tpf, current.y, targetY);

        cam.setLocation(new Vector3f(smoothX, smoothY, 10f));
    }

    // =========================================================================
    // COLISIONES (BUG 1) — API Minie correcta
    // =========================================================================

    @Override
    public void collision(PhysicsCollisionEvent event) {
        PhysicsCollisionObject pcoA = event.getObjectA();
        PhysicsCollisionObject pcoB = event.getObjectB();

        Object dataA = (pcoA != null) ? pcoA.getApplicationData() : null;
        Object dataB = (pcoB != null) ? pcoB.getApplicationData() : null;

        boolean playerInvolved = (dataA instanceof Player) || (dataB instanceof Player);
        
        if (!playerInvolved || playerDead) return;

        // --- SENSORES (Checkpoint y Meta) ---
        Node sensorNode = null;
        if (dataA instanceof Node && ("Checkpoint".equals(((Node)dataA).getName()) || "Meta".equals(((Node)dataA).getName()))) {
            sensorNode = (Node) dataA;
        } else if (dataB instanceof Node && ("Checkpoint".equals(((Node)dataB).getName()) || "Meta".equals(((Node)dataB).getName()))) {
            sensorNode = (Node) dataB;
        }

        if (sensorNode != null) {
            if ("Checkpoint".equals(sensorNode.getName())) {
                // FIX 4: Regresar a la capa Z = 0
                Vector3f pos = sensorNode.getLocalTranslation().clone();
                pos.z = 0f; 
                this.spawnPoint.set(pos);
                
                // FIX 2: Cambiar sprite del checkpoint
                com.jme3.scene.Geometry geom = (com.jme3.scene.Geometry) sensorNode.getChild(0);
                try {
                    com.jme3.texture.Texture tex = app.getAssetManager().loadTexture("Textures/checkpoint_on.png");
                    geom.getMaterial().setTexture("ColorMap", tex);
                } catch (Exception e) {}

                // FIX 2: Tocar sonido de checkpoint (solo una vez)
                if (checkpointSfx != null) checkpointSfx.playInstance();

                // Cambiar el nombre para que ya no vuelva a entrar aquí y no repita el sonido
                sensorNode.setName("Checkpoint_Guardado");

            } else if ("Meta".equals(sensorNode.getName())) {
                System.out.println("¡NIVEL COMPLETADO!");
                gameOver = true;
                
                // --- DETENEMOS LA MÚSICA AL GANAR ---
                if (bgm != null) bgm.stop();
                
                // FIX 2: Tocar sonido de meta
                if (winSfx != null) winSfx.playInstance();
                
                sensorNode.setName("Meta_Tocada");
            }
            return; 
        }

        // --- 2. LUEGO REVISAMOS ENEMIGOS ---
        Enemy enemyHit = findEnemy(dataA, dataB);
        if (enemyHit == null || enemyHit.isDying()) return;

        Vector3f playerPos = player.getPhysicsPosition();
        Vector3f enemyPos  = enemyHit.getWorldTranslation();
        if (playerPos == null) return;

        float playerBottom = playerPos.y - player.getHalfSize();
        float enemyTop     = enemyPos.y  + enemyHit.getHalfHeight();

        boolean stomped = playerBottom >= (enemyTop - 0.35f);

        if (stomped) {
            // --- SONIDO DE APLASTAR ---
            if (squashSfx != null) squashSfx.playInstance();
            
            enemyHit.startDying();
            player.bounce();
            app.getStateManager().getState(HUDState.class).onEnemyKilled();
        } else {
            // --- SONIDO DE RECIBIR GOLPE ---
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
    // MUERTE Y RESPAWN
    // =========================================================================

    private void triggerPlayerDeath() {
        if (playerDead) return;
        playerDead   = true;
        respawnTimer = RESPAWN_DELAY;
        
        // --- CORTAR BGM Y REPRODUCIR SONIDO DE MUERTE ---
        if (bgm != null) bgm.stop();
        if (deathSfx != null) deathSfx.playInstance();

        player.die();

        HUDState hud = app.getStateManager().getState(HUDState.class);
        hud.onPlayerDied();

        // Comprobar si es game over
        if (hud.getLives() <= 0) {
            gameOver = true;
        }
    }

    private void respawnPlayer() {
        playerDead     = false;
        ignoreKillZone = true;  // BUG 3: saltear kill-zone check este frame
        player.respawn(spawnPoint.clone());
        
        // --- REANUDAR MÚSICA SI NO ES GAME OVER ---
        if (bgm != null && !gameOver) {
            bgm.play();
        }
    }

    // =========================================================================
    // REINICIO DE NIVEL (BUG 4)
    // =========================================================================

    private void restartLevel() {
        // Limpiar todo y hacer re-initialize vía el StateManager de JME.
        // La forma más limpia: detach + re-attach este mismo estado.
        AppStateManager sm = app.getStateManager();

        // Reiniciar HUD
        HUDState hud = sm.getState(HUDState.class);
        if (hud != null) hud.reset();

        // Limpiar y re-inicializar GameState
        sm.detach(this);
        GameState fresh = new GameState(bulletAppState);
        sm.attach(fresh);
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Override
    public void cleanup() {
        super.cleanup();
        
        // Detener la música
        if (bgm != null) bgm.stop();

        if (bulletAppState.getPhysicsSpace() != null) {
            bulletAppState.getPhysicsSpace().removeCollisionListener(this);
            
            // --- FIX 1: Recorrer todo el nivel y borrar las físicas viejas ---
            levelNode.depthFirstTraversal(new com.jme3.scene.SceneGraphVisitor() { // <- ¡Aquí quitamos la palabra Adapter!
                @Override
                public void visit(com.jme3.scene.Spatial spatial) {
                    com.jme3.bullet.control.PhysicsControl control = spatial.getControl(com.jme3.bullet.control.PhysicsControl.class);
                    if (control != null) {
                        bulletAppState.getPhysicsSpace().remove(control);
                    }
                }
            });
        }

        // Remover fondo del rootNode
        if (backgroundGeom != null) {
            rootNode.detachChild(backgroundGeom);
            backgroundGeom = null;
        }

        safeDeleteMapping("MoveLeft");
        safeDeleteMapping("MoveRight");
        safeDeleteMapping("Jump");
        safeDeleteMapping("Retry");
        inputManager.removeListener(this);

        rootNode.detachChild(levelNode);
    }

    private void safeDeleteMapping(String name) {
        try { inputManager.deleteMapping(name); } catch (Exception ignored) {}
    }
}