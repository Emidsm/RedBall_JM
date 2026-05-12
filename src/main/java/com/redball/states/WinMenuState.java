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
 * WinMenuState — Se muestra al completar un nivel.
 *
 * ENTER → siguiente nivel
 * R     → reiniciar nivel actual
 */
public class WinMenuState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node              guiNode;
    private AssetManager      assetManager;
    private InputManager      inputManager;
    private Camera            cam;

    private Geometry   overlayGeom;
    private Geometry   nextBtnGeom;
    private Geometry   restartBtnGeom;
    private BitmapText promptText;

    private static final float BTN_W = 220f, BTN_H = 80f, GAP = 20f;

    private final com.jme3.bullet.BulletAppState bulletAppState;
    private final int completedLevel;

    public WinMenuState(com.jme3.bullet.BulletAppState bulletAppState, int completedLevel) {
        this.bulletAppState = bulletAppState;
        this.completedLevel = completedLevel;
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
        buildButtons();
        buildPrompt();
        registerInput();
    }

    private void buildOverlay() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        Quad quad = new Quad(sw, sh);
        overlayGeom = new Geometry("WinOverlay", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0.6f));
        overlayGeom.setMaterial(mat);
        overlayGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        overlayGeom.setLocalTranslation(0f, 0f, 2f);
        guiNode.attachChild(overlayGeom);
    }

    private void buildButtons() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        float cx = (sw - BTN_W) / 2f;
        float cy = sh / 2f;

        // Botón siguiente nivel (arriba)
        nextBtnGeom = makeButton("Textures/playbutton.png",
                new ColorRGBA(0.1f, 0.8f, 0.1f, 1f),
                cx, cy + GAP / 2f + BTN_H / 2f, 3f);

        // Botón reiniciar (abajo)
        restartBtnGeom = makeButton("Textures/restartbutton.png",
                new ColorRGBA(0.8f, 0.3f, 0.1f, 1f),
                cx, cy - GAP / 2f - BTN_H / 2f - BTN_H, 3f);
    }

    private Geometry makeButton(String texPath, ColorRGBA fallback, float x, float y, float z) {
        Quad quad = new Quad(BTN_W, BTN_H);
        Geometry geom = new Geometry("Btn", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        try {
            Texture tex = assetManager.loadTexture(texPath);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", fallback);
        }
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        geom.setLocalTranslation(x, y, z);
        guiNode.attachChild(geom);
        return geom;
    }

    private void buildPrompt() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        promptText = new BitmapText(font, false);
        promptText.setSize(font.getCharSet().getRenderedSize() * 1.3f);
        promptText.setColor(ColorRGBA.White);
        promptText.setText("ENTER = Siguiente nivel    R = Reiniciar");
        float tw = promptText.getLineWidth();
        promptText.setLocalTranslation((sw - tw) / 2f, sh / 2f - BTN_H * 2f - GAP * 2f, 4f);
        guiNode.attachChild(promptText);
    }

    private void registerInput() {
        inputManager.addMapping("WinNext",    new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("WinRestart", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addListener(this, "WinNext", "WinRestart");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) return;
        if ("WinNext".equals(name))    switchGame(completedLevel + 1);
        if ("WinRestart".equals(name)) switchGame(completedLevel);
    }

    private void switchGame(int levelIndex) {
        AppStateManager sm = app.getStateManager();

        // Quitar GameState anterior si aún vive
        GameState old = sm.getState(GameState.class);
        if (old != null) sm.detach(old);

        // Resetear HUD
        HUDState hud = sm.getState(HUDState.class);
        if (hud != null) sm.detach(hud);
        sm.attach(new HUDState());

        sm.attach(new GameState(bulletAppState, levelIndex));
        sm.detach(this);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        safeDetach(overlayGeom);
        safeDetach(nextBtnGeom);
        safeDetach(restartBtnGeom);
        if (promptText != null) guiNode.detachChild(promptText);
        try { inputManager.deleteMapping("WinNext");    } catch (Exception ignored) {}
        try { inputManager.deleteMapping("WinRestart"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }

    private void safeDetach(Geometry g) { if (g != null) guiNode.detachChild(g); }
}