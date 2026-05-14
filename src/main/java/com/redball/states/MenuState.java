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
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;


/**
 * MenuState — Pantalla principal.
 * Muestra moon.png de fondo + instrucción de teclado.
 * ENTER → empieza el nivel 1.
 */
public class MenuState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node              guiNode;
    private AssetManager      assetManager;
    private InputManager      inputManager;
    private Camera            cam;

    private Geometry   bgGeom;
    private Geometry   playBtnGeom;
    private BitmapText promptText;

    private final com.jme3.bullet.BulletAppState bulletAppState;

    public MenuState(com.jme3.bullet.BulletAppState bulletAppState) {
        this.bulletAppState = bulletAppState;
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app          = (SimpleApplication) application;
        this.guiNode      = app.getGuiNode();
        this.assetManager = app.getAssetManager();
        this.inputManager = app.getInputManager();
        this.cam          = app.getCamera();

        buildBackground();
        buildPlayButton();
        buildPrompt();
        registerInput();
    }

    private void buildBackground() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        Quad quad = new Quad(sw, sh);
        bgGeom = new Geometry("MenuBG", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture("Textures/moon.png");
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new ColorRGBA(0.05f, 0.02f, 0.12f, 1f));
        }
        bgGeom.setMaterial(mat);
        bgGeom.setLocalTranslation(0f, 0f, 0f);
        guiNode.attachChild(bgGeom);
    }

    private void buildPlayButton() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        float btnW = 200f, btnH = 80f;
        Quad quad = new Quad(btnW, btnH);
        playBtnGeom = new Geometry("PlayBtn", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        try {
            Texture tex = assetManager.loadTexture("Textures/playbutton.png");
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new ColorRGBA(0.1f, 0.8f, 0.1f, 1f));
        }
        playBtnGeom.setMaterial(mat);
        playBtnGeom.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        playBtnGeom.setLocalTranslation((sw - btnW) / 2f, (sh - btnH) / 2f - 40f, 1f);
        guiNode.attachChild(playBtnGeom);
    }

    private void buildPrompt() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        promptText = new BitmapText(font, false);
        promptText.setSize(font.getCharSet().getRenderedSize() * 1.4f);
        promptText.setColor(ColorRGBA.White);
        promptText.setText("Presiona ENTER para jugar");
        float tw = promptText.getLineWidth();
        promptText.setLocalTranslation((sw - tw) / 2f, sh / 2f - 140f, 2f);
        guiNode.attachChild(promptText);
    }

    private void registerInput() {
        inputManager.addMapping("MenuPlay", new KeyTrigger(KeyInput.KEY_RETURN),
                                            new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addListener(this, "MenuPlay");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("MenuPlay".equals(name) && isPressed) startGame();
    }

    private void startGame() {
        AppStateManager sm = app.getStateManager();
        // Redirigir al selector de niveles en lugar del juego directo
        sm.attach(new LevelSelectState(bulletAppState));
        sm.detach(this);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(bgGeom);
        guiNode.detachChild(playBtnGeom);
        guiNode.detachChild(promptText);
        try { inputManager.deleteMapping("MenuPlay"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}