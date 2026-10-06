package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.MembershipDao
import com.locatedo.locatedo.core.database.MembershipEntity
import com.locatedo.locatedo.core.model.Membership
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The household's members as the last pull left them; sync writes them, the sharing screen reads them.
interface MembershipRepository {
    fun observeAll(): Flow<List<Membership>>
}

@Singleton
class RoomMembershipRepository @Inject constructor(private val dao: MembershipDao) : MembershipRepository {
    override fun observeAll(): Flow<List<Membership>> =
        dao.observeAll().map { memberships -> memberships.map(MembershipEntity::asModel) }
}
