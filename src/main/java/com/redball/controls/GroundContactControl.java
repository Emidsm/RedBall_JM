package com.redball.controls;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;

/**
 * GroundContactControl
 *
 * Control auxiliar que detecta si el jugador está tocando el suelo.
 * Se usa para evitar saltos en el aire (o limitar a N saltos).
 *
 * Estrategia: analizar el vector de normal de colisión.
 * Si la normal apunta hacia arriba (Y > 0.5), hay contacto con el suelo.
 *
 * NOTA: JME3 no provee un "onGround" directo, por lo que
 * se usa PhysicsCollisionListener + decaimiento por tiempo.
 */
public class GroundContactControl extends AbstractControl implements PhysicsCollisionListener {

    private final RigidBodyControl rigidBody;
    private boolean onGround    = false;
    private float   coyoteTime  = 0f; // Segundos restantes de "gracia" tras caer del borde
    private static final float COYOTE_DURATION = 0.12f; // 120ms de gracia

    public GroundContactControl(RigidBodyControl rigidBody) {
        this.rigidBody = rigidBody;
    }

    // =========================================================================
    // PHYSICS COLLISION LISTENER
    // =========================================================================

    @Override
    public void collision(PhysicsCollisionEvent event) {
        // Verificar que la colisión involucra a esta entidad
        boolean myCollision =
            event.getObjectA() == rigidBody ||
            event.getObjectB() == rigidBody;

        if (!myCollision) return;

        // Obtener la normal de colisión
        Vector3f normal = event.getNormalWorldOnB();
        if (normal == null) return;

        // Si la normal apunta hacia arriba → estamos parados sobre algo
        if (normal.y > 0.5f || normal.y < -0.5f) {
            onGround   = true;
            coyoteTime = COYOTE_DURATION;
        }
    }

    // =========================================================================
    // CONTROL UPDATE
    // =========================================================================

    @Override
    protected void controlUpdate(float tpf) {
        if (coyoteTime > 0f) {
            coyoteTime -= tpf;
            if (coyoteTime <= 0f) {
                onGround = false;
            }
        }

        // Alternativa/complemento: usar la velocidad vertical
        // Si la bola está cayendo rápido, claramente no está en el suelo.
        float velY = rigidBody.getLinearVelocity().y;
        if (Math.abs(velY) > 3.5f) {
            onGround = false;
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) { /* unused */ }

    // =========================================================================
    // API
    // =========================================================================

    public boolean isOnGround() { return onGround; }

    /** Forzar "en suelo" tras un bounce o teleport. */
    public void setOnGround(boolean v) {
        onGround   = v;
        coyoteTime = v ? COYOTE_DURATION : 0f;
    }
}
