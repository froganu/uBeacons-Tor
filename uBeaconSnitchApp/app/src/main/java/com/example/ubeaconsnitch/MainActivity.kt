package com.example.ubeaconsnitch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

const val FREQ0 = 18500.0
const val FREQ1 = 19500.0
const val UBEACON_UUID = "1011010011001100" //16 bits
const val URL_API = "https://ubeaconsnitch.click/api/beacon-data"

class MainActivity : AppCompatActivity() {
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)
        audioRecorder = AudioRecorder()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startDetection()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopDetection()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun startDetection() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (requiredPermissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startAudioDetection()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, 101)
        }
    }

    private fun startAudioDetection() {
        updateStatus("Estado: Detectando...")
        audioRecorder.onBeaconDetected = {
            requestCurrentLocationAndSend()
            updateStatus("Estado: DETECTADA BALIZA UUID: $UBEACON_UUID")
        }
        audioRecorder.start()
    }

    // Solicita una localización activa y envía los datos al servidor
    private fun requestCurrentLocationAndSend() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                Toast.makeText(this, "Permiso de localización no concedido", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(0)
            .setNumUpdates(1)

        val detectionTime = System.currentTimeMillis()
        val detectionTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(detectionTime))

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                val location = result.lastLocation
                val ipAddress = getIPAddress()

                val jsonData = JSONObject().apply {
                    put("ip_address", ipAddress)
                    put("latitude", location?.latitude ?: JSONObject.NULL)
                    put("longitude", location?.longitude ?: JSONObject.NULL)
                    put("timestamp", detectionTime)
                    put("detection_time", detectionTimeStr) // NUEVO CAMPO
                }

                sendToServer(jsonData)
            }
        }, mainLooper)
    }

    private fun getIPAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (interfaz in interfaces) {
                for (addr in interfaz.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "N/A"
                    }
                }
            }
            "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun sendToServer(data: JSONObject) {
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = data.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(URL_API)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Network", "Error enviando datos", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error enviando datos", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Datos enviados correctamente", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("Network", "Respuesta no exitosa: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error en servidor: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun stopDetection() {
        audioRecorder.stop()
        updateStatus("Estado: Inactivo")
        tvResult.text = "Esperando baliza..."
    }

    private fun updateStatus(text: String) {
        runOnUiThread {
            tvStatus.text = text
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startAudioDetection()
            } else {
                Toast.makeText(
                    this,
                    "Se necesitan permisos de micrófono y localización",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
