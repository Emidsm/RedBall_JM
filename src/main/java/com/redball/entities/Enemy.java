package com.redball.entities;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;

import static com.redball.entities.Player.buildCenteredQuad;

/**
 * Enemy — Cuadrado patrullero.
 *
 * Cambios vs versión original:
 *  - buildPhysics() usa rigidBody.setApplicationData(this) en lugar de setUserObject().
 *    En Minie, PhysicsCollisionEvent.getObjectA/B() retorna PhysicsCollisionObject;
 *    para recuperar la entidad hay que llamar getApplicationData(). Con setUserObject()
 *    (que es un método del Node/Spatial) la colisión nunca se detecta correctamente.
 *  - syncPhysics() usa getWorldTranslation() que es correcto: el Enemy vive en levelNode
 *    que está en el origen, así que World == Local en este setup.
 */
public class Enemy extends Node {

    private static final float SIZE = 0.9f;
    static final         float HALF = SIZE / 2f;   // package-visible para GameState

    // --- Patrulla ---
    private final float patrolMinX;
    private final float patrolMaxX;
    private float moveSpeed = 2.5f;
    private int   direction = 1;

    // --- Rotacion cara-a-cara ---
    private float stepProgress = 0f;
    private float currentAngle = 0f;
    private float targetAngle  = 0f;

    // --- Componentes ---
    private Geometry         enemyGeom;
    private RigidBodyControl rigidBody;
    private final PhysicsSpace     physicsSpace;
    private final AssetManager     assetManager;

    // --- Muerte ---
    private boolean dying         = false;
    private boolean readyToRemove = false;
    private float   dyingTimer    = 0f;
    private static final float DYING_DURATION = 0.2f;

    private static int idCounter = 0;

    public Enemy(AssetManager assetManager, PhysicsSpace physicsSpace,
                 float patrolMinX, float patrolMaxX) {
        super("Enemy_" + (++idCounter));
        this.assetManager = assetManager;
        this.physicsSpace = physicsSpace;
        this.patrolMinX   = patrolMinX;
        this.patrolMaxX   = patrolMaxX;

        buildGeometry();
        buildPhysics();
    }

    // =========================================================================
    // CONSTRUCCION
    // =========================================================================

    private void buildGeometry() {
        enemyGeom = new Geometry("EnemyGeom_" + getName(), buildCenteredQuad(SIZE, SIZE));

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            Texture tex = assetManager.loadTexture("Textures/enemy.png");
            tex.setMagFilter(Texture.MagFilter.Nearest);
            tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
            mat.setTexture("ColorMap", tex);
        } catch (Exception e) {
            System.out.println("[Enemy] enemy.png no encontrada -> gris oscuro.");
            mat.setColor("Color", new ColorRGBA(0.25f, 0.25f, 0.25f, 1f));
        }
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        enemyGeom.setMaterial(mat);
        enemyGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        attachChild(enemyGeom);
    }

    private void buildPhysics() {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(HALF, HALF, HALF));
        rigidBody = new RigidBodyControl(shape, 1f);
        addControl(rigidBody);
        physicsSpace.add(rigidBody);

        rigidBody.setKinematic(true);

        // FIX BUG 1: ApplicationData (Minie) en lugar de UserObject (Spatial).
        // GameState.collision() hace: event.getObjectA().getApplicationData()
        // para obtener este Enemy directamente.
        rigidBody.setApplicationData(this);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    public void updateEnemy(float tpf) {
        if (dying) {
            updateSquishAnimation(tpf);
            return;
        }
        updatePatrol(tpf);
        updateFaceRotation();
        syncPhysics();
    }

    // =========================================================================
    // PATRULLA
    // =========================================================================

    private void updatePatrol(float tpf) {
        float delta = moveSpeed * tpf * direction;
        float newX  = getLocalTranslation().x + delta;

        if (newX >= patrolMaxX && direction > 0) {
            newX      = patrolMaxX;
            direction = -1;
            startNewStep();
        } else if (newX <= patrolMinX && direction < 0) {
            newX      = patrolMinX;
            direction = 1;
            startNewStep();
        }

        setLocalTranslation(newX, getLocalTranslation().y, 0f);

        stepProgress += Math.abs(delta);
        if (stepProgress >= SIZE) {
            currentAngle = targetAngle;
            stepProgress = stepProgress - SIZE;
            startNewStep();
        }
    }

    private void startNewStep() {
        currentAngle = targetAngle;
        stepProgress = 0f;
        targetAngle  = currentAngle + (-FastMath.HALF_PI * direction);
    }

    // =========================================================================
    // ROTACION CARA-A-CARA
    // =========================================================================

    private void updateFaceRotation() {
        float t       = FastMath.clamp(stepProgress / SIZE, 0f, 1f);
        float smoothT = t * t * (3f - 2f * t);
        float angle   = FastMath.interpolateLinear(smoothT, currentAngle, targetAngle);
        Quaternion rot = new Quaternion();
        rot.fromAngleAxis(angle, Vector3f.UNIT_Z);
        enemyGeom.setLocalRotation(rot);
    }

    private void syncPhysics() {
        // getWorldTranslation() es correcto porque levelNode está en el origen.
        rigidBody.setPhysicsLocation(getWorldTranslation());
    }

    // =========================================================================
    // MUERTE
    // =========================================================================

    public void startDying() {
        if (dying) return;
        dying      = true;
        dyingTimer = 0f;
        rigidBody.setEnabled(false);
    }

    private void updateSquishAnimation(float tpf) {
        dyingTimer += tpf;
        float progress = FastMath.clamp(dyingTimer / DYING_DURATION, 0f, 1f);
        float eased  = 1f - (1f - progress) * (1f - progress);
        float scaleY = FastMath.interpolateLinear(eased, 1.0f, 0.1f);
        float scaleX = FastMath.interpolateLinear(eased, 1.0f, 1.5f);
        setLocalScale(scaleX, scaleY, 1f);
        if (progress >= 1f) readyToRemove = true;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public boolean          isDying()          { return dying; }
    public boolean          isReadyToRemove()  { return readyToRemove; }
    public float            getHalfHeight()    { return HALF; }
    public RigidBodyControl getPhysicsControl(){ return rigidBody; }
}