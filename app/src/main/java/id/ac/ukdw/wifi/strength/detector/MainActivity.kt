package id.ac.ukdw.wifi.strength.detector

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.material.textfield.TextInputLayout
import id.ac.ukdw.wifi.strength.detector.utils.HttpClientUtil
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_change_ip.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private val wifiSSIDs = arrayOf(
        "ukdw",
        "LAB FTI 4",
        "LAB FTI 3",
        "@FreeBiznetHotspot",
        "LABFTI2",
        "LAB-BEBAS",
        "@wifi.id",
        "LAB MOBILE & WEB"
    )
    private var data = JSONArray()
    private lateinit var wifiManager: WifiManager
    private val handler: Handler = Handler()

    private lateinit var dialog: ProgressDialog

    private var isScanning: Boolean = false
    private var locationId: Int = 0

    private val okHttpClient: OkHttpClient = OkHttpClient()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Location service permission request
        val permissions = arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, 0)

        //Get the shared preferences
        sharedPreferences = getSharedPreferences("wifi-signal", Context.MODE_PRIVATE)
        sharedPreferencesEditor = sharedPreferences.edit()

        //Set the server address text view
        txtServerIp.text = sharedPreferences.getString("server-address", HttpClientUtil.ADDRESS)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setUpSpinner()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.navSync -> {
                if (!isScanning) {
                    //Sync to backend
                    if (data.length() == 0) {
                        Toast.makeText(applicationContext, "Data still empty!", Toast.LENGTH_SHORT).show()
                    } else {
                        showProgressDialog()
                        val params = mutableMapOf<String, Any>()
                        params["data"] = data

                        val url = sharedPreferences.getString("server-url", HttpClientUtil.URL)
                        val request: Request = HttpClientUtil.preparePostRequest("$url${HttpClientUtil.WIFI_URL}", params)

                        okHttpClient.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                e.printStackTrace()
                                runOnUiThread {
                                    Toast.makeText(applicationContext, "Oops, something's wrong", Toast.LENGTH_SHORT)
                                        .show()
                                    dismissProgressDialog()
                                }
                            }

                            override fun onResponse(call: Call, response: Response) {
                                dismissProgressDialog()
                                val result = response.body()?.string()

                                val jsonResult = JSONObject(result)
                                val isSuccess = jsonResult.getBoolean("success")

                                if (isSuccess) {
                                    resetView()

                                    runOnUiThread {
                                        Toast.makeText(applicationContext, "Sync successful", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        })
                    }
                } else {
                    Toast.makeText(applicationContext, "Please stop the scanning first!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()

        handler.removeCallbacks(wifiSignalRunnable)
    }

    private var wifiSignalRunnable: Runnable = object : Runnable {
        override fun run() {
            //Get wifi list
            var wifiList: List<ScanResult> = wifiManager.scanResults

            wifiList.forEach { result ->
                if (wifiSSIDs.contains(result.SSID)) {
                    var json = JSONObject()

                    json.put("ssid", result.SSID)
                    json.put("frequency", result.frequency)
                    json.put("level", WifiManager.calculateSignalLevel(result.level, 5))
                    json.put("location_id", locationId + 1)

                    data.put(json)
                }
            }

            txtScanned.text = data.length().toString()

            handler.postDelayed(this, 1500)
        }
    }

    /**
     * Set up the spinner adapter.
     */
    fun setUpSpinner() {
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.location)
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sprLocation.adapter = spinnerAdapter

        sprLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                locationId = position
            }
        }
    }

    /**
     * Show the loading dialog
     */
    fun showProgressDialog() {
        dialog = ProgressDialog.show(this, "", "Syncing", true)
    }

    /**
     * Dismiss the loading dialog
     */
    fun dismissProgressDialog() {
        dialog.dismiss()
    }

    /**
     * Reset the view and the ssid data
     */
    fun resetView() {
        runOnUiThread {
            data = JSONArray()
            txtScanned.text = 0.toString()
        }
    }

    /**
     * Event Handler
     * -------------
     * Scan button on click event handler
     */
    fun scan(view: View) {
        //Turning on the wifi if the wifi is turned off
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this.applicationContext, "Turning on Wifi", Toast.LENGTH_SHORT).show()

            wifiManager.isWifiEnabled = true
        }

        var locationManager: LocationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var isGpsEnabled: Boolean = false

        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //Check if the location service is not turned on
        if (!isGpsEnabled) {
            AlertDialog.Builder(ContextThemeWrapper(this, R.style.dialog))
                .setMessage("Location not enabled, do you want to enabled it?")
                .setPositiveButton("Enable") { dialog, which ->
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel") { dialog, which ->
                    //
                }
                .show()
        } else {
            if (!isScanning) {
                handler.post(wifiSignalRunnable)
                isScanning = true

                btnScan.text = "Stop Scanning"
            } else {
                handler.removeCallbacks(wifiSignalRunnable)
                isScanning = false

                btnScan.text = "Start Scanning"
            }
        }
    }

    /**
     * Event Handler
     * -------------
     * Change IP button event handler
     */
    fun changeServerIp(view: View) {
        val view: View = layoutInflater.inflate(R.layout.dialog_change_ip, null)
        val edtIpAddress: TextInputLayout = view.findViewById(R.id.edtIpAddress)

        AlertDialog.Builder(ContextThemeWrapper(this, R.style.dialog))
            .setTitle("Set Server Address")
            .setView(view)
            .setPositiveButton("Set") { dialog, which ->
                val address = edtIpAddress.editText?.text.toString()
                val url = "http://$address"

                //Save the defined url to shared preferences
                sharedPreferencesEditor.putString("server-address", address)
                sharedPreferencesEditor.putString("server-url", url)
                sharedPreferencesEditor.apply()

                //Set the defined url to the view
                txtServerIp.text = address

                Toast.makeText(applicationContext, "Successfully Update Server URL", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
