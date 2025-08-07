package net.dacworld.android.holyplacesofthelord.map

import android.graphics.BitmapFactory // For loading icon
import android.graphics.Color // For info window example
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView // For info window example
import android.widget.Toast // For info window example
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.gson.JsonObject // For feature properties
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.MapViewModelFactory
import net.dacworld.android.holyplacesofthelord.databinding.FragmentMapBinding
import net.dacworld.android.holyplacesofthelord.data.MapPlace // Assuming this is your data class
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression // For SymbolLayer expressions
import org.maplibre.android.style.layers.PropertyFactory // For SymbolLayer properties
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point // <<< UPDATED
import org.maplibre.geojson.Feature // <<< UPDATED
import org.maplibre.geojson.FeatureCollection // <<< UPDATED
import org.maplibre.android.plugins.markerview.MarkerView
import org.maplibre.android.plugins.markerview.MarkerViewManager
import net.dacworld.android.holyplacesofthelord.util.ColorUtils

class MapFragment : Fragment(), OnMapReadyCallback, MapLibreMap.OnMapClickListener { // Implement OnMapClickListener

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var maplibreMap: MapLibreMap? = null
    private lateinit var mapView: MapView
    private var mapStyle: Style? = null // To hold the loaded style
    private lateinit var markerViewManager: MarkerViewManager
    private var activeMarkerView: MarkerView? = null
    companion object {
        private const val FRAGMENT_TAG = "MapFragment"
        private const val GEOJSON_SOURCE_ID = "places_source"
        private const val ICON_ID = "place_icon"
        private const val SYMBOL_LAYER_ID = "places_layer"

        // Keys for Feature properties
        private const val PROPERTY_ID = "place_id"
        private const val PROPERTY_NAME = "place_name"
        private const val PROPERTY_ADDRESS = "place_address"
        private const val PROPERTY_TYPE = "place_type"
        private const val PROPERTY_IS_VISITED = "place_is_visited"
    }

    private val mapViewModel: net.dacworld.android.holyplacesofthelord.data.MapViewModel by viewModels {
        Log.d(FRAGMENT_TAG, "Creating MapViewModel using MapViewModelFactory.")
        MapViewModelFactory(requireActivity().application)
    }

    // Reference to the info window TextView
    private var currentInfoWindowPlaceId: String? = null

