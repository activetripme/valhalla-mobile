package com.valhalla.valhalla

internal interface ValhallaActorProviding {
  fun route(request: String): String

  fun traceAttributes(request: String): String

  fun matrix(request: String): String
}

/**
 * Access with raw unchecked strings to the Valhalla routing engine. This class is available, but
 * not recommended for general use.
 *
 * @property configPath
 */
internal class ValhallaActor(private val configPath: String) : ValhallaActorProviding {
  private val valhallaKotlin = ValhallaKotlin()

  /**
   * Run a route request to the Valhalla routing engine. This assumes your config path is valid,
   * tiles exist and your request string is valid.
   *
   * @param request
   * @return
   */
  override fun route(request: String): String {
    return valhallaKotlin.route(request, configPath)
  }

  /**
   * Run a trace_attributes request: map-match an encoded GPS trace onto the graph and return
   * per-edge attributes (surface, length, speed, road class, etc.). Assumes a valid config path,
   * tiles, and request string.
   *
   * @param request a valid Valhalla trace_attributes JSON request string
   * @return the raw Valhalla trace_attributes JSON response string
   */
  override fun traceAttributes(request: String): String {
    return valhallaKotlin.traceAttributes(request, configPath)
  }

  /**
   * Run a sources_to_targets (matrix) request: compute a time/distance matrix between the given
   * sources and targets. Assumes a valid config path, tiles, and request string.
   *
   * @param request a valid Valhalla matrix JSON request string
   * @return the raw Valhalla matrix JSON response string
   */
  override fun matrix(request: String): String {
    return valhallaKotlin.matrix(request, configPath)
  }
}
