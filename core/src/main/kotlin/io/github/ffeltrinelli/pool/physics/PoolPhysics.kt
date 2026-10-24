package io.github.ffeltrinelli.pool.physics

import com.badlogic.gdx.math.*
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.dynamics.*
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.*

class Ball internal constructor(val id: Int, val body: btRigidBody) {
    val previousPosition = Vector3()
    val position = Vector3()
    val previousRotation = Quaternion()
    val rotation = Quaternion()
    var pocketed = false
        internal set
    val point: Point get() = Point(position.x, position.z)
}

/** Owns every Bullet allocation. All calls and event delivery stay on the owning thread. */
class PoolPhysics(
    private val events: (PhysicsEvent) -> Unit = {},
    private val impacts: (Impact) -> Unit = {},
    private val pocketDrops: (PocketDrop) -> Unit = {}
) : Disposable {
    private val configuration: btDefaultCollisionConfiguration
    private val dispatcher: btCollisionDispatcher
    private val broadphase: btDbvtBroadphase
    private val solver: btSequentialImpulseConstraintSolver
    private val world: btDiscreteDynamicsWorld
    private val ballShape: btSphereShape
    private val table: TablePhysics
    private val ownedBalls = mutableListOf<Ball>()
    val balls: List<Ball> get() = ownedBalls
    private val contactImpacts = ContactImpacts(impacts)
    private val transform = Matrix4()
    private val velocity = Vector3()
    private var accumulator = 0f
    private val restStability = RestStability()
    private var quietSeconds = 0f
    private var wasSettled = true
    var interpolation = 0f
        private set

    init {
        BulletRuntime.initialize()
        configuration = btDefaultCollisionConfiguration()
        dispatcher = btCollisionDispatcher(configuration)
        broadphase = btDbvtBroadphase()
        solver = btSequentialImpulseConstraintSolver()
        world = btDiscreteDynamicsWorld(dispatcher, broadphase, solver, configuration)
        world.solverInfo.numIterations = PhysicsTuning.SOLVER_ITERATIONS
        world.gravity = Vector3(0f, -PhysicsTuning.GRAVITY, 0f)
        table = TablePhysics(world)
        ballShape = btSphereShape(TableGeometry.BALL_RADIUS)
        reset()
    }

    private fun rigidBody(shape: btCollisionShape, mass: Float, point: Point): btRigidBody {
        val inertia = Vector3()
        if (mass > 0f) shape.calculateLocalInertia(mass, inertia)
        val info = btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, inertia)
        val body = btRigidBody(info)
        info.dispose()
        body.worldTransform = Matrix4().setToTranslation(point.x, TableGeometry.BALL_RADIUS, point.z)
        return body
    }

    fun reset(layout: List<Pair<Int, Point>> = TableGeometry.rack()) {
        contactImpacts.reset()
        balls.forEach(::disposeBall)
        ownedBalls.clear()
        layout.forEach { (id, point) -> addBall(id, point) }
        accumulator = 0f
        quietSeconds = 0f
        restStability.reset()
        wasSettled = true
        interpolation = 0f
    }

    private fun addBall(id: Int, point: Point): Ball {
        val body = rigidBody(ballShape, TableGeometry.BALL_MASS, point)
        // Texture stamps lie on local ±Z, away from sphere UV poles. Face +Z upward.
        body.worldTransform = Matrix4().setToTranslation(point.x, TableGeometry.BALL_RADIUS, point.z).rotate(Vector3.X, -90f)
        body.userValue = id
        body.restitution = PhysicsTuning.BALL_RESTITUTION
        body.friction = PhysicsTuning.BALL_FRICTION
        body.rollingFriction = PhysicsTuning.ROLLING_FRICTION
        body.spinningFriction = PhysicsTuning.SPINNING_FRICTION
        body.setDamping(0f, 0f)
        body.setSleepingThresholds(PhysicsTuning.LINEAR_SLEEP_SPEED, PhysicsTuning.ANGULAR_SLEEP_SPEED)
        body.ccdMotionThreshold = TableGeometry.BALL_RADIUS * 0.5f
        body.ccdSweptSphereRadius = TableGeometry.BALL_RADIUS * 0.8f
        world.addRigidBody(body)
        val ball = Ball(id, body).also { readTransform(it); it.previousPosition.set(it.position); it.previousRotation.set(it.rotation) }
        ownedBalls.add(ball)
        return ball
    }

    fun snapshot() = TableSnapshot(balls.filter { !it.pocketed }.map { ball ->
        BallSnapshot(ball.id, ball.point, ball.position.y, ball.rotation.x, ball.rotation.y, ball.rotation.z, ball.rotation.w)
    })

    /** Snapshots are taken only after settling: velocities are intentionally zero. */
    fun restore(snapshot: TableSnapshot) {
        reset(emptyList())
        snapshot.balls.forEach { saved ->
            val ball = addBall(saved.id, saved.point)
            ball.body.worldTransform = Matrix4().set(Vector3(saved.point.x, saved.height, saved.point.z),
                Quaternion(saved.qx, saved.qy, saved.qz, saved.qw))
            readTransform(ball)
            ball.previousPosition.set(ball.position)
            ball.previousRotation.set(ball.rotation)
        }
    }

    fun replaceCueBall() {
        val point = CueReplacement.position(balls.filter { it.id != 0 && !it.pocketed }.map { it.point })
        balls.firstOrNull { it.id == 0 }?.let {
            disposeBall(it)
            ownedBalls.remove(it)
        }
        contactImpacts.reset()
        addBall(0, point)
        interpolation = 0f
    }

    fun strike(shot: ShotCommand) {
        if (!shot.impulse.isFinite() || shot.impulse <= 0f || shot.direction == Point(0f, 0f)) return
        val cue = balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return
        cue.body.activate()
        val direction = shot.direction.normalized()
        cue.body.applyCentralImpulse(Vector3(direction.x * shot.impulse, 0f, direction.z * shot.impulse))
        quietSeconds = 0f
        restStability.reset()
        wasSettled = false
        impacts(Impact(ImpactKind.CUE, shot.impulse))
    }

    fun advance(delta: Float) {
        accumulator += delta.coerceIn(0f, 0.1f)
        var steps = 0
        while (accumulator >= STEP && steps++ < 12) {
            step()
            accumulator -= STEP
        }
        interpolation = (accumulator / STEP).coerceIn(0f, 1f)
    }

    fun step() {
        savePreviousTransforms()
        world.stepSimulation(STEP, 0)
        collectImpactContacts()
        observeBalls()
        updateSettling()
    }

    private fun savePreviousTransforms() {
        balls.forEach {
            it.previousPosition.set(it.position)
            it.previousRotation.set(it.rotation)
        }
    }

    private fun observeBalls() {
        var moving = false
        restStability.beginStep()
        for (ball in balls) {
            if (ball.pocketed) continue
            readTransform(ball)
            velocity.set(ball.body.linearVelocity)
            val linearSpeed2 = velocity.len2()
            val angularSpeed2 = ball.body.angularVelocity.len2()
            if (linearSpeed2 > PhysicsTuning.LINEAR_SLEEP_SPEED * PhysicsTuning.LINEAR_SLEEP_SPEED ||
                angularSpeed2 > PhysicsTuning.ANGULAR_SLEEP_SPEED * PhysicsTuning.ANGULAR_SLEEP_SPEED) moving = true
            restStability.observe(ball.id, ball.position, ball.rotation, linearSpeed2, angularSpeed2)
            captureIfDropped(ball)
        }
        restStability.finishStep(STEP)
        quietSeconds = if (moving) 0f else quietSeconds + STEP
    }

    private fun captureIfDropped(ball: Ball) {
        val pocket = PocketGeometry.pockets.firstOrNull { it.containsDrop(ball.point) }
        if (ball.position.y < PocketGeometry.CAPTURE_DEPTH && pocket != null) {
            capture(ball)
            pocketDrops(PocketDrop(ball.id, pocket.id))
        } else if (kotlin.math.abs(ball.position.x) > 0.80f || kotlin.math.abs(ball.position.z) > 1.36f || ball.position.y < -0.40f) {
            com.badlogic.gdx.Gdx.app?.error("PoolPhysics", "Ball ${ball.id} escaped table at ${ball.position}")
            capture(ball)
        }
    }

    private fun updateSettling() {
        if (wasSettled || (quietSeconds < PhysicsTuning.SETTLED_SECONDS && !restStability.settled)) return
        balls.filter { !it.pocketed }.forEach {
            it.body.linearVelocity = Vector3.Zero
            it.body.angularVelocity = Vector3.Zero
        }
        wasSettled = true
        events(PhysicsEvent.Settled)
    }

    private fun collectImpactContacts() {
        for (index in 0 until dispatcher.numManifolds) {
            val manifold = dispatcher.getManifoldByIndexInternal(index)
            val first = manifold.body0.userValue
            val second = manifold.body1.userValue
            for (contactIndex in 0 until manifold.numContacts) {
                val point = manifold.getContactPoint(contactIndex)
                // CCD can resolve an impact while a small positive separation remains.
                if (point.distance <= 0f || point.appliedImpulse > 0f) {
                    contactImpacts.contact(first, second, point.appliedImpulse)
                }
            }
        }
        contactImpacts.finishStep()
    }

    private fun capture(ball: Ball) {
        if (ball.pocketed) return
        ball.pocketed = true
        world.removeRigidBody(ball.body)
        events(PhysicsEvent.Pocketed(ball.id))
    }

    private fun readTransform(ball: Ball) {
        ball.body.getWorldTransform(transform)
        transform.getTranslation(ball.position)
        transform.getRotation(ball.rotation)
    }

    private fun disposeBall(ball: Ball) {
        if (!ball.pocketed) world.removeRigidBody(ball.body)
        ball.body.dispose()
    }

    override fun dispose() {
        balls.forEach(::disposeBall)
        ballShape.dispose()
        table.dispose()
        world.dispose()
        solver.dispose()
        broadphase.dispose()
        dispatcher.dispose()
        configuration.dispose()
    }

    companion object { const val STEP = 1f / 120f }
}

/** Initialization precedes worker creation; subsequent worlds never reload native libraries. */
private object BulletRuntime {
    init { Bullet.init() }
    fun initialize() = Unit
}
