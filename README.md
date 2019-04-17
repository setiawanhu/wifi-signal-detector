# wifi-signal-detector
Wifi signal detection for specific SSID in Duta Wacana Christian University used for collecting signal strength data

The app will do wifi scanning using the WifiManager for every 1.5 seconds when the scan button is pressed. The app will 
store the wifi SSID, frequency, signal level into JSONArray and then will be send to server using OkHttpClient.
