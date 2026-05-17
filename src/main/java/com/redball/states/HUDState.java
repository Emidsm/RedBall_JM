package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class HUDState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node guiNode;
    private AssetManager assetManager;
    private InputManager inputManager;
    private BitmapFont font;

    private Node hudNode;
    private Node pauseMenuSubNode; // Agrupa los sub-botones desplegables de pausa

    private BitmapText livesText;
    private BitmapText centerText;
    private Geometry pauseBtn;

    private int lives = 3;
    private float centerTextTimer = 0f;
    private boolean isPaused = false;

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();
        this.assetManager = app.getAssetManager();
        this.inputManager = app.getInputManager();

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        
        hudNode = new Node("HUDNode");
        pauseMenuSubNode = new Node("PauseMenuSubNode");
        
        guiNode.attachChild(hudNode);

        buildHUDGameplay();
        registerInput();
    }

    private void buildHUDGameplay() {
        float sw = app.getCamera().getWidth();
        float sh = app.getCamera().getHeight();

        // Vidas
        livesText = new BitmapText(font, false);
        livesText.setSize(font.getCharSet().getRenderedSize() * 1.5f);
        livesText.setColor(ColorRGBA.White);
        livesText.setText("❤ x " + lives);
        livesText.setLocalTranslation(20, sh - 20, 1f);
        hudNode.attachChild(livesText);

        // Texto informativo central
        centerText = new BitmapText(font, false);
        centerText.setSize(font.getCharSet().getRenderedSize() * 2f);
        centerText.setColor(ColorRGBA.Yellow);
        centerText.setText("");
        centerText.setLocalTranslation(sw / 2f - 120f, sh / 2f + 50f, 1f);
        hudNode.attachChild(centerText);

        // ÚNICO botón inicial en el juego: Botón Pausa
        pauseBtn = createHUDButton("BtnPause", "Textures/button_pause.png", ColorRGBA.Cyan, 50f, 50f);
        pauseBtn.setLocalTranslation(sw - 70f, sh - 70f, 1f);
        hudNode.attachChild(pauseBtn);
    }

    private void showPauseMenuElements(boolean show) {
        if (show) {
            float sw = app.getCamera().getWidth();
            float sh = app.getCamera().getHeight();
            
            pauseMenuSubNode.detachAllChildren();

            // Fondo translúcido de pausa
            Geometry overlay = new Geometry("Overlay", new Quad(sw, sh));
            Material matOver = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            matOver.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            matOver.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0.6f));
            overlay.setMaterial(matOver);
            pauseMenuSubNode.attachChild(overlay);

            // Despliegue de los 3 botones en el centro de la pantalla
            float cx = sw / 2f;
            float cy = sh / 2f;

            // 1. Reanudar (Play)
            Geometry btnResume = createHUDButton("BtnResume", "Textures/button_play.png", ColorRGBA.Green, 70f, 70f);
            btnResume.setLocalTranslation(cx - 110f, cy - 35f, 1f);
            pauseMenuSubNode.attachChild(btnResume);

            // 2. Reiniciar (Restart)
            Geometry btnRestart = createHUDButton("BtnRestart", "Textures/button_restart.png", ColorRGBA.Magenta, 70f, 70f);
            btnRestart.setLocalTranslation(cx - 35f, cy - 35f, 1f);
            pauseMenuSubNode.attachChild(btnRestart);

            // 3. Volver al selector (Back)
            Geometry btnBack = createHUDButton("BtnBack", "Textures/button_back.png", ColorRGBA.Gray, 70f, 70f);
            btnBack.setLocalTranslation(cx + 40f, cy - 35f, 1f);
            pauseMenuSubNode.attachChild(btnBack);

            hudNode.attachChild(pauseMenuSubNode);
            hudNode.detachChild(pauseBtn); // Oculta el botón de esquina
        } else {
            hudNode.detachChild(pauseMenuSubNode);
            hudNode.attachChild(pauseBtn); // Regresa el botón de esquina
        }
    }

    private Geometry createHUDButton(String name, String texPath, ColorRGBA fallback, float w, float h) {
        Geometry geom = new Geometry(name, new Quad(w, h));
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        try {
            mat.setTexture("ColorMap", assetManager.loadTexture(texPath));
        } catch (Exception e) {
            mat.setColor("Color", fallback);
        }
        geom.setMaterial(mat);
        return geom;
    }

    private void registerInput() {
        inputManager.addMapping("HUDClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, "HUDClick");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("HUDClick".equals(name) && isPressed) {
            Vector2f click2d = inputManager.getCursorPosition();
            Vector3f click3d = new Vector3f(click2d.x, click2d.y, 10f);
            Ray ray = new Ray(click3d, new Vector3f(0, 0, -1f));
            CollisionResults results = new CollisionResults();
            hudNode.collideWith(ray, results);

            if (results.size() > 0) {
                Geometry target = results.getClosestCollision().getGeometry();
                String targetName = target.getName();

                if ("BtnPause".equals(targetName) && !isPaused) {
                    togglePause(true);
                } else if ("BtnResume".equals(targetName) && isPaused) {
                    togglePause(false);
                } else if ("BtnRestart".equals(targetName)) {
                    executeRestart();
                } else if ("BtnBack".equals(targetName)) {
                    executeBackToSelector();
                }
            }
        }
    }

    private void togglePause(boolean pause) {
        this.isPaused = pause;
        GameState gs = app.getStateManager().getState(GameState.class);
        BulletAppState physics = app.getStateManager().getState(BulletAppState.class);

        if (pause) {
            if (gs != null) gs.setEnabled(false);
            if (physics != null) physics.setSpeed(0f);
            showPauseMenuElements(true);
        } else {
            if (gs != null) gs.setEnabled(true);
            if (physics != null) physics.setSpeed(1f);
            centerText.setText("");
            showPauseMenuElements(false);
        }
    }

    private void executeRestart() {
        togglePause(false); // Descongelar
        AppStateManager sm = app.getStateManager();
        GameState currentGs = sm.getState(GameState.class);
        if (currentGs != null) {
            int level = currentGs.getLevelIndex();
            BulletAppState physics = sm.getState(BulletAppState.class);
            sm.detach(currentGs);
            sm.detach(this);
            sm.attach(new HUDState());
            sm.attach(new GameState(physics, level));
        }
    }

    private void executeBackToSelector() {
        togglePause(false); // Asegurar que todo se descongele antes del salto
        AppStateManager sm = app.getStateManager();
        GameState currentGs = sm.getState(GameState.class);
        if (currentGs != null) sm.detach(currentGs);
        
        BulletAppState physics = sm.getState(BulletAppState.class);
        sm.detach(this);
        sm.attach(new LevelSelectState(physics)); // Regresa al selector lógico
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;
        if (centerTextTimer > 0f && !isPaused) {
            centerTextTimer -= tpf;
            if (centerTextTimer <= 0f) centerText.setText("");
        }
    }

    public void onPlayerDied() {
        lives = Math.max(0, lives - 1);
        livesText.setText("❤ x " + lives);
        if (lives <= 0) showCenterMessage("¡Game Over!", ColorRGBA.Red, 999f);
        else showCenterMessage("¡Ouch!", ColorRGBA.Orange, 1.5f);
    }

    public void onEnemyKilled() { showCenterMessage("¡Aplastado!", ColorRGBA.Yellow, 1f); }
    public void onLevelComplete() { showCenterMessage("¡Nivel Completado!", ColorRGBA.Green, 999f); }

    public void showCenterMessage(String msg, ColorRGBA color, float duration) {
        centerText.setText(msg);
        centerText.setColor(color);
        centerTextTimer = duration;
    }

    public int getLives() { return this.lives; }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(hudNode);
        try { inputManager.deleteMapping("HUDClick"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}