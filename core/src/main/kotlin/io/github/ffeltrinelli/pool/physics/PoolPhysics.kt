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
    private val impacts: ((Impact) -> Unit)? = null,
    private val pocketDrops: (PocketDrop) -> Unit = {},
    private val afterStep: () -> Unit = {}
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
    private val contactImpacts = impacts?.let(::ContactImpacts)
    private val transform = Matrix4()
    private val velocity = Vector3()
    private var accumulator = 0f
    private val restStability = RestStability()
    private var quietSeconds = 0f
    private var wasSettled = true
    private var ruleContacts = mutableSetOf<Int>()
    private var nextRuleContacts = mutableSetOf<Int>()
    private val contactPosition = Vector3()
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
        contactImpacts?.reset()
        ruleContacts.clear()
        nextRuleContacts.clear()
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

    fun placeBall(id: Int, point: Point) {
        balls.firstOrNull { it.id == id }?.let {
            disposeBall(it)
            ownedBalls.remove(it)
        }
        contactImpacts?.reset()
        ruleContacts.clear()
        nextRuleContacts.clear()
        addBall(id, point)
        interpolation = 0f
    }

    fun spotBall(id: Int) {
        val objects = balls.filter { it.id != id && !it.pocketed }.map { it.point }
        placeBall(id, BallPlacement.spot(objects))
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
        impacts?.invoke(Impact(ImpactKind.CUE, shot.impulse))
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
        afterStep()
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
            capture(ball, offTable = true)
        }
    }

    private fun updateSettling() {
        if (wasSettled || (quietSeconds < PhysicsTuning.SETTLED_SECONDS && !restStability.settled)) return
        balls.filter { !it.pocketed }.forEach {
            it.body.linearVelocity = Vector3.Zero
            it.body.angularVelocity = Vector3.Zero
            it.body.activationState = Collision.ISLAND_SLEEPING
        }
        wasSettled = true
        events(PhysicsEvent.Settled)
    }

    private fun collectImpactContacts() {
        val touching = nextRuleContacts
        touching.clear()
        var targets: MutableSet<Int>? = null
        var rails: MutableSet<Int>? = null
        for (index in 0 until dispatcher.numManifolds) {
            val manifold = dispatcher.getManifoldByIndexInternal(index)
            val first = manifold.body0.userValue
            val second = manifold.body1.userValue
            val cueTarget = when {
                first == 0 && second in 1..15 -> second
                second == 0 && first in 1..15 -> first
                else -> null
            }
            val railBall = when {
                first in CollisionIds.BALLS && second in CollisionIds.CUSHIONS -> first
                second in CollisionIds.BALLS && first in CollisionIds.CUSHIONS -> second
                else -> null
            }
            val audible = (first in CollisionIds.BALLS && second in CollisionIds.BALLS) || railBall != null
            // Cloth/support manifolds contribute neither rule contacts nor impact sounds.
            if (cueTarget == null && railBall == null && (contactImpacts == null || !audible)) continue
            val pair = (minOf(first, second) shl 16) or (maxOf(first, second) and 0xffff)
            for (contactIndex in 0 until manifold.numContacts) {
                val point = manifold.getContactPoint(contactIndex)
                // CCD can resolve an impact while a small positive separation remains.
                if (point.distance <= 0f || point.appliedImpulse > 0f) {
                    contactImpacts?.contact(first, second, point.appliedImpulse)
                    if (cueTarget == null && railBall == null) continue
                    touching.add(pair)
                    if (pair !in ruleContacts && point.appliedImpulse > 0.000001f) {
                        if (cueTarget != null) {
                            val contacts = targets ?: mutableSetOf<Int>().also { targets = it }
                            contacts.add(cueTarget)
                        }
                        if (railBall != null) {
                            val contacts = rails ?: mutableSetOf<Int>().also { rails = it }
                            contacts.add(railBall)
                        }
                    }
                }
            }
        }
        nextRuleContacts = ruleContacts
        ruleContacts = touching
        contactImpacts?.finishStep()
        val cue = balls.firstOrNull { it.id == 0 && !it.pocketed }
        val cueZ = cue?.body?.worldTransform?.getTranslation(contactPosition)?.z
        val crossed = cue != null && cueZ != null && cue.previousPosition.z > TableGeometry.HEAD_STRING && cueZ <= TableGeometry.HEAD_STRING
        if (targets != null || rails != null || crossed) {
            val targetIds = targets.orEmpty()
            val behind = targetIds.all { id -> balls.first { it.id == id }.body.worldTransform.getTranslation(contactPosition).z > TableGeometry.HEAD_STRING }
            events(PhysicsEvent.Contacts(targetIds, rails.orEmpty(), crossed, behind))
        }
    }

    private fun capture(ball: Ball, offTable: Boolean = false) {
        if (ball.pocketed) return
        ball.pocketed = true
        world.removeRigidBody(ball.body)
        events(if (offTable) PhysicsEvent.OffTable(ball.id) else PhysicsEvent.Pocketed(ball.id))
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
