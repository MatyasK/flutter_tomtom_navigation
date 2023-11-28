package com.tomtom.flutter_tomtom_navigation_android

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tomtom.quantity.Weight
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraChangeListener
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.gesture.MapLongClickListener
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.map.OnlineCachePolicy
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.style.LoadingStyleFailure
import com.tomtom.sdk.map.display.style.StandardStyles
import com.tomtom.sdk.map.display.style.StyleLoadingCallback
import com.tomtom.sdk.map.display.style.StyleMode
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.DestinationArrivalListener
import com.tomtom.sdk.navigation.NavigationFailure
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteUpdateReason
import com.tomtom.sdk.navigation.RouteUpdatedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.progress.RouteProgress
import com.tomtom.sdk.navigation.routereplanner.RouteReplanner
import com.tomtom.sdk.navigation.routereplanner.online.OnlineRouteReplannerFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.guidance.AnnouncementPoints
import com.tomtom.sdk.routing.options.guidance.ExtendedSections
import com.tomtom.sdk.routing.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.options.guidance.InstructionPhoneticsType
import com.tomtom.sdk.routing.options.guidance.InstructionType
import com.tomtom.sdk.routing.options.guidance.ProgressPoints
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.vehicle.DefaultVehicleProvider
import com.tomtom.sdk.vehicle.Vehicle
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.platform.PlatformView
import java.lang.IllegalArgumentException
import kotlin.random.Random

