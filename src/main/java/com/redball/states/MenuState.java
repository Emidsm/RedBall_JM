package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
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
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

public class MenuState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private Node guiNode;
    private AssetManager assetManager;
    private InputManager inputManager;
    private Camera cam;

    private Node menuNode;
    private Geometry bgGeom;
    private Geometry btnPlay;
    private Geometry btnExit;

    private final com.jme3.bullet.BulletAppState bulletAppState;

    public MenuState(com.jme3.bullet.BulletAppState bulletAppState) {
        this.bulletAppState = bulletAppState;
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();
        this.assetManager = app.getAssetManager();
        this.inputManager = app.getInputManager();
        this.cam = app.getCamera();

        menuNode = new Node("MenuNode");
        // Aseguramos que todo el nodo herede el comportamiento de la GUI
        menuNode.setQueueBucket(RenderQueue.Bucket.Gui);
        guiNode.attachChild(menuNode);

        buildBackground();
        buildButtons();
        registerInput();
    }

    private void buildBackground() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        Quad quad = new Quad(sw, sh);
        bgGeom = new Geometry("MenuBG", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        
        // Desactivamos la prueba de profundidad para evitar colisiones de renderizado 2D
        mat.getAdditionalRenderState().setDepthTest(false);
        mat.getAdditionalRenderState().setDepthWrite(false);
        
        try {
            Texture tex = assetManager.loadTexture("Textures/background_main.png");
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", new ColorRGBA(0.05f, 0.05f, 0.15f, 1f));
        }
        bgGeom.setMaterial(mat);
        bgGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        menuNode.attachChild(bgGeom);
    }

    private void buildButtons() {
        float sw = cam.getWidth(), sh = cam.getHeight();
        
        // 1. FORMA CUADRADA: Mismo ancho y alto para que no se apachurren los círculos
        float btnSize = 120f; 
        
        // 2. DISTRIBUCIÓN HORIZONTAL:
        float gap = 60f; // Espacio de separación entre los botones
        
        // Calculamos el ancho total que ocupa el "bloque" de los dos botones
        float totalWidth = (btnSize * 2) + gap;
        
        // Posición X inicial para que todo el bloque quede perfectamente centrado
        float startX = (sw - totalWidth) / 2f;
        
        // Posición Y para ambos (centrados verticalmente, o ajusta si los quieres más abajo)
        float posY = (sh - btnSize) / 2f;

        // Botón Jugar (Se dibuja a la izquierda)
        btnPlay = createMenuButton("BtnPlay", "Textures/button_play.png", ColorRGBA.Green, btnSize, btnSize);
        btnPlay.setLocalTranslation(startX, posY, 1f);
        menuNode.attachChild(btnPlay);

        // Botón Salir (Se dibuja a la derecha)
        btnExit = createMenuButton("BtnExit", "Textures/button_exit.png", ColorRGBA.Red, btnSize, btnSize);
        // X = inicio + tamaño del primer botón + espacio de separación
        btnExit.setLocalTranslation(startX + btnSize + gap, posY, 1f);
        menuNode.attachChild(btnExit);
    }

    private Geometry createMenuButton(String name, String texPath, ColorRGBA fallback, float w, float h) {
        Quad quad = new Quad(w, h);
        Geometry geom = new Geometry(name, quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setDepthTest(false);
        mat.getAdditionalRenderState().setDepthWrite(false);
        
        try {
            Texture tex = assetManager.loadTexture(texPath);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            mat.setColor("Color", fallback);
        }
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui); // Corregido: Forzar modo GUI
        return geom;
    }

    private void registerInput() {
        inputManager.addMapping("MenuClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, "MenuClick");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("MenuClick".equals(name) && isPressed) {
            Vector2f click2d = inputManager.getCursorPosition();
            Vector3f click3d = new Vector3f(click2d.x, click2d.y, 10f);
            Ray ray = new Ray(click3d, new Vector3f(0, 0, -1f));
            CollisionResults results = new CollisionResults();
            menuNode.collideWith(ray, results);

            if (results.size() > 0) {
                Geometry target = results.getClosestCollision().getGeometry();
                if ("BtnPlay".equals(target.getName())) {
                    app.getStateManager().attach(new LevelSelectState(bulletAppState));
                    app.getStateManager().detach(this);
                } else if ("BtnExit".equals(target.getName())) {
                    app.stop();
                }
            }
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(menuNode);
        try { inputManager.deleteMapping("MenuClick"); } catch (Exception ignored) {}
        inputManager.removeListener(this);
    }
}