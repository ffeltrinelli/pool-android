package io.github.ffeltrinelli.pool.physics

import com.badlogic.gdx.math.*
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.dynamics.*
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.*
import kotlin.math.min

class Ball internal constructor(val id: Int, val body: btRigidBody) {
    val previousPosition = Vector3()
    val position = Vector3()
    val previousRotation = Quaternion()
    val rotation = Quaternion()
    var pocketed = false
        internal set
    var sinkSeconds = 0f
        internal set
    val point: Point get() = Point(position.x, position.z)
}

/** Owns every Bullet allocation. All calls and event delivery happen on the game thread. */
class PoolPhysics(private val events: (PhysicsEvent) -> Unit = {}) : Disposable {
    private val configuration: btDefaultCollisionConfiguration
    private val dispatcher: btCollisionDispatcher
    private val broadphase: btDbvtBroadphase
    private val solver: btSequentialImpulseConstraintSolver
    private val world: btDiscreteDynamicsWorld
    private val shapes = mutableListOf<btCollisionShape>()
    private val ballShape: btSphereShape
    private val fixedBodies = mutableListOf<btRigidBody>()
    private val sensors = mutableListOf<btCollisionObject>()
    val balls = mutableListOf<Ball>()
    private val transform = Matrix4()
    private val velocity = Vector3()
    private var accumulator = 0f
    private var quietSeconds = 0f
    private var wasSettled = true
    var interpolation = 0f
        private set

    init {
        Bullet.init()
        configuration = btDefaultCollisionConfiguration()
        dispatcher = btCollisionDispatcher(configuration)
        broadphase = btDbvtBroadphase()
        solver = btSequentialImpulseConstraintSolver()
        world = btDiscreteDynamicsWorld(dispatcher, broadphase, solver, configuration)
        world.gravity = Vector3.Zero
        TableGeometry.rails.forEach { rail ->
            val shape = btBoxShape(Vector3(rail.halfWidth, 0.07f, rail.halfLength))
            shapes.add(shape)
            val body = rigidBody(shape, 0f, rail.center)
            body.restitution = 0.78f
            body.friction = 0.16f
            body.userValue = -1
            fixedBodies.add(body)
            world.addRigidBody(body)
        }
        TableGeometry.pockets.forEachIndexed { index, point ->
            val shape = btSphereShape(TableGeometry.POCKET_RADIUS)
            shapes.add(shape)
            val sensor = btCollisionObject()
            sensor.collisionShape = shape
            sensor.worldTransform = Matrix4().setToTranslation(point.x, TableGeometry.BALL_RADIUS, point.z)
            sensor.collisionFlags = sensor.collisionFlags or btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE
            sensor.userValue = 100 + index
            sensors.add(sensor)
            world.addCollisionObject(sensor)
        }
        ballShape = btSphereShape(TableGeometry.BALL_RADIUS)
        shapes.add(ballShape)
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
        balls.forEach { if (!it.pocketed) world.removeRigidBody(it.body); it.body.dispose() }
        balls.clear()
        layout.forEach { (id, point) ->
            val body = rigidBody(ballShape, TableGeometry.BALL_MASS, point)
            body.userValue = id
            body.linearFactor = Vector3(1f, 0f, 1f)
            body.restitution = 0.94f
            body.friction = 0.08f
            body.rollingFriction = 0.015f
            body.setDamping(0.48f, 0.55f)
            body.setSleepingThresholds(0.012f, 0.08f)
            body.ccdMotionThreshold = TableGeometry.BALL_RADIUS * 0.5f
            body.ccdSweptSphereRadius = TableGeometry.BALL_RADIUS * 0.8f
            world.addRigidBody(body)
            balls.add(Ball(id, body).also { readTransform(it); it.previousPosition.set(it.position); it.previousRotation.set(it.rotation) })
        }
        accumulator = 0f
        quietSeconds = 0f
        wasSettled = true
        interpolation = 0f
    }

    fun strike(shot: ShotCommand) {
        val cue = balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return
        cue.body.activate()
        val direction = shot.direction.normalized()
        cue.body.applyCentralImpulse(Vector3(direction.x * shot.impulse, 0f, direction.z * shot.impulse))
        quietSeconds = 0f
        wasSettled = false
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
        balls.forEach { it.previousPosition.set(it.position); it.previousRotation.set(it.rotation) }
        world.stepSimulation(STEP, 0)
        capturePocketContacts()
        var moving = false
        balls.forEach { ball ->
            if (ball.pocketed) {
                ball.sinkSeconds = min(1f, ball.sinkSeconds + STEP)
            } else {
                readTransform(ball)
                velocity.set(ball.body.linearVelocity)
                if (velocity.len2() > 0.000144f) moving = true
                // A safety net for rare escapes through pocket jaws; normal capture uses engine contacts.
                if (kotlin.math.abs(ball.position.x) > 0.76f || kotlin.math.abs(ball.position.z) > 1.32f) capture(ball)
            }
        }
        quietSeconds = if (moving) 0f else quietSeconds + STEP
        if (!wasSettled && quietSeconds >= 0.30f) {
            balls.filter { !it.pocketed }.forEach {
                it.body.linearVelocity = Vector3.Zero
                it.body.angularVelocity = Vector3.Zero
            }
            wasSettled = true
            events(PhysicsEvent.Settled)
        }
    }

    private fun capturePocketContacts() {
        val capturedIds = mutableSetOf<Int>()
        for (index in 0 until dispatcher.numManifolds) {
            val manifold = dispatcher.getManifoldByIndexInternal(index)
            val first = manifold.body0.userValue
            val second = manifold.body1.userValue
            val ballId = when {
                first >= 100 && second in 0..15 -> second
                second >= 100 && first in 0..15 -> first
                else -> continue
            }
            if ((0 until manifold.numContacts).any { manifold.getContactPoint(it).distance <= 0f }) capturedIds.add(ballId)
        }
        // Never remove bodies while iterating Bullet's contact manifolds.
        balls.filter { it.id in capturedIds && !it.pocketed }.forEach(::capture)
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

    override fun dispose() {
        balls.forEach { if (!it.pocketed) world.removeRigidBody(it.body); it.body.dispose() }
        sensors.forEach { world.removeCollisionObject(it); it.dispose() }
        fixedBodies.forEach { world.removeRigidBody(it); it.dispose() }
        shapes.forEach { it.dispose() }
        world.dispose()
        solver.dispose()
        broadphase.dispose()
        dispatcher.dispose()
        configuration.dispose()
    }

    companion object { const val STEP = 1f / 120f }
}
