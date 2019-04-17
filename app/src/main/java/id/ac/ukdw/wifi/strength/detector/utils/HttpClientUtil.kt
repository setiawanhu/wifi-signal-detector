package id.ac.ukdw.wifi.strength.detector.utils

import android.util.Log
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class HttpClientUtil {
    companion object {
        //static methods and variable
        val ADDRESS = "192.168.88.12:8000"

        val URL = "http://$ADDRESS"

        val WIFI_URL = "/api/wifi"

        private val header: MediaType? = MediaType.parse("application/json; charset=utf-8")

        /**
         * Prepare a POST request instance
         */
        fun preparePostRequest(url: String, params: Map<String, Any>): Request {
            val jsonBody: JSONObject = prepareBody(params)

            val body: RequestBody = RequestBody.create(header, jsonBody.toString())

            //Building the request
            return Request.Builder()
                            .url(url)
                            .post(body)
                            .build()
        }

        /**
         * Prepare the request body parameter(s)
         */
        fun prepareBody(params: Map<String, Any>): JSONObject {
            val body = JSONObject()

            for ((key, value) in params) {
                body.put(key, value)
            }

            return body
        }
    }
}