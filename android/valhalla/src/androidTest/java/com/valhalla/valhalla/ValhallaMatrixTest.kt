package com.valhalla.valhalla

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaMatrixTest {

  private lateinit var appContext: Context
  private lateinit var configPath: String

  @Before
  fun setUp() {
    appContext = InstrumentationRegistry.getInstrumentation().targetContext
    configPath = TestFileUtils.getConfigPath(appContext)
    Log.d("ValhallaMatrixTest", "Using config path: $configPath")
  }

  @Test
  fun testInvalidConfigSurfacesJsonError() {
    val valhalla = ValhallaActor("invalid.json")

    val request =
        "{\"sources\":[{\"lat\":42.5063,\"lon\":1.5218}]," +
            "\"targets\":[{\"lat\":42.5086,\"lon\":1.5394}],\"costing\":\"auto\",\"units\":\"km\"}"
    val response = valhalla.matrix(request)

    assertEquals("{\"code\":-1,\"message\":\"Cannot open file invalid.json\"}", response)
  }

  @Test
  fun testMatrixReturnsSourcesToTargetsGrid() {
    val valhalla = ValhallaActor(configPath)

    // Same Andorran corridor the route test routes successfully, so a one-to-one matrix must resolve.
    val request =
        "{\"sources\":[{\"lat\":42.5063,\"lon\":1.5218}]," +
            "\"targets\":[{\"lat\":42.5086,\"lon\":1.5394}],\"costing\":\"auto\",\"units\":\"km\"}"
    val response = valhalla.matrix(request)

    assertFalse("matrix response must not be empty", response.isEmpty())
    val json = JSONObject(response)
    // The verbose form (default) emits sources_to_targets as an array of rows of cell objects.
    assertNotNull("expected sources_to_targets in matrix response", json.opt("sources_to_targets"))
    val grid = json.optJSONArray("sources_to_targets")
    if (grid != null) {
      assertEquals("one source row", 1, grid.length())
      val row: JSONArray = grid.getJSONArray(0)
      assertEquals("one target cell", 1, row.length())
    }
  }
}
