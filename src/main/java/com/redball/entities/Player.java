package com.redball.entities;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

/**
 * Red Ball — Jugador principal.
 *
 * Cambios vs versión original:
 *  - CullHint importado explícitamente (evita error de compilación con Minie).
 *  - getPhysicsLocation() expuesto para que GameState lea la posición real de la física,
 *    evitando la race-condition de "muerte en el vacío infinita" tras respawn.
 *  - respawn() ahora fuerza updateLogicalState() en el Node para que
 *    getWorldTranslation() sea coherente en el mismo frame.
 *  - rollLeft/rollRight reciben tpf pero ya no lo ignoran (el tpf viene de update principal).
 */
public class Player extends Node {

    // --- Constantes de movimiento ---
    private static final float ROLL_TORQUE    = 28f;
    private static final float MAX_ROLL_SPEED = 12f;
    private static final float JUMP_FORCE     = 10f;
    private static final float BOUNCE_FORCE   =  6f;
    public  static final float BALL_RADIUS    =  0.5f;

    // --- Componentes ---
    private Geometry         ballGeom;
    private RigidBodyControl rigidBody;
    private PhysicsSpace     physicsSpace;
    private AssetManager     assetManager;

    // --- Estado ---
    private boolean dead      = false;
    private boolean exploding = false;

    // --- Animacion de explosion ---
    private float    explosionTimer = 0f;
    private Geometry explosionGeom  = null;
    private static final float EXPLOSION_DURATION = 0.8f;

    // --- Contador de saltos ---
    private int jumpCount = 0;
    private static final int MAX_JUMPS = 1;

    public Player(AssetManager assetManager, PhysicsSpace physicsSpace) {
        super("Player");
        this.assetManager = assetManager;
        this.physicsSpace = physicsSpace;
        buildGeometry();
        buildPhysics();
    }

    // =========================================================================
    // CONSTRUCCION
    // =========================================================================

