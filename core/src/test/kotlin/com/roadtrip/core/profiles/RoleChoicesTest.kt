package com.roadtrip.core.profiles

import com.roadtrip.core.common.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleChoicesTest {
    // covers: AND-013
    @Test
    fun `role options are Kid then Parent with stable labels AND-013`() {
        assertEquals(
            listOf(Role.KID to "Kid", Role.PARENT to "Parent"),
            RoleChoices.all.map { it.role to it.label },
        )
    }
}
