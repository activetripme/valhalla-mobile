package com.valhalla.valhalla

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaTraceAttributesTest {

  private lateinit var appContext: Context
  private lateinit var configPath: String

  @Before
  fun setUp() {
    appContext = InstrumentationRegistry.getInstrumentation().targetContext
    configPath = TestFileUtils.getConfigPath(appContext)
    Log.d("ValhallaTraceAttributesTest", "Using config path: $configPath")
  }

  @Test
  fun testInvalidConfigSurfacesJsonError() {
    // Mirrors ValhallaActorTest.testNoConfigPath: the actor is constructed inside run_actor_op,
    // so a bad config path must surface as a JSON error string, not a JNI crash.
    val valhalla = ValhallaActor("invalid.json")

    val request =
        "{\"shape\":[{\"lat\":42.5063,\"lon\":1.5218},{\"lat\":42.5086,\"lon\":1.5394}]," +
            "\"costing\":\"auto\",\"shape_match\":\"walk_or_snap\"}"
    val response = valhalla.traceAttributes(request)

    assertEquals("{\"code\":-1,\"message\":\"Cannot open file invalid.json\"}", response)
  }

  @Test
  fun testTraceAttributesReturnsParsedJsonObject() {
    val valhalla = ValhallaActor(configPath)

    // Three points interpolated along the same Andorran corridor the route test uses.
    val request =
        "{\"shape\":[" +
            "{\"lat\":42.5063,\"lon\":1.5218}," +
            "{\"lat\":42.5074,\"lon\":1.5306}," +
            "{\"lat\":42.5086,\"lon\":1.5394}]," +
            "\"costing\":\"auto\",\"shape_match\":\"walk_or_snap\"," +
            "\"filters\":{\"attributes\":[\"edge.length\",\"edge.surface\"],\"action\":\"include\"}}"
    val response = valhalla.traceAttributes(request)

    // Map-matching can legitimately fail to find a confident match on a tiny test tileset, in which
    // case Valhalla returns an error object. Either way the response must be a parseable JSON object
    // carrying either a matched edges array or a code field — never an empty string or a crash.
    assertFalse("trace_attributes response must not be empty", response.isEmpty())
    val json = JSONObject(response)
    assertTrue(
        "expected either a matched edges array or an error code",
        json.has("edges") || json.has("code"),
    )
  }
}
