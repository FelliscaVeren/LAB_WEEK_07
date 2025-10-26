package com.example.lab_week_07

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
    }

    private lateinit var mMap: GoogleMap

    // Fused Location Provider
    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // For requesting runtime permission
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // For requesting location updates (fallback when lastLocation == null)
    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false
    private val locationRequest: LocationRequest by lazy {
        LocationRequest.create().apply {
            interval = 5000L
            fastestInterval = 2000L
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.d(TAG, "Permission granted via launcher")
                    getLastLocation()
                } else {
                    Log.d(TAG, "Permission denied via launcher")
                    showPermissionRationale {
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d(TAG, "onMapReady called")

        // UI
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // Try different map types if you want (NORMAL, SATELLITE, HYBRID, TERRAIN)
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        // mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        // mMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        // Callback when tiles have loaded
        mMap.setOnMapLoadedCallback {
            Log.d(TAG, "Map tiles loaded (onMapLoadedCallback). mapType=${mMap.mapType}")
            Toast.makeText(this, "Map tiles loaded", Toast.LENGTH_SHORT).show()
        }

        // If permission already granted, enable My Location layer
        if (hasLocationPermission()) {
            try {
                mMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                Log.e(TAG, "isMyLocationEnabled SecurityException: ${e.message}")
            }
        }

        // Existing permission flow / get location
        when {
            hasLocationPermission() -> getLastLocation()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionRationale {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun showPermissionRationale(positiveAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Location permission")
            .setMessage("This app will not work without knowing your current location")
            .setPositiveButton(android.R.string.ok) { _, _ -> positiveAction() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun getLastLocation() {
        if (hasLocationPermission()) {
            try {
                fusedLocationProviderClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            Log.d(TAG, "lastLocation available: ${location.latitude}, ${location.longitude}")
                            val userLocation = LatLng(location.latitude, location.longitude)
                            updateMapLocation(userLocation)
                            addMarkerAtLocation(userLocation, "You")
                        } else {
                            Log.w(TAG, "lastLocation is null — starting location updates")
                            // fallback: request location updates
                            startLocationUpdates()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "getLastLocation failed", e)
                        // fallback: request location updates
                        startLocationUpdates()
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "startLocationUpdates called without permission")
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (requestingLocationUpdates) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                Log.d(TAG, "LocationCallback onLocationResult: $location")
                location?.let {
                    val userLocation = LatLng(it.latitude, it.longitude)
                    updateMapLocation(userLocation)
                    addMarkerAtLocation(userLocation, "You (updated)")
                    // if you only need one update, stop after first:
                    stopLocationUpdates()
                }
            }
        }

        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
            requestingLocationUpdates = true
            Log.d(TAG, "requestLocationUpdates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "requestLocationUpdates SecurityException: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationProviderClient.removeLocationUpdates(it)
            Log.d(TAG, "Location updates stopped")
        }
        locationCallback = null
        requestingLocationUpdates = false
    }

    private fun updateMapLocation(location: LatLng) {
        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
        } catch (e: Exception) {
            Log.e(TAG, "updateMapLocation error: ${e.message}")
        }
    }

    private fun addMarkerAtLocation(location: LatLng, title: String) {
        try {
            mMap.addMarker(
                MarkerOptions()
                    .title(title)
                    .position(location)
            )
        } catch (e: Exception) {
            Log.e(TAG, "addMarkerAtLocation error: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}
