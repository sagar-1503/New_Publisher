// init everthing working


// down below there is an undated version

package com.example.akash.new_publisher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class PublisherActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var isLocationEnabled: Boolean = false
    private var client: Mqtt5BlockingClient? = null

    private lateinit var liveLocationTextView: TextView
    private lateinit var sendLocationButton: Button

    private var lastKnownLatitude: Double? = null
    private var lastKnownLongitude: Double? = null

    private var studentId: String? = null // To store the student ID
    private var minSpeed: Double? = null // To track the minimum speed
    private var maxSpeed: Double? = null // To track the maximum speed

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MQTT_BROKER_HOST = "broker-816036149.sundaebytestt.com"
        private const val MQTT_BROKER_PORT = 1883
        private const val MQTT_TOPIC = "assignment/location"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publisher)

        // Retrieve the student ID from the Intent
        studentId = intent.getStringExtra("STUDENT_ID")
        Toast.makeText(this, "Welcome Student ID: $studentId", Toast.LENGTH_SHORT).show()

        // Initialize MQTT client
        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost(MQTT_BROKER_HOST)
            .serverPort(MQTT_BROKER_PORT)
            .build()
            .toBlocking()

        // Initialize LocationManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Initialize UI elements
        liveLocationTextView = findViewById(R.id.tv_live_location)
        sendLocationButton = findViewById(R.id.btn_send_location)

        // Set up send location button click listener
        sendLocationButton.setOnClickListener {
            sendCurrentLocationToBroker()
        }
    }

    fun enableLocation(view: View?) {
        if (isLocationEnabled) {
            Toast.makeText(this, "Location already enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if permissions are granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permissions
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Start receiving location updates
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000, // Minimum time interval between updates (2 seconds)
            10f,  // Minimum distance between updates (10 meters)
            this
        )

        // Connect to MQTT broker
        try {
            client?.connect()
            Toast.makeText(this, "Connected to MQTT broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
            Log.e("MQTT", "Error connecting to broker: $e")
        }

        isLocationEnabled = true
        Toast.makeText(this, "Location enabled", Toast.LENGTH_SHORT).show()
    }

    fun disableLocation(view: View?) {
        if (!isLocationEnabled) {
            Toast.makeText(this, "Location already disabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Stop receiving location updates
        locationManager.removeUpdates(this)
        isLocationEnabled = false
        liveLocationTextView.text = "Location updates are disabled."

        // Disconnect from MQTT broker
        try {
            client?.disconnect()
            Toast.makeText(this, "Disconnected from MQTT broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to disconnect from MQTT broker", Toast.LENGTH_SHORT).show()
            Log.e("MQTT", "Error disconnecting from broker: $e")
        }
    }

    override fun onLocationChanged(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val speed = location.speed.toDouble() // Get the current speed in meters/second

        // Update last known latitude and longitude
        lastKnownLatitude = latitude
        lastKnownLongitude = longitude

        // Update min and max speeds
        minSpeed = if (minSpeed == null) speed else minOf(minSpeed!!, speed)
        maxSpeed = if (maxSpeed == null) speed else maxOf(maxSpeed!!, speed)

        // Update the live location TextView
        liveLocationTextView.text = "Live Location:\nLatitude: $latitude\nLongitude: $longitude\nSpeed: $speed m/s"

        // Log the location
        Log.d("LOCATION", "Lat: $latitude, Lon: $longitude, Speed: $speed")
    }

    private fun sendCurrentLocationToBroker() {
        if (lastKnownLatitude == null || lastKnownLongitude == null) {
            Toast.makeText(this, "Location not available yet. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        // Construct the payload
        val payload = """
            Student ID: $studentId
            Lat: $lastKnownLatitude
            Lon: $lastKnownLongitude
            Min Speed: $minSpeed
            Max Speed: $maxSpeed
        """.trimIndent()

        try {
            client?.publishWith()
                ?.topic(MQTT_TOPIC)
                ?.payload(payload.toByteArray())
                ?.send()
            Toast.makeText(this, "Location sent to MQTT broker.", Toast.LENGTH_SHORT).show()
            Log.d("MQTT", "Published message: $payload")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send location to MQTT broker.", Toast.LENGTH_SHORT).show()
            Log.e("MQTT", "Error publishing message: $e")
        }
    }

    override fun onProviderEnabled(provider: String) {
        Toast.makeText(this, "GPS Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "GPS Disabled. Enable it to receive location updates.", Toast.LENGTH_LONG).show()
        liveLocationTextView.text = "GPS is disabled."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Permission granted. Enable location again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Cannot access location.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

