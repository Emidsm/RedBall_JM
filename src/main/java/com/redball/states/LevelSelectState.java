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
import com.jme3.texture.Texture;

public class LevelSelectState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node guiNode;
    private AssetManager assetManager;
    private InputManager inputManager;
    private BitmapFont font;
    private BulletAppState bulletAppState;

    private Node selectNode;
    private Geometry btnBack;

    public LevelSelectState(BulletAppState bulletAppState) {
        this.bulletAppState = bulletAppState;
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();
        this.assetManager = app.getAssetManager();
        this.inputManager = app.getInputManager();
        
        // Carga una fuente gruesa (puedes generar un .fnt de Poppins o usar la default limpia)
        this.font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        selectNode = new Node("SelectNode");
        guiNode.attachChild(selectNode);

        buildBackground();
        buildLevelButtons();
        registerInput();
    }

    private void buildBackground() {
        float sw = app.getCamera().getWidth(), sh = app.getCamera().getHeight();
        Geometry bg = new Geometry("SelectBG", new Quad(sw, sh));
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture("Textures/background_levels.png");
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new ColorRGBA(0.1f, 0.1f, 0.2f, 1f));
        }
        bg.setMaterial(mat);
        selectNode.attachChild(bg);
    }

    private void buildLevelButtons() {
        float sw = app.getCamera().getWidth(), sh = app.getCamera().getHeight();

        // Botón de Volver (Esquina superior izquierda)
        btnBack = new Geometry("BtnBack", new Quad(50f, 50f));
        Material matBack = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        matBack.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        try {
            matBack.setTexture("ColorMap", assetManager.loadTexture("Textures/button_back.png"));
        } catch (Exception e) {
            matBack.setColor("Color", ColorRGBA.Gray);
        }
        btnBack.setMaterial(matBack);
        btnBack.setLocalTranslation(30f, sh - 80f, 1f);
        selectNode.attachChild(btnBack);

        // Cuadrícula de Niveles usando el sprite vacío limpio
        float startX = sw / 2f - 180f;
        float startY = sh / 2f - 40f;
        float btnSize = 80f;
        float gap = 40f;

        for (int i = 1; i <= GameState.TOTAL_LEVELS; i++) {
            Geometry btn = new Geometry("LevelBtn_" + i, new Quad(btnSize, btnSize));
            Material matBtn = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            matBtn.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            try {
                matBtn.setTexture("ColorMap", assetManager.loadTexture("Textures/button_empty.png"));
            } catch (Exception e) {
                matBtn.setColor("Color", ColorRGBA.Blue);
            }
            btn.setMaterial(matBtn);

            float posX = startX + ((i - 1) * (btnSize + gap));
            btn.setLocalTranslation(posX, startY, 1f);
            btn.setUserData("levelIndex", i);
            selectNode.attachChild(btn);

            // Superponer el número del nivel sobre el sprite limpio
            BitmapText numText = new BitmapText(font, false);
            numText.setSize(font.getCharSet().getRenderedSize() * 1.8f);
            numText.setColor(ColorRGBA.White);
            numText.setText(String.valueOf(i));
            // Centrado aproximado del texto
            numText.setLocalTranslation(posX + (btnSize / 2f) - 12f, startY + (btnSize / 2f) + 18f, 2f);
            selectNode.attachChild(numText);
        }
    }

    private void registerInput() {
        inputManager.addMapping("SelectClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, "SelectClick");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("SelectClick".equals(name) && isPressed) {
            Vector2f click2d = inputManager.getCursorPosition();
            Vector3f click3d = new Vector3f(click2d.x, click2d.y, 10f);
            Ray ray = new Ray(click3d, new Vector3f(0, 0, -1f));
            CollisionResults results = new CollisionResults();
            selectNode.collideWith(ray, results);

            if (results.size() > 0) {
                Geometry target = results.getClosestCollision().getGeometry();
                if ("BtnBack".equals(target.getName())) {
                    app.getStateManager().attach(new MenuState(bulletAppState));
                    app.getStateManager().detach(this);
                } else if (target.getName().startsWith("LevelBtn_")) {
                    int level = target.getUserData("levelIndex");
                    // Redirige a la nueva Pantalla de Carga
                    app.getStateManager().attach(new LoadingState(bulletAppState, level));
                    app.getStateManager().detach(this);
                }
            }
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(selectNode);
        try { inputManager.deleteMapping("SelectClick"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}