    // No longer need currentMarkers list or markerIdToMapPlaceMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(FRAGMENT_TAG, "onCreate called")
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(FRAGMENT_TAG, "onCreateView called")
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(FRAGMENT_TAG, "onViewCreated called")
        try {
            val viewModel = mapViewModel
            Log.d(FRAGMENT_TAG, "MapViewModel instance accessed in onViewCreated: $viewModel")
        } catch (e: Exception) {
            Log.e(FRAGMENT_TAG, "Error accessing MapViewModel in onViewCreated: ${e.message}", e)
        }
        setupToolbar()
        setupMap()
        observeViewModel()
    }

    private fun setupToolbar() {
        Log.d(FRAGMENT_TAG, "setupToolbar called")
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarMap)
        binding.toolbarMap.title = getString(R.string.title_map_tab)
    }

    private fun setupMap() {
        Log.d(FRAGMENT_TAG, "setupMap called, calling mapView.getMapAsync()")
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: MapLibreMap) {
        Log.d(FRAGMENT_TAG, "onMapReady called with map: $map")
        this.maplibreMap = map
        markerViewManager = MarkerViewManager(mapView, map) // mapView is your class member

        val styleUrl = "https://api.maptiler.com/maps/basic-v2/style.json?key=0zuusUKvMimRw3H8I6WV" // IMPORTANT: Replace
        Log.d(FRAGMENT_TAG, "Setting map style: $styleUrl")

        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            Log.d(FRAGMENT_TAG, "Map style loaded successfully: ${style.json}")
            this.mapStyle = style
            map.addOnMapClickListener(this) // Add map click listener

            // 1. Add icon image to style
            val iconBitmap = BitmapFactory.decodeResource(resources, R.drawable.temple_map_pin)
            if (iconBitmap != null) {
                style.addImage(ICON_ID, iconBitmap)
                Log.d(FRAGMENT_TAG, "Icon '$ICON_ID' added to style.")
            } else {
                Log.e(FRAGMENT_TAG, "Failed to decode icon resource for '$ICON_ID'.")
                // Optionally, fall back to a default MapLibre icon or no icon,
                // or handle this error more gracefully. For now, it will just mean no icons.
            }

            // 2. Add GeoJsonSource
            style.addSource(GeoJsonSource(GEOJSON_SOURCE_ID))
            Log.d(FRAGMENT_TAG, "GeoJsonSource '$GEOJSON_SOURCE_ID' added.")

            // 3. Add SymbolLayer
            val symbolLayer = SymbolLayer(SYMBOL_LAYER_ID, GEOJSON_SOURCE_ID)
                .withProperties(
                    PropertyFactory.iconImage(ICON_ID),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(Expression.literal(0.2f))
                )
            style.addLayer(symbolLayer)
            Log.d(FRAGMENT_TAG, "GeoJsonSource and SymbolLayer added to style.")

            // --- RESTORE OR SET INITIAL CAMERA POSITION ---
            val savedState = mapViewModel.lastCameraState.value // Assumes lastCameraState is LiveData in ViewModel
            if (savedState != null) {
                val restoredCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(savedState.latitude, savedState.longitude))
                    .zoom(savedState.zoom)
                 savedState.bearing?.let { restoredCameraPosition.bearing(it) }
                 savedState.tilt?.let { restoredCameraPosition.tilt(it) }

                map.moveCamera(CameraUpdateFactory.newCameraPosition(restoredCameraPosition.build()))
                Log.d(FRAGMENT_TAG, "Restored camera state to: LatLng(${savedState.latitude}, ${savedState.longitude}), Zoom: ${savedState.zoom}")
                mapViewModel.clearLastCameraState() // Clear the state after restoring
            } else {
                // No saved state, use your existing default initial camera position
                val initialLatLng = LatLng(40.770468, -111.891958) // Salt Lake Temple
                val initialZoom = 2.0
                // maplibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, initialZoom)) // Your old line
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, initialZoom)) // Use moveCamera for consistency
                Log.d(FRAGMENT_TAG, "Initial camera set to $initialLatLng at zoom $initialZoom")
            }

            // Initial data load if ViewModel already has data
            val currentPlaces = mapViewModel.mapPlaces.value
            if (currentPlaces != null && currentPlaces.isNotEmpty()) {
                Log.d(FRAGMENT_TAG, "Map ready, and places already available. Loading ${currentPlaces.size} features.")
                loadPlacesIntoSource(currentPlaces)
            } else if (currentPlaces != null && currentPlaces.isEmpty()) {
                Log.d(FRAGMENT_TAG, "Map ready, ViewModel has empty list of places.")
                clearAllPlacesFromSource()
            } else {
                Log.d(FRAGMENT_TAG, "Map ready, ViewModel places is null. Waiting for observer.")
            }
        }
    }

    private fun observeViewModel() {
        Log.d(FRAGMENT_TAG, "observeViewModel() called.")
        mapViewModel.mapPlaces.observe(viewLifecycleOwner) { places ->
            Log.d(FRAGMENT_TAG, "mapPlaces LiveData OBSERVED. Places count: ${places?.size ?: 0}")
            if (mapStyle?.isFullyLoaded == true) { // Check if style and source/layers are ready
                Log.d(FRAGMENT_TAG, "Map style is loaded, calling loadPlacesIntoSource.")
                if (places != null) {
                    loadPlacesIntoSource(places) // Load new data into the source
                } else {
                    clearAllPlacesFromSource() // Treat null as empty
                }
            } else {
                Log.d(FRAGMENT_TAG, "mapPlaces LiveData observed, but map style/layers not yet ready.")
            }
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        Log.d(FRAGMENT_TAG, "Map clicked at: $point")
        // REMOVE/REPLACE old info window logic
        // hideInfoWindow() // Keep this call, but its implementation will change

        val screenPoint: PointF? = maplibreMap?.projection?.toScreenLocation(point)

        if (screenPoint != null) {
            val features = maplibreMap?.queryRenderedFeatures(screenPoint, SYMBOL_LAYER_ID)
            if (!features.isNullOrEmpty()) {
                val clickedFeature = features[0]
                val placeName = clickedFeature.getStringProperty(PROPERTY_NAME)
                val placeId = clickedFeature.getStringProperty(PROPERTY_ID)
                val geometry = clickedFeature.geometry()
                Log.d(FRAGMENT_TAG, "Feature ID: $placeId, Geometry object: $geometry, Geometry type: ${geometry?.type()}")

                var featureLatLng: LatLng? = null
                if (geometry is org.maplibre.geojson.Point) {
                    featureLatLng = LatLng(geometry.latitude(), geometry.longitude())
                    Log.d(FRAGMENT_TAG, "Geometry IS org.maplibre.geojson.Point. featureLatLng: $featureLatLng")
                } else {
                    Log.w(FRAGMENT_TAG, "Geometry is NOT org.maplibre.geojson.Point. Actual class: ${geometry?.javaClass?.name}")
                }

                if (placeName != null && placeId != null && featureLatLng != null) {
                    Log.d(FRAGMENT_TAG, "Clicked on feature: $placeName, ID: $placeId at $featureLatLng")
                    currentInfoWindowPlaceId = placeId
                    val templeType = clickedFeature.getStringProperty(PROPERTY_TYPE)
                    showInfoWindow(placeName, placeId, featureLatLng, templeType) // Add placeId
                    return true
                } else {
                    Log.w(FRAGMENT_TAG, "Clicked feature missing name, ID, or geometry. Name: $placeName, ID: $placeId")
                    hideInfoWindow() // Ensure it's hidden if data is incomplete
                }
            } else {
                Log.d(FRAGMENT_TAG, "No features found at click point on the symbol layer.")
                hideInfoWindow()
            }
        } else {
            Log.w(FRAGMENT_TAG, "Could not convert map click LatLng to screen point.")
            hideInfoWindow()
        }
        return false
    }

    private fun showInfoWindow(placeName: String, placeId: String, featureLatLng: LatLng, templeType: String?) {
        // Ensure any previous MarkerView is removed first
        hideInfoWindow()

        // Inflate the custom layout for the MarkerView
        val inflater = LayoutInflater.from(requireContext())
        val customView = inflater.inflate(R.layout.custom_map_info_window, binding.root, false)

        val placeNameTextView = customView.findViewById<TextView>(R.id.info_window_place_name)
        placeNameTextView.text = placeName

        if (templeType != null) {
            val colorRes = ColorUtils.getTextColorForTempleType(requireContext(), templeType)
            placeNameTextView.setTextColor(colorRes)
            Log.d(FRAGMENT_TAG, "Setting text color for type '$templeType' to resolved color: $colorRes")
        } else {
            // Optional: Fallback color if templeType is null
            placeNameTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_colorOnSurface)) // Using your ColorUtils default
            Log.w(FRAGMENT_TAG, "Temple type is null, using default text color.")
        }

        // Set click listener for navigation
        customView.setOnClickListener {
            Log.d(FRAGMENT_TAG, "MarkerView clicked for place: $placeName, ID: $placeId. Navigating...")
            // --- YOUR EXISTING NAVIGATION LOGIC ---
            currentInfoWindowPlaceId?.let { navPlaceId -> // Use currentInfoWindowPlaceId if it's set
                if (navPlaceId.isNotEmpty()) {
                    Log.d(FRAGMENT_TAG, "Info window clicked for place ID: $navPlaceId. Navigating.")
                    try {
                        val action = MapFragmentDirections.actionMapFragmentToPlaceDetailFragment(navPlaceId)
                        findNavController().navigate(action)
                    } catch (e: IllegalStateException) {
                        Log.e(FRAGMENT_TAG, "Navigation failed: Have you rebuilt after adding the action to nav_graph? ${e.message}", e)
                        Toast.makeText(context, "Error navigating to details.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(FRAGMENT_TAG, "Navigation failed. ${e.message}", e)
                        Toast.makeText(context, "Could not navigate to details.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(FRAGMENT_TAG, "Info window clicked, but place ID is empty.")
                }
            }
            hideInfoWindow() // Hide info window after click
        }

        activeMarkerView = MarkerView(featureLatLng, customView)

        markerViewManager.addMarker(activeMarkerView!!)
        Log.d(FRAGMENT_TAG, "Showing MarkerView for '$placeName' at $featureLatLng")
    }


    private fun hideInfoWindow() {
        activeMarkerView?.let {
            markerViewManager.removeMarker(it)
            Log.d(FRAGMENT_TAG, "MarkerView hidden and removed.")
        }
        activeMarkerView = null
        // currentInfoWindowPlaceId = null // Optional: clear this if it's solely for the info window's state
    }

    private fun loadPlacesIntoSource(places: List<MapPlace>) {
        Log.d(FRAGMENT_TAG, "loadPlacesIntoSource() called with ${places.size} places.")
        val style = mapStyle ?: run {
            Log.w(FRAGMENT_TAG, "loadPlacesIntoSource: mapStyle is null!")
            return
        }
        val source = style.getSourceAs<GeoJsonSource>(GEOJSON_SOURCE_ID)
        if (source == null) {
            Log.e(FRAGMENT_TAG, "GeoJsonSource with ID '$GEOJSON_SOURCE_ID' not found!")
            return
        }

        val features = mutableListOf<Feature>()
        places.forEach { place ->
            val point = Point.fromLngLat(place.longitude, place.latitude)
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ID, place.id)
                addProperty(PROPERTY_NAME, place.name)
                addProperty(PROPERTY_ADDRESS, place.address ?: "")
                addProperty(PROPERTY_TYPE, place.type)
                addProperty(PROPERTY_IS_VISITED, place.isVisited)
            }
            features.add(Feature.fromGeometry(point, properties))
        }
        // Convert FeatureCollection to JSON String
        val featureCollectionJson = FeatureCollection.fromFeatures(features).toJson()
        source.setGeoJson(featureCollectionJson) // Use the String overload
        Log.d(FRAGMENT_TAG, "GeoJsonSource updated with ${features.size} features.")
    }

    private fun clearAllPlacesFromSource() {
        Log.d(FRAGMENT_TAG, "clearAllPlacesFromSource() called.")
        val style = mapStyle ?: return
        val source = style.getSourceAs<GeoJsonSource>(GEOJSON_SOURCE_ID)
        val emptyFeatureCollectionJson = FeatureCollection.fromFeatures(emptyList<Feature>()).toJson()
        source?.setGeoJson(emptyFeatureCollectionJson) // Use the String overload
        Log.d(FRAGMENT_TAG, "GeoJsonSource cleared.")
    }

    // --- Lifecycle methods remain the same ---
    override fun onStart() {
        Log.d(FRAGMENT_TAG, "onStart")
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        Log.d(FRAGMENT_TAG, "onResume")
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        Log.d(FRAGMENT_TAG, "onPause")
        maplibreMap?.cameraPosition?.let {
            mapViewModel.saveCameraState(it)
        }
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        Log.d(FRAGMENT_TAG, "onStop")
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(FRAGMENT_TAG, "onSaveInstanceState")
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        Log.d(FRAGMENT_TAG, "onLowMemory")
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        Log.d(FRAGMENT_TAG, "onDestroyView")
        super.onDestroyView()
        hideInfoWindow()
        maplibreMap?.removeOnMapClickListener(this) // Remove listener
        mapView.onDestroy()
        maplibreMap = null
        mapStyle = null
        if (::markerViewManager.isInitialized) { // Check if initialized before calling onDestroy
            markerViewManager.onDestroy()
        }
        _binding = null
    }
}
