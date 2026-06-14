package com.valhalla.valhalla

import android.content.Context
import com.osrm.api.models.RouteResponse as OsrmRouteResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.valhalla.api.models.MatrixRequest
import com.valhalla.api.models.MatrixResponse
import com.valhalla.api.models.RouteRequest
import com.valhalla.api.models.RouteResponse
import com.valhalla.api.models.TraceAttributesRequest
import com.valhalla.api.models.TraceAttributesResponse
import com.valhalla.config.models.ValhallaConfig
import com.valhalla.valhalla.config.ValhallaConfigManager

/**
 * Main entry point for the Valhalla routing engine on Android.
 *
 * This class provides a Kotlin interface to the native Valhalla C++ routing engine. It handles
 * configuration management, JSON serialization, and routing requests.
 *
 * @param context The Android context used for file system operations and configuration management.
 * @param config The Valhalla configuration specifying tile locations and routing options.
 * @param valhallaConfigManager Manages the Valhalla configuration file on the device. Defaults to a
 *   new instance.
 * @param moshi JSON serialization adapter. Defaults to a Moshi instance with Kotlin reflection
 *   support.
 * @see ValhallaConfig
 * @see ValhallaConfigManager
 * @see RouteRequest
 * @see ValhallaResponse
 */
class Valhalla(
    context: Context,
    config: ValhallaConfig,
    valhallaConfigManager: ValhallaConfigManager = ValhallaConfigManager(context),
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {

  private val valhallaActor: ValhallaActorProviding

  init {
    valhallaConfigManager.writeConfig(config)
    valhallaActor = ValhallaActor(valhallaConfigManager.getAbsolutePath())
  }

  /**
   * Fetch a route from Valhalla.
   *
   * This function returns a sealed class with the format you designated. Currently this only
   * supports [ValhallaResponse.Json] and [ValhallaResponse.Osrm] formats.
   *
   * @param request The Valhalla routing request containing locations, costing model, and options.
   * @return The route response wrapped in a [ValhallaResponse] sealed class based on the requested
   *   format.
   * @throws ValhallaException.Internal if the Valhalla engine returns an error response.
   * @throws ValhallaException.InvalidError if an error response cannot be parsed.
   * @throws ValhallaException.InvalidResponse if the response JSON cannot be parsed.
   * @throws ValhallaException.NotSupported if an unsupported format (GPX or PBF) is requested.
   * @see RouteRequest
   * @see ValhallaResponse
   * @see RouteRequest.Format
   */
  fun route(request: RouteRequest): ValhallaResponse {
    val encodedRequest = moshi.adapter(RouteRequest::class.java).toJson(request)
    val rawResponse = valhallaActor.route(encodedRequest)

    // Check for error response in Valhalla format.
    // OSRM has a code and message like the valhalla error, but it's not the same format.
    // If the response contains routes, it's a valid OSRM response.
    if (rawResponse.contains("code") and !rawResponse.contains("routes")) {
      val error = moshi.adapter(ErrorResponse::class.java).fromJson(rawResponse)
      error?.let { throw ValhallaException.Internal(it) }
      throw ValhallaException.InvalidError()
    }

    return when (request.format) {
      RouteRequest.Format.gpx -> throw ValhallaException.NotSupported()
      RouteRequest.Format.osrm -> {
        val osrmResponse =
            moshi.adapter(OsrmRouteResponse::class.java).fromJson(rawResponse)
                ?: throw ValhallaException.InvalidResponse()
        ValhallaResponse.Osrm(osrmResponse)
      }

      RouteRequest.Format.pbf -> throw ValhallaException.NotSupported()
      // else includes default valhalla: RouteRequest.Format.json
      else -> {
        val valhallaResponse =
            moshi.adapter(RouteResponse::class.java).fromJson(rawResponse)
                ?: throw ValhallaException.InvalidResponse()
        ValhallaResponse.Json(valhallaResponse)
      }
    }
  }

  /**
   * Map-match an encoded GPS trace onto the graph and return per-edge attributes (surface, length,
   * speed, road class, etc.).
   *
   * Powers offline surface-type analysis of recorded tracks. The response is always native Valhalla
   * JSON; there is no OSRM variant for trace_attributes.
   *
   * @param request The trace_attributes request: a shape (or encoded polyline), a costing model, a
   *   shape_match strategy, and optional filters selecting which edge attributes to return.
   * @return The deserialized trace_attributes response, whose [edges][TraceAttributesResponse.edges]
   *   each carry a [surface][TraceAttributesRequest] type.
   * @throws ValhallaException.Internal if the Valhalla engine returns an error response.
   * @throws ValhallaException.InvalidError if an error response cannot be parsed.
   * @throws ValhallaException.InvalidResponse if the response JSON cannot be parsed.
   * @see TraceAttributesRequest
   * @see TraceAttributesResponse
   */
  fun traceAttributes(request: TraceAttributesRequest): TraceAttributesResponse {
    val encodedRequest = moshi.adapter(TraceAttributesRequest::class.java).toJson(request)
    val rawResponse = valhallaActor.traceAttributes(encodedRequest)
    checkError(rawResponse, successMarker = "edges")
    return moshi.adapter(TraceAttributesResponse::class.java).fromJson(rawResponse)
        ?: throw ValhallaException.InvalidResponse()
  }

  /** Raw-string trace_attributes for callers that build the request JSON themselves. */
  fun traceAttributes(rawRequest: String): String {
    return valhallaActor.traceAttributes(rawRequest)
  }

  /**
   * Compute a time/distance matrix between the given sources and targets.
   *
   * Powers offline multi-point routing and optimal-stop ordering: callers pair this with a TSP
   * heuristic over the returned [sourcesToTargets][MatrixResponse.sourcesToTargets] matrix.
   *
   * @param request The matrix request: sources, targets, and a costing model.
   * @return The deserialized matrix response. Each cell is a
   *   [MatrixDistance] (time in seconds, distance in the requested units); a cell may be absent
   *   when no path connects that source/target pair.
   * @throws ValhallaException.Internal if the Valhalla engine returns an error response.
   * @throws ValhallaException.InvalidError if an error response cannot be parsed.
   * @throws ValhallaException.InvalidResponse if the response JSON cannot be parsed.
   * @see MatrixRequest
   * @see MatrixResponse
   */
  fun matrix(request: MatrixRequest): MatrixResponse {
    val encodedRequest = moshi.adapter(MatrixRequest::class.java).toJson(request)
    val rawResponse = valhallaActor.matrix(encodedRequest)
    checkError(rawResponse, successMarker = "sources_to_targets")
    return moshi.adapter(MatrixResponse::class.java).fromJson(rawResponse)
        ?: throw ValhallaException.InvalidResponse()
  }

  /** Raw-string matrix for callers that build the request JSON themselves. */
  fun matrix(rawRequest: String): String {
    return valhallaActor.matrix(rawRequest)
  }

  /**
   * Detects a Valhalla error response (`{code, message}`) that lacks the success marker field.
   *
   * Valhalla error bodies carry a `code` field; valid trace_attributes/matrix responses instead
   * carry a top-level success marker (`edges` / `sources_to_targets`), so its absence plus a `code`
   * field flags an error.
   */
  private fun checkError(rawResponse: String, successMarker: String) {
    if (rawResponse.contains("code") && !rawResponse.contains(successMarker)) {
      val error = moshi.adapter(ErrorResponse::class.java).fromJson(rawResponse)
      error?.let { throw ValhallaException.Internal(it) }
      throw ValhallaException.InvalidError()
    }
  }
}
