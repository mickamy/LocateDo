package com.locatedo.locatedo.core.sync

import com.locatedo.category.v1.BuiltinCategory
import com.locatedo.category.v1.category
import com.locatedo.household.v1.Plan
import com.locatedo.household.v1.Role
import com.locatedo.household.v1.household
import com.locatedo.household.v1.membership
import com.locatedo.place.v1.place
import com.locatedo.sync.v1.Change
import com.locatedo.sync.v1.EntityKind
import com.locatedo.sync.v1.PullResponse
import com.locatedo.sync.v1.change
import com.locatedo.sync.v1.deletion
import com.locatedo.sync.v1.pullResponse
import com.locatedo.todo.v1.todo
import java.time.Instant
import java.util.UUID

val created: Instant = Instant.ofEpochSecond(1_700_000_000)
val updated: Instant = Instant.ofEpochSecond(1_800_000_000)

fun categoryChange(
    id: UUID,
    version: Long,
    builtin: BuiltinCategory = BuiltinCategory.BUILTIN_CATEGORY_UNSPECIFIED,
    name: String? = null,
): Change = change {
    category = category {
        this.id = id.toString()
        this.builtin = builtin
        name?.let { this.name = it }
        icon = "cart"
        color = "green"
        updatedAt = updated.toTimestamp()
        this.version = version
    }
}

fun placeChange(id: UUID, version: Long, categoryId: UUID? = null, name: String = "Store"): Change = change {
    place = place {
        this.id = id.toString()
        this.name = name
        lat = 35.5
        lng = 139.25
        radiusM = 150
        categoryId?.let { this.categoryId = it.toString() }
        updatedAt = updated.toTimestamp()
        this.version = version
    }
}

fun todoChange(
    id: UUID,
    placeId: UUID,
    version: Long,
    title: String = "Milk",
    completedAt: Instant? = null,
    creatorId: UUID? = null,
    completerId: UUID? = null,
): Change = change {
    todo = todo {
        this.id = id.toString()
        this.placeId = placeId.toString()
        this.title = title
        completedAt?.let { this.completedAt = it.toTimestamp() }
        creatorId?.let { this.creatorId = it.toString() }
        completerId?.let { this.completerId = it.toString() }
        updatedAt = updated.toTimestamp()
        this.version = version
    }
}

fun membershipChange(userId: UUID, role: Role, name: String, version: Long): Change = change {
    membership = membership {
        this.userId = userId.toString()
        this.role = role
        displayName = name
        joinedAt = created.toTimestamp()
        updatedAt = updated.toTimestamp()
        this.version = version
    }
}

fun deletionChange(kind: EntityKind, id: UUID, version: Long): Change = change {
    deletion = deletion {
        this.kind = kind
        this.id = id.toString()
        this.version = version
    }
}

fun pullPage(
    changes: List<Change>,
    cursor: Long,
    hasMore: Boolean = false,
    reset: Boolean = false,
    plan: Plan = Plan.PLAN_FREE,
): PullResponse = pullResponse {
    this.changes += changes
    this.cursor = cursor
    this.hasMore = hasMore
    this.reset = reset
    this.household = household { this.plan = plan }
}
