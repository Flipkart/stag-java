package com.vimeo.stag.processor

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vimeo.sample_java_model.NullFields
import com.vimeo.sample_java_model.stag.generated.Stag
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Integration tests for the compiler.
 *
 * Created by restainoa on 10/20/17.
 */
class StagProcessorIntegrationTest {

    @Test
    fun `NullFields type adapter is correct for null values`() {
        val nullFields = NullFields()
        val typeToken = TypeToken.get(nullFields.javaClass)
        val typeAdapter = Stag.Factory().create(Gson(), typeToken)
        val json = typeAdapter.toJson(nullFields)

        // Assert that we are getting the JSON we expect
        assertThat(json).isEqualTo("{}")
        assertThat(typeAdapter.fromJson(json)).isEqualTo(nullFields)

        // Assert that a null value emits null JSON
        assertThat(typeAdapter.toJson(null)).isEqualTo("null")
    }
}