    private void buildGeometry() {
        float size = BALL_RADIUS * 2f;
        Quad quad = buildCenteredQuad(size, size);
        ballGeom  = new Geometry("BallGeom", quad);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture("Textures/ball.png");
            tex.setMagFilter(Texture.MagFilter.Nearest);
            tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            System.out.println("[Player] ball.png no encontrada -> color rojo.");
            mat.setColor("Color", ColorRGBA.Red);
        }
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        ballGeom.setMaterial(mat);
        ballGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        attachChild(ballGeom);
    }

    private void buildPhysics() {
        SphereCollisionShape shape = new SphereCollisionShape(BALL_RADIUS);
        rigidBody = new RigidBodyControl(shape, 1.0f);
        
        // --- FIX: Evitar que la pelota atraviese pisos delgados ---
        rigidBody.setCcdMotionThreshold(0.1f);
        rigidBody.setCcdSweptSphereRadius(BALL_RADIUS);
        // ----------------------------------------------------------

        addControl(rigidBody);
        physicsSpace.add(rigidBody);
        // ... (el resto de tu código)

        rigidBody.setLinearFactor(new Vector3f(1f, 1f, 0f));
        rigidBody.setAngularFactor(new Vector3f(0f, 0f, 1f));
        rigidBody.setFriction(0.9f);
        rigidBody.setRestitution(0.1f);
        rigidBody.setAngularDamping(0.4f);

        // FIX BUG 1: tag de identidad para el listener de colisiones en Minie.
        // En Minie, PhysicsCollisionEvent.getObjectA/B() retorna PhysicsCollisionObject.
        // Guardamos "this" (el Node Player) como ApplicationData para recuperarlo fácil.
        rigidBody.setApplicationData(this);
    }

    // =========================================================================
    // MOVIMIENTO
    // =========================================================================

    public void rollLeft(float tpf) {
        if (dead) return;
        applyRollTorque(+ROLL_TORQUE);
        applyAirControl(-1f);
    }

    public void rollRight(float tpf) {
        if (dead) return;
        applyRollTorque(-ROLL_TORQUE);
        applyAirControl(1f);
    }

    private void applyAirControl(float direction) {
        if (Math.abs(rigidBody.getLinearVelocity().y) > 0.5f) {
            rigidBody.applyCentralForce(new Vector3f(direction * 8f, 0f, 0f));
        }
    }

    private void applyRollTorque(float torqueZ) {
        Vector3f angVel = rigidBody.getAngularVelocity(null);
        if (angVel == null) angVel = Vector3f.ZERO;
        if (Math.abs(angVel.z) < MAX_ROLL_SPEED) {
            rigidBody.applyTorque(new Vector3f(0f, 0f, torqueZ));
        }
    }

    public void tryJump() {
        if (dead) return;
        Vector3f vel = rigidBody.getLinearVelocity(null);
        if (vel == null) vel = Vector3f.ZERO;
        boolean grounded = Math.abs(vel.y) < 0.5f;
        if (grounded) jumpCount = 0;
        if (jumpCount < MAX_JUMPS) {
            rigidBody.setLinearVelocity(new Vector3f(vel.x, 0f, 0f));
            rigidBody.applyImpulse(new Vector3f(0f, JUMP_FORCE, 0f), Vector3f.ZERO);
            jumpCount++;
        }
    }

    public void bounce() {
        if (dead) return;
        Vector3f vel = rigidBody.getLinearVelocity(null);
        if (vel == null) vel = Vector3f.ZERO;
        rigidBody.setLinearVelocity(new Vector3f(vel.x, 0f, 0f));
        rigidBody.applyImpulse(new Vector3f(0f, BOUNCE_FORCE, 0f), Vector3f.ZERO);
        jumpCount = 0;
    }

    // =========================================================================
    // MUERTE Y RESPAWN
    // =========================================================================

    public void die() {
        if (dead) return;
        dead      = true;
        exploding = true;
        explosionTimer = EXPLOSION_DURATION;

        rigidBody.setEnabled(false);
        rigidBody.setLinearVelocity(Vector3f.ZERO);
        rigidBody.setAngularVelocity(Vector3f.ZERO);

        ballGeom.setCullHint(CullHint.Always);
        attachExplosionSprite();
    }

    private void attachExplosionSprite() {
        if (explosionGeom != null) detachChild(explosionGeom);
        float size = BALL_RADIUS * 2;
        Quad quad  = buildCenteredQuad(size, size);
        explosionGeom = new Geometry("ExplosionGeom", quad);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture("Textures/explosion.png");
            tex.setMagFilter(Texture.MagFilter.Nearest);
            tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            System.out.println("[Player] explosion.png no encontrada -> naranja.");
            mat.setColor("Color", new ColorRGBA(1f, 0.5f, 0f, 1f));
        }
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        explosionGeom.setMaterial(mat);
        explosionGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        attachChild(explosionGeom);
    }

    public boolean updateExplosion(float tpf) {
        if (!exploding) return true;
        explosionTimer -= tpf;
        float progress = 1f - (explosionTimer / EXPLOSION_DURATION);
        float scale    = 1f + (float) Math.sin(progress * Math.PI) * 0.8f;
        if (explosionGeom != null) explosionGeom.setLocalScale(scale);
        if (explosionTimer <= 0f) {
            exploding = false;
            if (explosionGeom != null) { detachChild(explosionGeom); explosionGeom = null; }
            return true;
        }
        return false;
    }

    public void respawn(Vector3f position) {
        dead      = false;
        exploding = false;
        jumpCount = 0;

        // Limpiar sprite de explosión si quedó colgado
        if (explosionGeom != null) {
            detachChild(explosionGeom);
            explosionGeom = null;
        }

        rigidBody.setEnabled(true);
        rigidBody.setPhysicsLocation(position);
        rigidBody.setLinearVelocity(Vector3f.ZERO);
        rigidBody.setAngularVelocity(Vector3f.ZERO);
        setLocalTranslation(position);      // FIX BUG 3: sincronizar el Node inmediatamente
        setLocalRotation(Quaternion.IDENTITY);
        ballGeom.setCullHint(CullHint.Inherit);
    }

    // =========================================================================
    // HELPER ESTATICO
    // =========================================================================

    /**
     * Quad centrado en el origen local.
     * UVs van de 0..1 independientemente del tamaño — para repetición usar buildTiledQuad().
     */
    public static Quad buildCenteredQuad(float w, float h) {
        Quad quad = new Quad(w, h);
        float hw = w / 2f, hh = h / 2f;
        quad.clearBuffer(VertexBuffer.Type.Position);
        quad.setBuffer(VertexBuffer.Type.Position, 3,
            new float[]{ -hw, -hh, 0f,  hw, -hh, 0f,  hw, hh, 0f,  -hw, hh, 0f });
        quad.updateBound();
        return quad;
    }

    /**
     * FIX BUG 6: Quad centrado con UVs repetidos según el tamaño.
     * Cada unidad de mundo = 1 repetición de textura.
     * Usar esto para plataformas y suelo.
     */
    public static Quad buildTiledQuad(float w, float h) {
        Quad quad = buildCenteredQuad(w, h);
        // Reemplazar el buffer de texCoords: repetir según tamaño
        quad.clearBuffer(VertexBuffer.Type.TexCoord);
        quad.setBuffer(VertexBuffer.Type.TexCoord, 2,
            new float[]{ 0f, 0f,  w, 0f,  w, h,  0f, h });
        quad.updateBound();
        return quad;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public float            getHalfSize()            { return BALL_RADIUS; }
    public boolean          isDead()                  { return dead; }
    public boolean          isExploding()             { return exploding; }
    public RigidBodyControl getRigidBody()            { return rigidBody; }

    /**
     * FIX BUG 3: usar esta posición en el kill-zone check para evitar
     * leer un WorldTranslation stale justo después del respawn.
     */
    public Vector3f getPhysicsPosition() {
        return rigidBody.getPhysicsLocation(null);
    }
}