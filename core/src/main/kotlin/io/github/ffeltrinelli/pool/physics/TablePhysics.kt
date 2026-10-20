package io.github.ffeltrinelli.pool.physics

import com.badlogic.gdx.math.*
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.dynamics.*
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.*

/** Owns static table bodies, their shapes and backing meshes; the world outlives this owner. */
internal class TablePhysics(private val world: btDiscreteDynamicsWorld) : Disposable {
    private val shapes = mutableListOf<btCollisionShape>()
    private val fixedBodies = mutableListOf<btRigidBody>()
    private val triangleMeshes = mutableListOf<btTriangleMesh>()

    init { createSupport(); createCushions(); createLiners() }

    private fun createSupport() {
        // Keep the main playing area a single convex face: triangle seams disturb rolling friction.
        val core = PocketGeometry.rectangle(TableGeometry.HALF_WIDTH - 0.09f, TableGeometry.HALF_LENGTH - 0.10f)
        val support = btCompoundShape()
        val supportPieces = listOf(core) + PocketGeometry.support.flatMap { PocketGeometry.subtract(it, core) }
        supportPieces.forEach { polygon ->
            val hull = btConvexHullShape()
            polygon.forEach { p -> hull.addPoint(vector(p, 0f), false); hull.addPoint(vector(p, -0.04f), false) }
            hull.recalcLocalAabb()
            hull.margin = 0f
            shapes.add(hull)
            support.addChildShape(Matrix4(), hull)
        }
        addStatic(support, CollisionIds.CLOTH, PhysicsTuning.CLOTH_FRICTION, 0f)
        val mesh = btTriangleMesh()
        triangleMeshes.add(mesh)
        PocketGeometry.pockets.forEach { pocket ->
            pocket.lipRings.zipWithNext().forEach { (outer, inner) ->
                for (i in outer.outline.indices) {
                    val j = (i + 1) % outer.outline.size
                    mesh.addTriangle(vector(outer.outline[i], outer.height), vector(inner.outline[i], inner.height), vector(inner.outline[j], inner.height), true)
                    mesh.addTriangle(vector(outer.outline[i], outer.height), vector(inner.outline[j], inner.height), vector(outer.outline[j], outer.height), true)
                }
            }
        }
        addStatic(btBvhTriangleMeshShape(mesh, true), CollisionIds.INTERIOR, PhysicsTuning.CLOTH_FRICTION, 0f)
    }

    private fun createCushions() {
        PocketGeometry.cushions.forEach { cushion ->
            val shape = btConvexHullShape()
            cushion.outline.forEach { point ->
                shape.addPoint(vector(point, 0f), false)
                shape.addPoint(vector(point, PocketGeometry.RAIL_HEIGHT), false)
            }
            shape.recalcLocalAabb()
            shape.margin = 0f
            addStatic(shape, -1 - cushion.id, PhysicsTuning.CUSHION_FRICTION, PhysicsTuning.CUSHION_RESTITUTION)
        }
    }

    private fun createLiners() {
        // A recessed, low-restitution liner contains fast entries without attracting balls.
        val linerMesh = btTriangleMesh()
        triangleMeshes.add(linerMesh)
        PocketGeometry.pockets.forEach { pocket ->
            pocket.backLiner.forEach { (a, b) ->
                val topA = pocket.linerTop(a); val topB = pocket.linerTop(b)
                linerMesh.addTriangle(vector(topA, PocketGeometry.RAIL_HEIGHT), vector(a, PocketGeometry.LINER_SHOULDER), vector(b, PocketGeometry.LINER_SHOULDER), true)
                linerMesh.addTriangle(vector(topA, PocketGeometry.RAIL_HEIGHT), vector(b, PocketGeometry.LINER_SHOULDER), vector(topB, PocketGeometry.RAIL_HEIGHT), true)
            }
            for (i in pocket.innerOpening.indices) {
                val a = pocket.innerOpening[i]
                val b = pocket.innerOpening[(i + 1) % pocket.innerOpening.size]
                linerMesh.addTriangle(vector(a, PocketGeometry.LINER_SHOULDER), vector(a, PocketGeometry.LINER_BOTTOM), vector(b, PocketGeometry.LINER_BOTTOM), true)
                linerMesh.addTriangle(vector(a, PocketGeometry.LINER_SHOULDER), vector(b, PocketGeometry.LINER_BOTTOM), vector(b, PocketGeometry.LINER_SHOULDER), true)
            }
        }
        addStatic(btBvhTriangleMeshShape(linerMesh, true), CollisionIds.INTERIOR, PhysicsTuning.LINER_FRICTION, PhysicsTuning.LINER_RESTITUTION)
    }

    private fun vector(point: Point, y: Float) = Vector3(point.x, y, point.z)

    private fun addStatic(shape: btCollisionShape, identity: Int, friction: Float, restitution: Float) {
        shapes.add(shape)
        val info = btRigidBody.btRigidBodyConstructionInfo(0f, null, shape, Vector3.Zero)
        val body = btRigidBody(info)
        info.dispose()
        body.worldTransform = Matrix4()
        body.userValue = identity
        body.friction = friction
        body.restitution = restitution
        fixedBodies.add(body)
        world.addRigidBody(body)
    }

    override fun dispose() {
        fixedBodies.forEach { world.removeRigidBody(it); it.dispose() }
        shapes.asReversed().forEach { it.dispose() }
        triangleMeshes.forEach { it.dispose() }
    }
}
