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
import com.jme3.input.KeyInput;
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

public class LevelSelectState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node guiNode;
    private AssetManager assetManager;
    private InputManager inputManager;
    private BitmapFont font;
    private BulletAppState bulletAppState;

    private Node buttonsNode; // Agrupa los botones para facilitar la limpieza

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
        this.font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        buttonsNode = new Node("LevelButtonsNode");
        guiNode.attachChild(buttonsNode);

        buildLevelButtons();
        registerInput();
    }

    private void buildLevelButtons() {
        float sw = app.getCamera().getWidth();
        float sh = app.getCamera().getHeight();
        
        BitmapText title = new BitmapText(font, false);
        title.setSize(font.getCharSet().getRenderedSize() * 2f);
        title.setText("Selecciona un Nivel");
        title.setLocalTranslation((sw - title.getLineWidth()) / 2f, sh - 50f, 0);
        buttonsNode.attachChild(title);

        float startX = sw / 2f - 150f;
        float startY = sh / 2f;
        float btnSize = 80f;
        float gap = 40f;

        for (int i = 1; i <= GameState.TOTAL_LEVELS; i++) {
            Quad quad = new Quad(btnSize, btnSize);
            Geometry btn = new Geometry("LevelBtn_" + i, quad);
            
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            try {
                Texture tex = assetManager.loadTexture("Textures/level" + i + ".png");
                mat.setTexture("ColorMap", tex);
            } catch (Exception e) {
                // Fallback: Cuadro azul si no hay sprite
                mat.setColor("Color", ColorRGBA.Blue);
            }
            btn.setMaterial(mat);
            
            float posX = startX + ((i - 1) * (btnSize + gap));
            btn.setLocalTranslation(posX, startY, 0);
            btn.setUserData("levelIndex", i); // Guardamos el índice del nivel aquí
            
            buttonsNode.attachChild(btn);

            // Texto del número encima del botón
            BitmapText numText = new BitmapText(font, false);
            numText.setSize(font.getCharSet().getRenderedSize() * 1.5f);
            numText.setText(String.valueOf(i));
            numText.setLocalTranslation(posX + btnSize/2f - 10f, startY + btnSize/2f + 10f, 1);
            buttonsNode.attachChild(numText);
        }
    }

    private void registerInput() {
        inputManager.addMapping("ClickSelect", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, "ClickSelect");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("ClickSelect".equals(name) && isPressed) {
            Vector2f click2d = inputManager.getCursorPosition();
            Vector3f click3d = new Vector3f(click2d.x, click2d.y, 10f);
            Vector3f dir = new Vector3f(0, 0, -1f);
            Ray ray = new Ray(click3d, dir);

            CollisionResults results = new CollisionResults();
            buttonsNode.collideWith(ray, results);

            if (results.size() > 0) {
                Geometry target = results.getClosestCollision().getGeometry();
                if (target.getName().startsWith("LevelBtn_")) {
                    int level = target.getUserData("levelIndex");
                    startGame(level);
                }
            }
        }
    }

    private void startGame(int levelIndex) {
        AppStateManager sm = app.getStateManager();
        sm.attach(new HUDState());
        sm.attach(new GameState(bulletAppState, levelIndex));
        sm.detach(this);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(buttonsNode);
        try { inputManager.deleteMapping("ClickSelect"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}