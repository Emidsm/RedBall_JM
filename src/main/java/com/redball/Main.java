package com.redball;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.renderer.RenderManager;
import com.jme3.system.AppSettings;
import com.redball.states.MenuState;

/**
 * Red Ball Clone - JMonkeyEngine 3
 * Punto de entrada principal de la aplicación.
 * Cámara ortográfica + físicas 2D en plano XY.
 * Pantalla completa con resolución nativa del monitor.
 */
public class Main extends SimpleApplication {

    private BulletAppState bulletAppState;

    public static void main(String[] args) {
        Main app = new Main();

        AppSettings settings = new AppSettings(true);
        settings.setTitle("Red Ball - JME3");
        settings.setSamples(4);
        settings.setFrameRate(60);

        // --- PANTALLA COMPLETA a resolución nativa ---
        java.awt.GraphicsDevice gd =
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice();
        java.awt.DisplayMode dm = gd.getDisplayMode();
        settings.setWidth(dm.getWidth());
        settings.setHeight(dm.getHeight());
        settings.setFullscreen(false);
        settings.setResizable(false);
        settings.setFrequency(dm.getRefreshRate() > 0 ? dm.getRefreshRate() : 60);
        settings.setBitsPerPixel(dm.getBitDepth() > 0 ? dm.getBitDepth() : 32);
        settings.setVSync(true);

        app.setShowSettings(false);
        app.setSettings(settings);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // --- Física ---
        bulletAppState = new BulletAppState();
        bulletAppState.setDebugEnabled(true);
        stateManager.attach(bulletAppState);

        // --- Cámara Ortográfica (igual que el Main original) ---
        setupOrthoCamera();

        // --- Desactivar FlyByCamera ---
        flyCam.setEnabled(false);

        // --- Mostrar cursor para los menús ---
        inputManager.setCursorVisible(true);

        // --- Arrancar con el menú principal ---
        stateManager.attach(new MenuState(bulletAppState));
    }

    /**
     * Cámara ortográfica idéntica al Main original.
     * frustumSize = 10f  →  mismo "zoom" que antes.
     */
    private void setupOrthoCamera() {
        float frustumSize = 7f;
        float aspect = (float) cam.getWidth() / (float) cam.getHeight();

        cam.setParallelProjection(true);
        cam.setFrustumNear(1f);
        cam.setFrustumFar(1000f);
        cam.setFrustumLeft(-frustumSize * aspect);
        cam.setFrustumRight(frustumSize * aspect);
        cam.setFrustumTop(frustumSize);
        cam.setFrustumBottom(-frustumSize);
        cam.setLocation(new com.jme3.math.Vector3f(0f, 0f, 10f));
        cam.lookAt(com.jme3.math.Vector3f.ZERO, com.jme3.math.Vector3f.UNIT_Y);
        cam.update();
    }

    @Override
    public void simpleUpdate(float tpf) { /* Lógica en AppStates */ }

    @Override
    public void simpleRender(RenderManager rm) { /* unused */ }

    public BulletAppState getBulletAppState() { return bulletAppState; }
}