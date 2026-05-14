package com.redball.states;

import com.jme3.asset.AssetManager;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

public class HUDState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node guiNode;
    private AssetManager assetManager;
    private InputManager inputManager;
    private BitmapFont font;

    private Node hudElements;
    private BitmapText livesText;
    private BitmapText centerText;
    
    // Botones de control
    private Geometry pauseBtn;
    private Geometry restartBtn;
    private Geometry resumeBtn;

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
        hudElements = new Node("HUDElements");
        guiNode.attachChild(hudElements);

        buildTexts();
        buildButtons();
        registerInput();
    }

    private void buildTexts() {
        int screenW = app.getCamera().getWidth();
        int screenH = app.getCamera().getHeight();

        livesText = new BitmapText(font, false);
        livesText.setSize(font.getCharSet().getRenderedSize() * 1.5f);
        livesText.setColor(ColorRGBA.White);
        livesText.setText("❤ x " + lives);
        livesText.setLocalTranslation(20, screenH - 20, 0);
        hudElements.attachChild(livesText);

        centerText = new BitmapText(font, false);
        centerText.setSize(font.getCharSet().getRenderedSize() * 2f);
        centerText.setColor(ColorRGBA.Yellow);
        centerText.setText("");
        centerText.setLocalTranslation(screenW / 2f - 100f, screenH / 2f + 30f, 0);
        hudElements.attachChild(centerText);
    }

    private void buildButtons() {
        float sw = app.getCamera().getWidth();
        float sh = app.getCamera().getHeight();
        
        // Botón Pausa (Esquina superior derecha)
        pauseBtn = createButton("BtnPause", "Textures/pause.png", ColorRGBA.Cyan, 50f, 50f);
        pauseBtn.setLocalTranslation(sw - 70f, sh - 70f, 0);
        hudElements.attachChild(pauseBtn);

        // Botón Reiniciar (Al lado del botón de pausa)
        restartBtn = createButton("BtnRestart", "Textures/restart.png", ColorRGBA.Magenta, 50f, 50f);
        restartBtn.setLocalTranslation(sw - 140f, sh - 70f, 0);
        hudElements.attachChild(restartBtn);

        // Botón Reanudar (Oculto por defecto, en el centro de la pantalla)
        resumeBtn = createButton("BtnResume", "Textures/resume.png", ColorRGBA.Green, 200f, 80f);
        resumeBtn.setLocalTranslation((sw - 200f) / 2f, (sh - 80f) / 2f, 0);
        // No lo adjuntamos todavía
    }

    private Geometry createButton(String name, String texPath, ColorRGBA fallback, float w, float h) {
        Quad quad = new Quad(w, h);
        Geometry geom = new Geometry(name, quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture(texPath);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", fallback); // Fallback: Color sólido si no hay sprite
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
            hudElements.collideWith(ray, results);

            if (results.size() > 0) {
                Geometry target = results.getClosestCollision().getGeometry();
                
                if (target.getName().equals("BtnPause") && !isPaused) {
                    togglePause(true);
                } else if (target.getName().equals("BtnResume") && isPaused) {
                    togglePause(false);
                } else if (target.getName().equals("BtnRestart")) {
                    restartCurrentLevel();
                }
            }
        }
    }

    private void togglePause(boolean pause) {
        this.isPaused = pause;
        GameState gs = app.getStateManager().getState(GameState.class);
        BulletAppState physics = app.getStateManager().getState(BulletAppState.class);

        if (pause) {
            if (gs != null) gs.setEnabled(false); // Detiene la lógica de GameState
            if (physics != null) physics.setSpeed(0f); // Congela físicas
            showCenterMessage("PAUSADO", ColorRGBA.White, 999f);
            hudElements.attachChild(resumeBtn);
            hudElements.detachChild(pauseBtn);
        } else {
            if (gs != null) gs.setEnabled(true);
            if (physics != null) physics.setSpeed(1f);
            centerText.setText("");
            hudElements.detachChild(resumeBtn);
            hudElements.attachChild(pauseBtn);
        }
    }

    private void restartCurrentLevel() {
        if (isPaused) togglePause(false); // Descongelar antes de reiniciar
        
        AppStateManager sm = app.getStateManager();
        GameState currentGs = sm.getState(GameState.class);
        if (currentGs != null) {
            int currentLevel = currentGs.getLevelIndex();
            BulletAppState physics = sm.getState(BulletAppState.class);
            
            sm.detach(currentGs);
            sm.detach(this); // Limpiar este HUD
            
            sm.attach(new HUDState());
            sm.attach(new GameState(physics, currentLevel));
        }
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;
        if (centerTextTimer > 0f && !isPaused) {
            centerTextTimer -= tpf;
            if (centerTextTimer <= 0f) {
                centerText.setText("");
            }
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
        guiNode.detachChild(hudElements);
        try { inputManager.deleteMapping("HUDClick"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}