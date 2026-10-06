package com.locatedo.locatedo.core.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    private val token = "example_Invite-Link"

    @Test
    fun buildsTheLinkOnTheOwnedDomain() {
        assertEquals("https://locatedo.com/i/$token", InviteLink.url(token))
    }

    @Test
    fun readsTheTokenFromALinkOrOnItsOwn() {
        assertEquals(token, InviteLink.token("https://locatedo.com/i/$token"))
        assertEquals(token, InviteLink.token("  https://www.locatedo.com/i/$token\n"))
        assertEquals(token, InviteLink.token(token))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(InviteLink.token("https://example.com/i/$token"))
        assertNull(InviteLink.token("https://locatedo.com/privacy"))
        assertNull(InviteLink.token("https://locatedo.com/i/$token/extra"))
        assertNull(InviteLink.token("short"))
        assertNull(InviteLink.token("has spaces in the middle of it"))
        assertNull(InviteLink.token(""))
    }
}
