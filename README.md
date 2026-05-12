# Red Ball Clone — JMonkeyEngine 3
> Proyecto de portafolio — Mecánicas 2D en motor 3D

---

## 📁 Estructura del Proyecto

```
RedBallGame/
├── pom.xml
└── src/main/
    ├── java/com/redball/
    │   ├── Main.java                        ← Punto de entrada, cámara ortográfica
    │   ├── states/
    │   │   ├── GameState.java               ← Lógica principal, colisiones, cámara follow
    │   │   └── HUDState.java                ← UI en pantalla (vidas, nivel, mensajes)
    │   ├── entities/
    │   │   ├── Player.java                  ← Red Ball: físicas, rolling, salto, muerte
    │   │   └── Enemy.java                   ← Cuadrado: patrulla + rotación cara-a-cara
    │   ├── controls/
    │   │   └── GroundContactControl.java    ← Detector de contacto con suelo
    │   └── level/
    │       └── Level1Builder.java           ← Constructor del nivel tutorial
    └── resources/assets/
        ├── Textures/
        │   ├── ball.png          ← Sprite de la bola roja (requerido)
        │   ├── enemy.png         ← Sprite del enemigo cuadrado (requerido)
        │   ├── explosion.png     ← Sprite de explosión al morir (requerido)
        │   ├── ground.png        ← Textura del suelo (opcional, fallback marrón)
        │   ├── platform.png      ← Textura de plataformas (opcional)
        │   ├── background.png    ← Fondo del nivel (opcional, fallback azul cielo)
        │   └── flag.png          ← Bandera de meta (opcional)
        └── Sounds/               ← Para el futuro (SFX de salto, muerte, etc.)
```

---

## 🎮 Controles

| Acción       | Tecla             |
|--------------|-------------------|
| Mover izq.   | `← / A`           |
| Mover der.   | `→ / D`           |
| Saltar       | `Espacio / ↑ / W` |
| Doble salto  | Segunda pulsación de salto en el aire |

---

## 🧠 Arquitectura — Decisiones Técnicas

### Cámara Ortográfica
```java
cam.setParallelProjection(true);
cam.setFrustum(-1000, 1000, -frustumSize*aspect, frustumSize*aspect, -frustumSize, frustumSize);
```
`frustumSize = 10f` → En una pantalla 1280×720 se ven ≈ 18 unidades de ancho × 20 de alto.
Ajusta este valor para hacer "zoom in/out".

### Cámara Follow sin mover la cámara real
En lugar de mover `cam`, se mueve el `levelNode` en dirección opuesta al jugador.
Esto preserva la proyección ortográfica que configuramos:
```java
levelNode.setLocalTranslation(-playerX, -playerY + offset, 0f);
```

### Físicas 2D con Bullet 3D
Bullet es un motor 3D, pero lo forzamos a 2D con:
```java
rigidBody.setLinearFactor(new Vector3f(1, 1, 0));   // Sin traslación en Z
rigidBody.setAngularFactor(new Vector3f(0, 0, 1));  // Solo rotación en Z
```

### Rotación cara-a-cara del enemigo
El cuadrado **no** usa `setAngularVelocity`. En cambio:
1. Se mide cuántos "pasos" (= `SIZE` unidades) ha avanzado.
2. Cada paso genera un nuevo target de +90° o -90°.
3. Se interpola con una curva **ease-in-out cúbica** para que el giro se "acelere y frene" como una caja pivotando en su arista.
4. La física (kinematic) no rota; solo rota el `Geometry` visual.

### Efecto de Aplastado (Procedural, sin Spritesheet)
```java
float scaleY = FastMath.interpolateLinear(eased, 1.0f, 0.1f); // 1.0 → 0.1 en 200ms
float scaleX = FastMath.interpolateLinear(eased, 1.0f, 1.5f); // Se "expande" horizontalmente
enemyNode.setLocalScale(scaleX, scaleY, 1f);
```
La hitbox se desactiva inmediatamente (`rigidBody.setEnabled(false)`) para que el jugador no
reciba daño durante la animación de muerte.

### Detección de Stomp vs Golpe Lateral
```java
float playerBottom = playerPos.y - player.getHalfSize();
float enemyTop     = enemyPos.y  + enemy.getHalfHeight();
boolean stomped    = playerBottom >= (enemyTop - 0.35f); // margen de tolerancia
```
El margen `0.35f` evita que saltos rasantes al borde del enemigo maten al jugador.
Ajústalo si la detección se siente injusta.

---

## 🎨 Texturas — Guía de Tamaños Recomendados

| Archivo          | Tamaño sugerido | Notas                                      |
|------------------|-----------------|--------------------------------------------|
| `ball.png`       | 64×64 px        | Fondo transparente (PNG-32). La bola ocupa todo el cuadrado. |
| `enemy.png`      | 64×64 px        | Fondo transparente. El cuadrado tendrá una "cara" molesta. |
| `explosion.png`  | 128×128 px      | Fondo transparente. Una sola imagen estática de explosión. |
| `ground.png`     | 64×64 px        | Se repite en tile (wrap mode = Repeat). |
| `platform.png`   | 64×32 px        | Se repite en tile. |
| `background.png` | 1280×720 px     | Fondo de cielo/escenario. |
| `flag.png`       | 32×48 px        | Bandera de llegada. |

**Filtro de pixel art**: El código aplica `MagFilter.Nearest` a todas las texturas para
que no se vean borrosas al escalar. Es fundamental para pixel art.

---

## 🚀 Cómo Ejecutar

```bash
# Con Maven
cd RedBallGame
mvn clean package
mvn exec:java

# O ejecutar el JAR generado
java -jar target/red-ball-game-1.0-SNAPSHOT.jar
```

---

## 🗺️ Nivel 2 con Tiled

Para integrar mapas de Tiled (`.tmx`):
1. Añadir la dependencia `jme3-plugins` o usar la librería `tmx-loader` de la comunidad JME.
2. Cargar el mapa: `TiledMapLoader.load("Levels/level2.tmx", assetManager, physicsSpace, levelNode)`.
3. Las capas de "colisión" en Tiled se convierten automáticamente en `RigidBodyControl` estáticos.
4. Los "object layers" de Tiled pueden marcar spawn points de enemigos.

Librería recomendada: **[jme3-tiled](https://github.com/jmecn/TMXLoader)**

---

## 📋 TODO / Próximas Mejoras

- [ ] Sistema de monedas con trigger zones (`GhostControl`)
- [ ] Sonidos: `AudioNode` para salto, muerte, aplastado
- [ ] AnimationControl con spritesheet para el jugador (parpadeo al rodar)
- [ ] Pantalla de título y menú principal (`AppState`)
- [ ] Guardado de récord local (`.properties` o JSON)
- [ ] Carga de Nivel 2 desde Tiled TMX
- [ ] Parallax scrolling para el fondo (múltiples capas a velocidades distintas)