class FlutterTomtomNavigationView(
    context: Context,
    id: Int,
    creationParams: Map<String?, Any?>?
) : PlatformView, MethodCallHandler {
    private val context: Context
    private val apiKey: String

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private var channel: MethodChannel
    private val publish: (String) -> (Unit)

    // The relative layout contains the mapview (and navigation view),
    // stacked on top of each other.
    private val relativeLayout: RelativeLayout
    private val mapFragmentContainer: FragmentContainerView
    private val navigationFragmentContainer: FragmentContainerView

    override fun getView(): View {
        return relativeLayout
    }

    private lateinit var tomTomMap: TomTomMap

    // TomTom SDK objects, which should be disposed by calling close()!
    private lateinit var locationProvider: LocationProvider
    private lateinit var routePlanner: RoutePlanner

    private lateinit var routeReplanner: RouteReplanner
    private lateinit var tomTomNavigation: TomTomNavigation

    // Other SDK objects that do not have their own lifecycle
    private var mapFragment: MapFragment
    private var navigationFragment: NavigationFragment

    //    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private var route: Route? = null
    private lateinit var routePlanningOptions: RoutePlanningOptions
    private var useSimulation: Boolean = true

    override fun dispose() {
        channel.setMethodCallHandler(null)

        tomTomNavigation.close()
        routeReplanner.close()

        routePlanner.close()
    }

    init {
        this.context = context
        log("Init navigation view with id $id")

        if (creationParams.isNullOrEmpty()) {
            throw IllegalArgumentException("No creation parameters provided to the FlutterTomtomNavigationView")
        }

        // Get the API key from the creation params
        val mapOptionsJson = creationParams["mapOptions"] as String
        val rawMapOptions =
            Gson().fromJson(mapOptionsJson, MapOptions::class.java)
        log("${rawMapOptions.cameraOptions?.position}/${rawMapOptions.cameraOptions?.zoom} ($rawMapOptions)")

        val mapOptions = rawMapOptions.copy(
            rawMapOptions.mapKey,
            cameraOptions = rawMapOptions.cameraOptions,
            padding = rawMapOptions.padding,
            mapStyle = rawMapOptions.mapStyle,
            styleMode = rawMapOptions.styleMode,
            onlineCachePolicy = OnlineCachePolicy.Default,
            renderToTexture = rawMapOptions.renderToTexture,
        )

        log("Camera options is ${mapOptions.cameraOptions}")

        apiKey = mapOptions.mapKey

        if (apiKey.isEmpty()) {
            throw IllegalArgumentException("Missing Apikey")
        } else {
            log("APiKey is: $apiKey")
        }

        val debug = creationParams["debug"] as Boolean

        // Get the binary messenger from the creation params
        val binaryMessenger =
            creationParams["binaryMessenger"] as BinaryMessenger
        channel = MethodChannel(binaryMessenger, "flutter_tomtom_navigation")
        channel.setMethodCallHandler(this)

        log("methodChanel handler has been setup")

        @Suppress("UNCHECKED_CAST")
        publish = creationParams["publish"] as (String) -> (Unit)

        sendNavigationStatusUpdate(NavigationStatus.INITIALIZING)

        // The root view is a RelativeLayout
        relativeLayout = RelativeLayout(context)

        // This layout contains two views: the map view...
        mapFragment = MapFragment.newInstance(mapOptions)
        mapFragmentContainer = FragmentContainerView(context)
        mapFragmentContainer.id = Random.nextInt()
        try {
            mapFragmentContainer.doOnAttach {
                val activity = it.context.getFragmentActivityOrThrow()
                activity.supportFragmentManager.findFragmentByTag("flutter_fragment")?.childFragmentManager?.beginTransaction()
                    ?.replace(it.id, mapFragment)?.commit()
                log("MapFragment successfully attached")
                relativeLayout.addView(mapFragmentContainer)
            }
        } catch(e: Exception) {
            log("Attaching mapfragment failed $e")
        }



        log("Map fragment has been setup")

        // ...and the navigation view
        navigationFragment = NavigationFragment.newInstance(
            NavigationUiOptions()
        )

        navigationFragmentContainer = FragmentContainerView(context)
        navigationFragmentContainer.id = Random.nextInt()
        navigationFragmentContainer.foregroundGravity = Gravity.BOTTOM
        val params = RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        navigationFragmentContainer.layoutParams = params

        log("Navigation fragment has been setup")
        // Hide it by default
        navigationFragmentContainer.visibility = View.INVISIBLE
        navigationFragmentContainer.doOnAttach {
            val activity = it.context.getFragmentActivityOrThrow()
            activity.supportFragmentManager.findFragmentByTag("flutter_fragment")?.childFragmentManager?.beginTransaction()
                ?.replace(it.id, navigationFragment)?.commit()
        }
        relativeLayout.addView(navigationFragmentContainer)

        initLocationProvider()
        initRouting()
        initNavigation()
        log("getting map")
        mapFragment.getMapAsync {
            log("Map is ready")
            tomTomMap = it
            sendNavigationStatusUpdate(NavigationStatus.READY)
            enableUserLocation()
            if (debug) setUpMapListeners()
            tomTomMap.loadStyle(
                StandardStyles.VEHICLE_RESTRICTIONS,
                styleLoadingCallback,
            )
        }

        log("Initialization finished")
    }


    private fun Context.getFragmentActivityOrThrow(): FragmentActivity {
        if (this is FragmentActivity) {
            return this
        }

        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is FragmentActivity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }

        throw IllegalStateException("Unable to find activity")
    }

    private fun showNavigation() {
        navigationFragmentContainer.visibility = View.VISIBLE
    }

    private fun hideNavigation() {
        navigationFragmentContainer.visibility = View.INVISIBLE
    }

    // Below functions are copied from/based on the example activity
    // https://github.com/tomtom-international/tomtom-navigation-android-examples/blob/main/app/src/main/java/com/tomtom/sdk/examples/usecase/BasicNavigationActivity.kt

    /**
     * The SDK provides a LocationProvider interface that is used between different modules to get location updates.
     * This examples uses the AndroidLocationProvider.
     * Under the hood, the engine uses Android’s system location services.
     */
    private fun initLocationProvider() {
        locationProvider = AndroidLocationProvider(context = context)
    }

    /**
     * You can plan route by initializing by using the online route planner and default route replanner.
     */
    private fun initRouting() {
        routePlanner =
            OnlineRoutePlanner.create(context = context, apiKey = apiKey)
        routeReplanner = OnlineRouteReplannerFactory.create(routePlanner)
    }

    /**
     * To use navigation in the application, start by by initialising the navigation configuration.
     */
    private fun initNavigation() {
        val configuration = Configuration(
            context = context,
            apiKey = apiKey,
            locationProvider = locationProvider,
            routeReplanner = routeReplanner,
            vehicleProvider = DefaultVehicleProvider(vehicle = Vehicle.Car())
        )
        tomTomNavigation = OnlineTomTomNavigationFactory.create(configuration)
    }

    /**
     * In order to show the user’s location, the application must use the device’s location services, which requires the appropriate permissions.
     */
    private fun enableUserLocation() {
        if (areLocationPermissionsGranted()) {
            showUserLocation()
        } else {
            log("No location permissions :(")
        }
    }

    /**
     * The LocationProvider itself only reports location changes. It does not interact internally with the map or navigation.
     * Therefore, to show the user’s location on the map you have to set the LocationProvider to the TomTomMap.
     * You also have to manually enable the location indicator.
     * It can be configured using the LocationMarkerOptions class.
     *
     * Read more about user location on the map in the Showing User Location guide.
     */
    private fun showUserLocation() {
        locationProvider.enable()
        // zoom to current location at city level
//        onLocationUpdateListener = OnLocationUpdateListener { location ->
//            tomTomMap.moveCamera(CameraOptions(location.position, zoom = 8.0))
//            locationProvider.removeOnLocationUpdateListener(
//                onLocationUpdateListener
//            )
//        }
//        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
        tomTomMap.setLocationProvider(locationProvider)
        val locationMarker =
            LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer)
        tomTomMap.enableLocationMarker(locationMarker)
    }

    /**
     * In this example on planning a route, the origin is the user’s location and the destination is determined by the user selecting a location on the map.
     * Navigation is started once the user taps on the route.
     *
     * To mark the destination on the map, add the MapLongClickListener event handler to the map view.
     * To start navigation, add the addRouteClickListener event handler to the map view.
     */
    private fun setUpMapListeners() {
        tomTomMap.addMapLongClickListener(mapLongClickListener)
        tomTomMap.addRouteClickListener(routeClickListener)
    }

    /**
     * Used to calculate a route based on a selected location.
     * - The method removes all polygons, circles, routes, and markers that were previously added to the map.
     * - It then creates a route between the user’s location and the selected location.
     * - The method needs to return a boolean value when the callback is consumed.
     */
    private val mapLongClickListener = MapLongClickListener { geoPoint ->
        clearMap()
        calculateRouteTo(geoPoint)
        true
    }

    /**
     * Checks whether navigation is currently running.
     */
    private fun isNavigationRunning(): Boolean =
        tomTomNavigation.navigationSnapshot != null


    /**
     * Used to start navigation based on a tapped route, if navigation is not already running.
     * - Hide the location button
     * - Then start the navigation using the selected route.
     */
    private val routeClickListener = RouteClickListener {
        if (!isNavigationRunning()) {
            route?.let { route ->
                mapFragment.currentLocationButton.visibilityPolicy =
                    CurrentLocationButton.VisibilityPolicy.Invisible
                startNavigation(route)
            }
        }
    }

    /**
     * Used to calculate a route using the following parameters:
     * - InstructionType - This indicates that the routing result has to contain guidance instructions.
     * - InstructionPhoneticsType - This specifies whether to include phonetic transcriptions in the response.
     * - AnnouncementPoints - When this parameter is specified, the instruction in the response includes up to three additional fine-grained announcement points, each with its own location, maneuver type, and distance to the instruction point.
     * - ExtendedSections - This specifies whether to include extended guidance sections in the response, such as sections of type road shield, lane, and speed limit.
     * - ProgressPoints - This specifies whether to include progress points in the response.
     */
    private fun calculateRouteTo(destination: GeoPoint) {
        val userLocation =
            tomTomMap.currentLocation?.position ?: return
        val itinerary =
            Itinerary(origin = userLocation, destination = destination)
        routePlanningOptions = RoutePlanningOptions(
            itinerary = itinerary,
            guidanceOptions = GuidanceOptions(
                instructionType = InstructionType.Text,
                phoneticsType = InstructionPhoneticsType.Ipa,
                announcementPoints = AnnouncementPoints.All,
                extendedSections = ExtendedSections.All,
                progressPoints = ProgressPoints.All
            ),
            vehicle = Vehicle.Car()
        )
        routePlanner.planRoute(routePlanningOptions, routePlanningCallback)
    }

    /**
     * The RoutePlanningCallback itself has two methods.
     * - The first method is triggered if the request fails.
     * - The second method returns RoutePlanningResponse containing the routing results.
     * - This example draws the first retrieved route on the map, using the RouteOptions class.
     */
    private val routePlanningCallback = object : RoutePlanningCallback {
        override fun onSuccess(result: RoutePlanningResponse) {
            val summaryJson = Gson().toJson(result.routes.first().summary)
            val json =
                appendNavigationUpdateStatusToJson(
                    summaryJson,
                    NativeEventType.ROUTE_PLANNED
                )
            publish(json)

            route = result.routes.first()
            drawRoute(route!!)
            tomTomMap.zoomToRoutes(ZOOM_TO_ROUTE_PADDING)
        }

        override fun onFailure(failure: RoutingFailure) {
            Toast.makeText(context, failure.message, Toast.LENGTH_SHORT).show()
        }

        override fun onRoutePlanned(route: Route) = Unit
    }

    /**
     * Used to draw route on the map
     * You can show the overview of the added routes using the TomTomMap.zoomToRoutes(Int) method. Note that its padding parameter is expressed in pixels.
     */
    private fun drawRoute(route: Route) {
        val instructions = route.mapInstructions()
        val routeOptions = RouteOptions(
            geometry = route.geometry,
            destinationMarkerVisible = true,
            departureMarkerVisible = true,
            instructions = instructions,
            routeOffset = route.routePoints.map { it.routeOffset }
        )
        println("Adding route to map!")
        tomTomMap.addRoute(routeOptions)

        println(tomTomMap.routes)
    }

    /**
     * For the navigation use case, the instructions can be drawn on the route in form of arrows that indicate maneuvers.
     * To do this, map the Instruction object provided by routing to the Instruction object used by the map.
     * Note that during navigation, you need to update the progress property of the drawn route to display the next instructions.
     */
    private fun Route.mapInstructions(): List<Instruction> {
        val routeInstructions =
            legs.flatMap { routeLeg -> routeLeg.instructions }
        return routeInstructions.map {
            Instruction(
                routeOffset = it.routeOffset,
                combineWithNext = it.combineWithNext
            )
        }
    }

    /**
     * Used to start navigation by
     * - initializing the NavigationFragment to display the UI navigation information,
     * - passing the Route object along which the navigation will be done, and RoutePlanningOptions used during the route planning,
     * - handling the updates to the navigation states using the NavigationListener.
     * Note that you have to set the previously-created TomTom Navigation object to the NavigationFragment before using it.
     */

    private fun startNavigation(route: Route, useSimulation: Boolean = true) {
        showNavigation()
        navigationFragment.setTomTomNavigation(tomTomNavigation)
        val routePlan = RoutePlan(route, routePlanningOptions)
        navigationFragment.startNavigation(routePlan)
        navigationFragment.addNavigationListener(navigationListener)
        tomTomNavigation.addProgressUpdatedListener(progressUpdatedListener)
        tomTomNavigation.addRouteUpdatedListener(routeUpdatedListener)
        tomTomNavigation.addDestinationArrivalListener(
            destinationArrivalListener
        )
        this.useSimulation = useSimulation
    }

    /**
     * Handle the updates to the navigation states using the NavigationListener
     * - Use CameraChangeListener to observe camera tracking mode and detect if the camera is locked on the chevron. If the user starts to move the camera, it will change and you can adjust the UI to suit.
     * - Use the SimulationLocationProvider for testing purposes.
     * - Once navigation is started, the camera is set to follow the user position, and the location indicator is changed to a chevron. To match raw location updates to the routes, use MapMatchedLocationProvider and set it to the TomTomMap.
     * - Set the bottom padding on the map. The padding sets a safe area of the MapView in which user interaction is not received. It is used to uncover the chevron in the navigation panel.
     */
    private val navigationListener =
        object : NavigationFragment.NavigationListener {
            override fun onStarted() {
                println("navigation started")
                sendNavigationStatusUpdate(NavigationStatus.RUNNING)

                tomTomMap.addCameraChangeListener(cameraChangeListener)
                tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRoute
                tomTomMap.enableLocationMarker(
                    LocationMarkerOptions(
                        LocationMarkerOptions.Type.Chevron
                    )
                )
                setMapMatchedLocationProvider()
                setLocationProviderToNavigation(route!!)
                setMapNavigationPadding()
            }

            override fun onFailed(failure: NavigationFailure) {
                sendNavigationStatusUpdate(NavigationStatus.FAILED)

                Toast.makeText(context, failure.message, Toast.LENGTH_SHORT)
                    .show()
                stopNavigation()
            }

            override fun onStopped() {
                sendNavigationStatusUpdate(NavigationStatus.STOPPED)
                println("stopped!")
                stopNavigation()
            }
        }

    private val progressUpdatedListener = ProgressUpdatedListener {
        tomTomMap.routes.firstOrNull()?.progress = it.distanceAlongRoute
        sendRouteUpdateEvent(it)
    }

    private val routeUpdatedListener by lazy {
        RouteUpdatedListener { route, updateReason ->
            if (updateReason != RouteUpdateReason.Refresh &&
                updateReason != RouteUpdateReason.Increment &&
                updateReason != RouteUpdateReason.LanguageChange
            ) {
                tomTomMap.removeRoutes()
                drawRoute(route)
            }
        }
    }

    private val destinationArrivalListener = DestinationArrivalListener {
        // Send event
        val route = "{\"routeId\": \"${it.id}\"}"
        val json = appendNavigationUpdateStatusToJson(
            route,
            NativeEventType.DESTINATION_ARRIVAL
        )
        publish(json)
    }

    /**
     * Use the SimulationLocationProvider for testing purposes.
     */
    private fun setLocationProviderToNavigation(route: Route) {
        locationProvider = if (useSimulation) {
            val routeGeoLocations = route.geometry.map { GeoLocation(it) }
            val simulationStrategy = InterpolationStrategy(routeGeoLocations)
            SimulationLocationProvider.create(strategy = simulationStrategy)
        } else {
            AndroidLocationProvider(context)
        }
        tomTomNavigation.locationProvider = locationProvider
        locationProvider.enable()
    }

    /**
     * Stop the navigation process using NavigationFragment.
     * This hides the UI elements and calls the TomTomNavigation.stop() method.
     * Don’t forget to reset any map settings that were changed, such as camera tracking, location marker, and map padding.
     */
    private fun stopNavigation() {
        navigationFragment.stopNavigation()
        mapFragment.currentLocationButton.visibilityPolicy =
            CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
        tomTomMap.removeCameraChangeListener(cameraChangeListener)
        tomTomMap.cameraTrackingMode = CameraTrackingMode.None
        tomTomMap.enableLocationMarker(
            LocationMarkerOptions(
                LocationMarkerOptions.Type.Pointer
            )
        )
        resetMapPadding()
        navigationFragment.removeNavigationListener(navigationListener)
        tomTomNavigation.removeProgressUpdatedListener(progressUpdatedListener)
        tomTomNavigation.removeRouteUpdatedListener(routeUpdatedListener)
        tomTomNavigation.removeDestinationArrivalListener(
            destinationArrivalListener
        )
        clearMap()
        hideNavigation()
        initLocationProvider()
        enableUserLocation()
    }

    private val styleLoadingCallback = object : StyleLoadingCallback {
        override fun onSuccess() {
            tomTomMap.hideVehicleRestrictions()
            sendNavigationStatusUpdate(NavigationStatus.READY)
            log("Style loading succeeded sent navigation status ready")
        }

        override fun onFailure(failure: LoadingStyleFailure) {
            log("Style loading failed: $failure")
        }
    }

    /**
     * Set the bottom padding on the map. The padding sets a safe area of the MapView in which user interaction is not received. It is used to uncover the chevron in the navigation panel.
     */
    private fun setMapNavigationPadding() {
        val paddingBottom =
            240 // resources.getDimensionPixelOffset(R.dimen.map_padding_bottom)
        val padding = Padding(0, 0, 0, paddingBottom)
        setPadding(padding)
    }

    private fun setPadding(padding: Padding) {
        val scale: Float =
            context.getFragmentActivityOrThrow().resources.displayMetrics.density

        val paddingInPixels = Padding(
            top = (padding.top * scale).toInt(),
            left = (padding.left * scale).toInt(),
            right = (padding.right * scale).toInt(),
            bottom = (padding.bottom * scale).toInt()
        )
        println("setting padding (in pixels) to $paddingInPixels, from $padding")
        tomTomMap.setPadding(paddingInPixels)
    }

    private fun resetMapPadding() {
        tomTomMap.setPadding(Padding(0, 0, 0, 0))
    }

    /**
     * Once navigation is started, the camera is set to follow the user position, and the location indicator is changed to a chevron.
     * To match raw location updates to the routes, use MapMatchedLocationProvider and set it to the TomTomMap.
     */
    private fun setMapMatchedLocationProvider() {
        val mapMatchedLocationProvider =
            MapMatchedLocationProvider(tomTomNavigation)
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        mapMatchedLocationProvider.enable()
    }

    /**
     *
     * The method removes all polygons, circles, routes, and markers that were previously added to the map.
     */
    private fun clearMap() {
        tomTomMap.clear()
        tomTomMap.hideVehicleRestrictions()
    }

    private val cameraChangeListener by lazy {
        CameraChangeListener {
            // TODO(Frank): This does not do anything. Instead, we hide and show the whole navigation view.
            val cameraTrackingMode = tomTomMap.cameraTrackingMode
            if (cameraTrackingMode == CameraTrackingMode.FollowRoute) {
                navigationFragment.navigationView.showSpeedView()
            } else {
                navigationFragment.navigationView.hideSpeedView()
            }
        }
    }

    private fun areLocationPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val ZOOM_TO_ROUTE_PADDING = 100
    }

    /**
     * Handle different method calls from Flutter to the navigation view.
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        println("Called method ${call.method}...")

        when (call.method) {
            "planRoute" -> {
                val cameraOptions = CameraOptions(
                    position = GeoPoint(52.1, 5.6),
                    zoom = 1.0,
                    tilt = 2.0,
                    rotation = 3.0,
                    fieldOfView = 4.0,
                )
                println(cameraOptions.position)
                println(Gson().toJson(cameraOptions))

                val userLocation =
                    tomTomMap.currentLocation?.position ?: return
                routePlanningOptions =
                    RoutePlanningOptionsDeserializer.deserialize(
                        call.arguments as String,
                        userLocation
                    )
                clearMap()

                // Show the vehicle restrictions that apply for the current vehicle
                // TODO do we want to make this optional? Is this what "showLayer" can be used for?
                tomTomMap.showVehicleRestrictions(routePlanningOptions.vehicle)
                routePlanner.planRoute(
                    routePlanningOptions,
                    routePlanningCallback
                )
            }

            "startNavigation" -> {
                val useSimulation = call.argument<Boolean>("useSimulation")!!

                // Same thing that is attached to the RouteClickListener in the example
                if (!isNavigationRunning()) {
                    route?.let { route ->
                        mapFragment.currentLocationButton.visibilityPolicy =
                            CurrentLocationButton.VisibilityPolicy.Invisible
                        startNavigation(route, useSimulation)
                    }
                }
            }

            "stopNavigation" -> {
                stopNavigation()
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun sendNavigationStatusUpdate(status: NavigationStatus) {
        val jsonString = "{" +
                "  \"navigationStatus\": ${status.value}" +
                "}"

        log("Sending new navigation update: $jsonString")
        val response =
            appendNavigationUpdateStatusToJson(
                jsonString,
                NativeEventType.NAVIGATION_UPDATE
            )

        publish(response)
    }

    private fun sendRouteUpdateEvent(event: RouteProgress) {
        val result = Gson().toJson(event)

        val response = appendNavigationUpdateStatusToJson(
            result,
            NativeEventType.ROUTE_UPDATE
        )
        publish(response)
    }

    // Adds the navigation Status to any Json string
    private fun appendNavigationUpdateStatusToJson(
        json: String,
        status: NativeEventType
    ): String {
        val newJsonObject = JsonObject()
        newJsonObject.addProperty("nativeEventType", status.value)
        newJsonObject.addProperty("data", json)

        return newJsonObject.toString()
    }
}

fun log(log: String) {
    println("[FlutterTomTomNavigationView]: $log")
}

// This should represent the communication between the native code and dart plugin
enum class NativeEventType(val value: Int) {
    ROUTE_UPDATE(1),
    ROUTE_PLANNED(2),
    NAVIGATION_UPDATE(3),
    DESTINATION_ARRIVAL(4),
}

enum class NavigationStatus(val value: Int) {
    INITIALIZING(0),
    READY(1),
    RUNNING(2),
    STOPPED(3),
    FAILED(4),
}