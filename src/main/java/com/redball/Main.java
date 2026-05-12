package com.redball;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.renderer.RenderManager;
import com.redball.states.GameState;
import com.redball.states.HUDState;

/**
 * Red Ball Clone - JMonkeyEngine 3
 * Punto de entrada principal de la aplicación.
 * Cámara ortográfica + físicas 2D en plano XY.
 */
public class Main extends SimpleApplication {

    private BulletAppState bulletAppState;

    public static void main(String[] args) {
        Main app = new Main();

        // Configuración de ventana
        app.setShowSettings(false);
        com.jme3.system.AppSettings settings = new com.jme3.system.AppSettings(true);
        settings.setTitle("Red Ball - JME3");
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFrameRate(60);
        settings.setSamples(4); // Anti-aliasing
        app.setSettings(settings);

        app.start();
    }

    @Override
    public void simpleInitApp() {
        // --- Física ---
        bulletAppState = new BulletAppState();
        bulletAppState.setDebugEnabled(false); // true para ver hitboxes durante desarrollo
        stateManager.attach(bulletAppState);

        // --- Cámara Ortográfica (ParallelProjection) ---
        setupOrthoCamera();

        // --- Desactivar el FlyByCamera por defecto ---
        flyCam.setEnabled(false);

        // --- Estados del juego ---
        GameState gameState = new GameState(bulletAppState);
        stateManager.attach(gameState);

        HUDState hudState = new HUDState();
        stateManager.attach(hudState);
    }

    /**
     * Configura la cámara en modo ortográfico.
     * El "zoom" se controla con setFrustum; aquí 1 unidad JME = 1 pixel de referencia a 720p.
     * Ajusta frustumSize para cambiar cuánto del mundo es visible.
     */
    private void setupOrthoCamera() {
        float frustumSize = 10f;
        float aspect = (float) cam.getWidth() / (float) cam.getHeight();

        cam.setParallelProjection(true);
        
        // Setters individuales explícitos
        cam.setFrustumNear(1f);
        cam.setFrustumFar(1000f);
        cam.setFrustumLeft(-frustumSize * aspect);
        cam.setFrustumRight(frustumSize * aspect);
        cam.setFrustumTop(frustumSize);
        cam.setFrustumBottom(-frustumSize);

        cam.setLocation(new com.jme3.math.Vector3f(0, 0, 10f));
        cam.lookAt(com.jme3.math.Vector3f.ZERO, com.jme3.math.Vector3f.UNIT_Y);
        
        // Forzar actualización de la cámara
        cam.update();
    }

    @Override
    public void simpleUpdate(float tpf) { /* Lógica en AppStates */ }

    @Override
    public void simpleRender(RenderManager rm) { /* unused */ }

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }
}
