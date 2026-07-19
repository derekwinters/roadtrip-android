package com.roadtrip.core.api

import com.roadtrip.core.testing.GeocodeFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Documents the exact failure mode behind #85 and prevents a silent reintroduction of an object
 * envelope: the backend body is a bare JSON array (GSR-002 / ANDMAP-008), so decoding it with an
 * object serializer throws, while a list serializer succeeds. This is the decode direction the
 * client must use.
 */
class GeocodeDecodingTest {
    @Test
    fun `the bare-array body decodes with a list serializer but not an object serializer ANDMAP-008`() {
        // Object-shaped decode (the old {results:[…]} envelope assumption) fails on an array body.
        assertFailsWith<SerializationException> {
            RoadtripJson.decodeFromString(JsonObject.serializer(), GeocodeFixtures.SINGLE_MATCH_JSON)
        }

        // The list serializer the client actually uses parses it cleanly.
        val matches = RoadtripJson.decodeFromString(
            ListSerializer(GeocodeMatch.serializer()),
            GeocodeFixtures.SINGLE_MATCH_JSON,
        )
        assertEquals(listOf(GeocodeFixtures.SINGLE_MATCH), matches)
    }
}
