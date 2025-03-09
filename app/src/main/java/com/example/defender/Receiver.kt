package com.example.defender

import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class Receiver : BroadcastReceiver() {

    companion object {
        private var requestQueue: RequestQueue? = null

        fun getRequestQueue(context: Context): RequestQueue {
            if (requestQueue == null) {
                requestQueue = Volley.newRequestQueue(context.applicationContext)
            }
            return requestQueue!!
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val servo = intent.getIntExtra("servo", 1)
        val ip = intent.getStringExtra("ip") ?: return
        val pills = intent.getIntExtra("pills", 1)
        val url = "http://$ip/dispense?servo=$servo&pills=$pills"
        val queue = getRequestQueue(context)

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d("Defender", "Success: $response")
                if (response.contains("dispensed")) {
                    checkProximitySensor(ip, servo, context)
                }
            },
            { error -> Log.e("Defender", "Error: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun checkProximitySensor(ip: String, servo: Int, context: Context) {
        val url = "http://$ip/proximity?servo=$servo"
        val queue = getRequestQueue(context)

        val stringRequest = object : StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d("Defender", "Proximity Response: $response")
                if (response.contains("no_change", ignoreCase = true)) {
                    showNotification(context, "Take Medicine", "Medicine has not been taken")
                } else if (response.contains("detected", ignoreCase = true)) {
                    Log.d("Defender", "Pill taken successfully")
                } else {
                    Log.e("Defender", "Unexpected proximity response: $response")
                }
            },
            { error -> Log.e("Defender", "Error checking proximity sensor: ${error.message}") }
        ) {
            override fun getRetryPolicy(): DefaultRetryPolicy {
                return DefaultRetryPolicy(
                    15000, // Timeout in milliseconds
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )
            }
        }
        queue.add(stringRequest)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, "pill_dispenser_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(context)) {
            notify(1, builder.build())
        }
    }
}