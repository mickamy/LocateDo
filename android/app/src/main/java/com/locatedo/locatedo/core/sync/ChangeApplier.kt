package com.locatedo.locatedo.core.sync

import android.util.Log
import com.locatedo.category.v1.Category
import com.locatedo.household.v1.Membership
import com.locatedo.household.v1.Role
import com.locatedo.locatedo.core.common.v7Instant
import com.locatedo.locatedo.core.database.CategoryDao
import com.locatedo.locatedo.core.database.CategoryEntity
import com.locatedo.locatedo.core.database.MembershipDao
import com.locatedo.locatedo.core.database.MembershipEntity
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.database.TodoDao
import com.locatedo.locatedo.core.database.TodoEntity
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.place.v1.Place
import com.locatedo.sync.v1.Change
import com.locatedo.sync.v1.Deletion
import com.locatedo.sync.v1.EntityKind
import com.locatedo.todo.v1.Todo
import java.util.UUID
import javax.inject.Inject

// Writes one pull into Room; the caller holds the transaction. Rows arrive in any order, so parents go in before
// children and a child whose parent is unknown is skipped rather than violating a foreign key.
class ChangeApplier @Inject constructor(
    private val categoryDao: CategoryDao,
    private val placeDao: PlaceDao,
    private val todoDao: TodoDao,
    private val membershipDao: MembershipDao,
) {
    private data class Key(val kind: EntityKind, val id: String)

    suspend fun apply(changes: List<Change>, reset: Boolean) {
        if (reset) {
            todoDao.deleteAll()
            placeDao.deleteAll()
            categoryDao.deleteAll()
            membershipDao.deleteAll()
        }
        val latest = latest(changes)
        val categoryIds = categoryDao.ids().toMutableSet()
        val placeIds = placeDao.ids().toMutableSet()
        for (change in latest.filter { it.kindCase == Change.KindCase.MEMBERSHIP }) {
            upsert(change.membership)
        }
        for (change in latest.filter { it.kindCase == Change.KindCase.CATEGORY }) {
            upsert(change.category)?.let { categoryIds += it }
        }
        for (change in latest.filter { it.kindCase == Change.KindCase.PLACE }) {
            upsert(change.place, categoryIds)?.let { placeIds += it }
        }
        for (change in latest.filter { it.kindCase == Change.KindCase.TODO }) {
            upsert(change.todo, placeIds)
        }
        for (change in latest.filter { it.kindCase == Change.KindCase.DELETION }) {
            delete(change.deletion)
        }
    }

    private fun latest(changes: List<Change>): List<Change> {
        val latest = LinkedHashMap<Key, Pair<Long, Change>>()
        for (change in changes) {
            val (key, version) = identity(change) ?: continue
            val existing = latest[key]
            if (existing != null && existing.first >= version) {
                continue
            }
            latest[key] = version to change
        }
        return latest.values.map { it.second }
    }

    private fun identity(change: Change): Pair<Key, Long>? = when (change.kindCase) {
        Change.KindCase.MEMBERSHIP -> Key(EntityKind.ENTITY_KIND_MEMBERSHIP, change.membership.userId) to change.membership.version
        Change.KindCase.CATEGORY -> Key(EntityKind.ENTITY_KIND_CATEGORY, change.category.id) to change.category.version
        Change.KindCase.PLACE -> Key(EntityKind.ENTITY_KIND_PLACE, change.place.id) to change.place.version
        Change.KindCase.TODO -> Key(EntityKind.ENTITY_KIND_TODO, change.todo.id) to change.todo.version
        Change.KindCase.DELETION -> Key(change.deletion.kind, change.deletion.id) to change.deletion.version
        Change.KindCase.KIND_NOT_SET, null -> null
    }

    private suspend fun upsert(proto: Membership) {
        val userId = uuid(proto.userId, "membership") ?: return
        membershipDao.upsert(
            MembershipEntity(
                userId = userId,
                role = (if (proto.role == Role.ROLE_OWNER) MemberRole.OWNER else MemberRole.MEMBER).key,
                displayName = proto.displayName,
                joinedAt = proto.joinedAt.toInstant().toEpochMilli(),
                updatedAt = proto.updatedAt.toInstant().toEpochMilli(),
            ),
        )
    }

    private suspend fun upsert(proto: Category): String? {
        val id = uuid(proto.id, "category") ?: return null
        categoryDao.upsert(
            CategoryEntity(
                id = id,
                builtin = builtin(proto.builtin)?.key,
                name = if (proto.hasName()) proto.name else null,
                icon = proto.icon,
                color = proto.color,
                sortOrder = proto.sortOrder,
                updatedAt = proto.updatedAt.toInstant().toEpochMilli(),
            ),
        )
        return id
    }

    // lastNotifiedAt and createdAt are the device's own; a new row dates from its id.
    private suspend fun upsert(proto: Place, categoryIds: Set<String>): String? {
        val id = uuid(proto.id, "place") ?: return null
        val existing = placeDao.get(id)
        val categoryId = if (proto.hasCategoryId()) uuid(proto.categoryId, "place category") else null
        val updatedAt = proto.updatedAt.toInstant().toEpochMilli()
        placeDao.upsert(
            PlaceEntity(
                id = id,
                name = proto.name,
                latitude = proto.lat,
                longitude = proto.lng,
                radiusMeters = proto.radiusM.toDouble(),
                categoryId = categoryId?.takeIf { it in categoryIds },
                sortOrder = proto.sortOrder,
                lastNotifiedAt = existing?.lastNotifiedAt,
                createdAt = existing?.createdAt ?: createdAt(id, updatedAt),
                updatedAt = updatedAt,
            ),
        )
        return id
    }

    private suspend fun upsert(proto: Todo, placeIds: Set<String>) {
        val id = uuid(proto.id, "todo") ?: return
        val placeId = uuid(proto.placeId, "todo place")
        if (placeId == null || placeId !in placeIds) {
            Log.e(TAG, "Skipping todo ${proto.id} for a missing place")
            return
        }
        val existing = todoDao.get(id)
        val updatedAt = proto.updatedAt.toInstant().toEpochMilli()
        todoDao.upsert(
            TodoEntity(
                id = id,
                title = proto.title,
                placeId = placeId,
                assigneeId = if (proto.hasAssigneeId()) uuid(proto.assigneeId, "assignee") else null,
                creatorId = if (proto.hasCreatorId()) uuid(proto.creatorId, "creator") else null,
                completedAt = if (proto.hasCompletedAt()) proto.completedAt.toInstant().toEpochMilli() else null,
                completerId = if (proto.hasCompleterId()) uuid(proto.completerId, "completer") else null,
                createdAt = existing?.createdAt ?: createdAt(id, updatedAt),
                updatedAt = updatedAt,
            ),
        )
    }

    private suspend fun delete(deletion: Deletion) {
        val id = uuid(deletion.id, "deletion") ?: return
        when (deletion.kind) {
            EntityKind.ENTITY_KIND_MEMBERSHIP -> membershipDao.delete(id)
            EntityKind.ENTITY_KIND_CATEGORY -> categoryDao.delete(id)
            EntityKind.ENTITY_KIND_PLACE -> placeDao.delete(id)
            EntityKind.ENTITY_KIND_TODO -> todoDao.delete(listOf(id))
            EntityKind.ENTITY_KIND_UNSPECIFIED, EntityKind.UNRECOGNIZED, null -> Unit
        }
    }

    private fun createdAt(id: String, fallback: Long): Long = UUID.fromString(id).v7Instant()?.toEpochMilli() ?: fallback

    // Ids are stored the way UUID prints them, so the server's spelling cannot create a second row.
    private fun uuid(raw: String, kind: String): String? {
        val parsed = runCatching { UUID.fromString(raw) }.getOrNull()
        if (parsed == null) {
            Log.e(TAG, "Skipping a $kind with an invalid id $raw")
            return null
        }
        return parsed.toString()
    }

    private fun builtin(proto: com.locatedo.category.v1.BuiltinCategory): BuiltinCategory? = when (proto) {
        com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_SHOPPING -> BuiltinCategory.SHOPPING
        com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_WORK -> BuiltinCategory.WORK
        com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_LIFE -> BuiltinCategory.LIFE
        com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_OTHER -> BuiltinCategory.OTHER
        com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_UNSPECIFIED,
        com.locatedo.category.v1.BuiltinCategory.UNRECOGNIZED,
        -> null
    }

    private companion object {
        const val TAG = "Sync"
    }
}
