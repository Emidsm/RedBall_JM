package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

/**
 * GameOverOverlay — Cuando el jugador se queda sin vidas.
 *
 * R / ENTER → reinicia el nivel actual desde cero.
 */
public class GameOverOverlay extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node              guiNode;
    private AssetManager      assetManager;
    private InputManager      inputManager;
    private Camera            cam;

    private Geometry   overlayGeom;
    private Geometry   restartBtnGeom;
    private BitmapText gameOverText;
    private BitmapText promptText;

    private static final float BTN_W = 220f, BTN_H = 80f;

    private final com.jme3.bullet.BulletAppState bulletAppState;
    private final int levelIndex;

    public GameOverOverlay(com.jme3.bullet.BulletAppState bulletAppState, int levelIndex) {
        this.bulletAppState = bulletAppState;
        this.levelIndex     = levelIndex;
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app          = (SimpleApplication) application;
        this.guiNode      = app.getGuiNode();
        this.assetManager = app.getAssetManager();
        this.inputManager = app.getInputManager();
        this.cam          = app.getCamera();

        buildOverlay();
        buildGameOverText();
        buildRestartButton();
        buildPrompt();
        registerInput();
    }

    private void buildOverlay() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        Quad quad = new Quad(sw, sh);
        overlayGeom = new Geometry("GOOverlay", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0.7f));
        overlayGeom.setMaterial(mat);
        overlayGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        overlayGeom.setLocalTranslation(0f, 0f, 4f);
        guiNode.attachChild(overlayGeom);
    }

    private void buildGameOverText() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        gameOverText = new BitmapText(font, false);
        gameOverText.setSize(font.getCharSet().getRenderedSize() * 3.5f);
        gameOverText.setColor(ColorRGBA.Red);
        gameOverText.setText("¡GAME OVER!");
        float tw = gameOverText.getLineWidth();
        gameOverText.setLocalTranslation((sw - tw) / 2f, sh / 2f + 120f, 5f);
        guiNode.attachChild(gameOverText);
    }

    private void buildRestartButton() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        float bx = (sw - BTN_W) / 2f;
        float by = sh / 2f - BTN_H / 2f;
        Quad quad = new Quad(BTN_W, BTN_H);
        restartBtnGeom = new Geometry("GORestartBtn", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        try {
            Texture tex = assetManager.loadTexture("Textures/restartbutton.png");
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new ColorRGBA(0.8f, 0.2f, 0.1f, 1f));
        }
        restartBtnGeom.setMaterial(mat);
        restartBtnGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        restartBtnGeom.setLocalTranslation(bx, by, 5f);
        guiNode.attachChild(restartBtnGeom);
    }

    private void buildPrompt() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        promptText = new BitmapText(font, false);
        promptText.setSize(font.getCharSet().getRenderedSize() * 1.3f);
        promptText.setColor(ColorRGBA.White);
        promptText.setText("R / ENTER = Reiniciar");
        float tw = promptText.getLineWidth();
        promptText.setLocalTranslation((sw - tw) / 2f, sh / 2f - BTN_H - 20f, 5f);
        guiNode.attachChild(promptText);
    }

    private void registerInput() {
        inputManager.addMapping("GORestart", new KeyTrigger(KeyInput.KEY_R),
                                             new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addListener(this, "GORestart");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("GORestart".equals(name) && isPressed) restartLevel();
    }

    private void restartLevel() {
        AppStateManager sm = app.getStateManager();
        sm.detach(this);

        GameState old = sm.getState(GameState.class);
        if (old != null) sm.detach(old);

        HUDState hud = sm.getState(HUDState.class);
        if (hud != null) sm.detach(hud);
        sm.attach(new HUDState());

        sm.attach(new GameState(bulletAppState, levelIndex));
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (overlayGeom    != null) guiNode.detachChild(overlayGeom);
        if (restartBtnGeom != null) guiNode.detachChild(restartBtnGeom);
        if (gameOverText   != null) guiNode.detachChild(gameOverText);
        if (promptText     != null) guiNode.detachChild(promptText);
        try { inputManager.deleteMapping("GORestart"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}