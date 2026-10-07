package com.locatedo.locatedo.ui.components

import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberNameTest {
    private val owner = member("Ann", MemberRole.OWNER)
    private val partner = member("Ben", MemberRole.MEMBER)
    private val checkedOff = todo("Milk", UUID.randomUUID()).copy(completedAt = fixedNow, completerId = partner.userId)

    @Test
    fun aSharedHouseholdNamesWhoCheckedItOff() {
        assertEquals(partner, completer(listOf(owner, partner), checkedOff))
    }

    @Test
    fun aHouseholdOfOneNamesNobody() {
        assertNull(completer(listOf(partner), checkedOff))
    }

    @Test
    fun anOpenTodoNamesNobody() {
        assertNull(completer(listOf(owner, partner), checkedOff.copy(completedAt = null)))
    }

    @Test
    fun someoneNoLongerInTheHouseholdOrUnknownGoesUnnamed() {
        assertNull(completer(listOf(owner, partner), checkedOff.copy(completerId = UUID.randomUUID())))
        assertNull(completer(listOf(owner, partner), checkedOff.copy(completerId = null)))
    }

    private fun member(name: String, role: MemberRole) =
        Membership(userId = UUID.randomUUID(), role = role, displayName = name, joinedAt = fixedNow, updatedAt = fixedNow)
}
