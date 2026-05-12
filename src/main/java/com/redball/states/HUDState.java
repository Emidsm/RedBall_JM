package com.redball.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;

/**
 * HUD — Interfaz en pantalla.
 * Muestra: Vidas restantes, nivel actual, texto de "Game Over" / "¡Completo!".
 *
 * Usa guiNode (espacio 2D de pantalla) en lugar del rootNode 3D.
 */
public class HUDState extends AbstractAppState {

    private SimpleApplication app;
    private Node guiNode;
    private BitmapFont font;

    // Textos del HUD
    private BitmapText livesText;
    private BitmapText levelText;
    private BitmapText centerText; // Mensajes temporales

    // Estado interno
    private int   lives          = 3;
    private float centerTextTimer = 0f;
    private static final float CENTER_TEXT_DURATION = 2f;

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);

        this.app     = (SimpleApplication) application;
        this.guiNode = app.getGuiNode();

        font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        buildHUD();
    }

    private void buildHUD() {
        int screenW = app.getCamera().getWidth();
        int screenH = app.getCamera().getHeight();

        // --- Vidas ---
        livesText = new BitmapText(font, false);
        livesText.setSize(font.getCharSet().getRenderedSize() * 1.5f);
        livesText.setColor(ColorRGBA.White);
        livesText.setText("❤ x " + lives);
        livesText.setLocalTranslation(20, screenH - 20, 0);
        guiNode.attachChild(livesText);

        // --- Nivel ---
        levelText = new BitmapText(font, false);
        levelText.setSize(font.getCharSet().getRenderedSize() * 1.2f);
        levelText.setColor(new ColorRGBA(1f, 0.9f, 0.3f, 1f));
        levelText.setText("Nivel 1 — Tutorial");
        // Centrar horizontalmente
        levelText.setLocalTranslation(screenW / 2f - 80f, screenH - 20, 0);
        guiNode.attachChild(levelText);

        // --- Mensaje central (inicialmente vacío) ---
        centerText = new BitmapText(font, false);
        centerText.setSize(font.getCharSet().getRenderedSize() * 2f);
        centerText.setColor(ColorRGBA.Yellow);
        centerText.setText("");
        centerText.setLocalTranslation(screenW / 2f - 100f, screenH / 2f + 30f, 0);
        guiNode.attachChild(centerText);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Hacer que el mensaje central desaparezca después de un tiempo
        if (centerTextTimer > 0f) {
            centerTextTimer -= tpf;
            if (centerTextTimer <= 0f) {
                centerText.setText("");
            }
        }
    }

    // =========================================================================
    // API PÚBLICA — llamada desde GameState
    // =========================================================================

    public void onPlayerDied() {
        lives = Math.max(0, lives - 1);
        livesText.setText("❤ x " + lives);

        if (lives <= 0) {
            showCenterMessage("¡Game Over!", ColorRGBA.Red, 999f);
        } else {
            showCenterMessage("¡Ouch!", ColorRGBA.Orange, 1.5f);
        }
    }

    public void onEnemyKilled() {
        showCenterMessage("¡Aplastado!", ColorRGBA.Yellow, 1f);
    }

    public void onLevelComplete() {
        showCenterMessage("¡Nivel Completado!", ColorRGBA.Green, 999f);
    }

    public void showCenterMessage(String msg, ColorRGBA color, float duration) {
        centerText.setText(msg);
        centerText.setColor(color);
        centerTextTimer = duration;
    }

    // =========================================================================
    // MÉTODOS AÑADIDOS PARA GAMESTATE
    // =========================================================================

    public int getLives() {
        return this.lives;
    }

    public void reset() {
        this.lives = 3;
        this.livesText.setText("❤ x " + lives);
        this.centerText.setText("");
        this.centerTextTimer = 0f;
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Override
    public void cleanup() {
        super.cleanup();
        guiNode.detachChild(livesText);
        guiNode.detachChild(levelText);
        guiNode.detachChild(centerText);
    }
}
