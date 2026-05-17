package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class LoadingState extends AbstractAppState {

    private SimpleApplication app;
    private Node guiNode;
    private Node loadingNode;
    
    private final BulletAppState bulletAppState;
    private final int targetLevel;
    private float frameCount = 0; // Espera un cuadro completo para asegurar el render

    public LoadingState(BulletAppState bulletAppState, int targetLevel) {
        this.bulletAppState = bulletAppState;
        this.targetLevel = targetLevel;
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();
        AssetManager assetManager = app.getAssetManager();

        loadingNode = new Node("LoadingNode");
        guiNode.attachChild(loadingNode);

        float sw = app.getCamera().getWidth(), sh = app.getCamera().getHeight();
        
        // Fondo negro total temporal
        Geometry bg = new Geometry("LoadingBG", new Quad(sw, sh));
        Material matBg = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        matBg.setColor("Color", ColorRGBA.Black);
        bg.setMaterial(matBg);
        loadingNode.attachChild(bg);

        // Icono de carga centrado
        float iconSize = 120f;
        Geometry icon = new Geometry("LoadingIcon", new Quad(iconSize, iconSize));
        Material matIcon = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        matIcon.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        try {
            matIcon.setTexture("ColorMap", assetManager.loadTexture("Textures/loading_icon.png"));
        } catch (Exception e) {
            matIcon.setColor("Color", ColorRGBA.White);
        }
        icon.setMaterial(matIcon);
        icon.setLocalTranslation((sw - iconSize) / 2f, (sh - iconSize) / 2f, 1f);
        loadingNode.attachChild(icon);
    }

    @Override
    public void update(float tpf) {
        frameCount += tpf;
        // Una vez que el frame se dibuja en pantalla, adjuntamos el juego de forma segura
        if (frameCount > 0.1f) {
            AppStateManager sm = app.getStateManager();
            sm.attach(new HUDState());
            sm.attach(new GameState(bulletAppState, targetLevel));
            sm.detach(this);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(loadingNode);
    }
}