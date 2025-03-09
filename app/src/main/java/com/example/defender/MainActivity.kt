package com.example.defender

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var etIpAddress: EditText
    private lateinit var etServo1Pills: EditText
    private lateinit var etServo2Pills: EditText
    private lateinit var tpServo1: TimePicker
    private lateinit var tpServo2: TimePicker
    private lateinit var btnAddServo1: Button
    private lateinit var btnAddServo2: Button
    private lateinit var lvSchedules: ListView
    private lateinit var adapter: ArrayAdapter<*>
    private val scheduleList = mutableListOf<String>()
    private lateinit var alarmManager: AlarmManager
    private lateinit var etPillsServo1: EditText
    private lateinit var etPillsServo2: EditText
    private lateinit var btnResetPills: Button
    private var pillsServo1 = 0
    private var pillsServo2 = 0

    // Remaining Pills TextViews
    private lateinit var tvRemainingPillsServo1: TextView
    private lateinit var tvRemainingPillsServo2: TextView

    private val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 1
    private val PREFS_NAME = "PillPrefs"
    private val KEY_PILLS_SERVO1 = "pillsServo1"
    private val KEY_PILLS_SERVO2 = "pillsServo2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        etIpAddress = findViewById(R.id.et_ip_address)
        etServo1Pills = findViewById(R.id.et_servo1_pills)
        etServo2Pills = findViewById(R.id.et_servo2_pills)
        tpServo1 = findViewById(R.id.tp_servo1)
        tpServo2 = findViewById(R.id.tp_servo2)
        btnAddServo1 = findViewById(R.id.btn_add_servo1_schedule)
        btnAddServo2 = findViewById(R.id.btn_add_servo2_schedule)
        lvSchedules = findViewById(R.id.lv_schedules)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        etPillsServo1 = findViewById(R.id.et_pills_servo1)
        etPillsServo2 = findViewById(R.id.et_pills_servo2)
        btnResetPills = findViewById(R.id.btn_reset_pills)

        // Remaining Pills TextViews
        tvRemainingPillsServo1 = findViewById(R.id.tv_remaining_pills_servo1)
        tvRemainingPillsServo2 = findViewById(R.id.tv_remaining_pills_servo2)

        // Load saved pill counts from SharedPreferences
        loadPillCounts()

        // Initialize adapter and list view
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, scheduleList)
        lvSchedules.adapter = adapter

        // Set click listeners
        btnAddServo1.setOnClickListener { addSchedule(1) }
        btnAddServo2.setOnClickListener { addSchedule(2) }

        // Reset button functionality
        btnResetPills.setOnClickListener {
            resetPillCounts()
        }

        // Initialize notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pill Dispenser Channel"
            val descriptionText = "Channel for pill dispenser notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("pill_dispenser_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun addSchedule(servo: Int) {
        val ipAddress = etIpAddress.text.toString().trim()
        if (ipAddress.isEmpty()) {
            Toast.makeText(this, "Enter NodeMCU IP Address", Toast.LENGTH_SHORT).show()
            return
        }

        val hour = if (servo == 1) tpServo1.hour else tpServo2.hour
        val minute = if (servo == 1) tpServo1.minute else tpServo2.minute
        val pills = if (servo == 1) etServo1Pills.text.toString().trim() else etServo2Pills.text.toString().trim()

        if (pills.isEmpty()) {
            Toast.makeText(this, "Enter number of pills for Servo $servo", Toast.LENGTH_SHORT).show()
            return
        }

        val pillsInt = pills.toIntOrNull() ?: run {
            Toast.makeText(this, "Invalid number of pills", Toast.LENGTH_SHORT).show()
            return
        }

        // Decrement pill count based on the scheduled pills
        when (servo) {
            1 -> {
                if (pillsInt > pillsServo1) {
                    Toast.makeText(this, "Not enough pills in Chamber 1", Toast.LENGTH_SHORT).show()
                    return
                }
                pillsServo1 -= pillsInt
            }
            2 -> {
                if (pillsInt > pillsServo2) {
                    Toast.makeText(this, "Not enough pills in Chamber 2", Toast.LENGTH_SHORT).show()
                    return
                }
                pillsServo2 -= pillsInt
            }
        }

        // Update the remaining pill count display and save to SharedPreferences
        updatePillCountDisplay()

        // Add the schedule to the list
        val scheduleText = "Servo $servo, ${String.format("%02d:%02d", hour, minute)}, Pills: $pills"
        scheduleList.add(scheduleText)
        adapter.notifyDataSetChanged()

        // Schedule the alarm
        scheduleAlarm(servo, hour, minute, ipAddress, pillsInt)

        // Clear the input fields
        if (servo == 1) {
            etServo1Pills.text.clear()
        } else {
            etServo2Pills.text.clear()
        }
    }

    private fun scheduleAlarm(servo: Int, hour: Int, minute: Int, ip: String, pills: Int) {
        val intent = Intent(this, Receiver::class.java)
        intent.putExtra("servo", servo)
        intent.putExtra("ip", ip)
        intent.putExtra("pills", pills)
        val requestCode = servo * 100 + minute // Unique request code for each alarm
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        // If the time is in the past, increment the day
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                Toast.makeText(this, "Please grant 'Allow setting alarms and reminders' permission for the app", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivityForResult(intent, REQUEST_CODE_SCHEDULE_EXACT_ALARM)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open alarm settings", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        Toast.makeText(this, "Scheduled alarm for Servo $servo at ${String.format("%02d:%02d", hour, minute)}", Toast.LENGTH_SHORT).show()
    }

    private fun updatePillCountDisplay() {
        // Update the remaining pills TextViews
        tvRemainingPillsServo1.text = "Servo 1: $pillsServo1 pills"
        tvRemainingPillsServo2.text = "Servo 2: $pillsServo2 pills"

        // Show notifications if pills reach 5
        if (pillsServo1 <= 5) {
            showNotification("Refill Needed", "Servo 1 needs refill")
        }
        if (pillsServo2 <= 5) {
            showNotification("Refill Needed", "Servo 2 needs refill")
        }

        // Save the updated pill counts to SharedPreferences
        savePillCounts()
    }

    private fun resetPillCounts() {
        // Get the new pill counts from the bottom section
        val newPillsServo1 = etPillsServo1.text.toString().toIntOrNull() ?: 0
        val newPillsServo2 = etPillsServo2.text.toString().toIntOrNull() ?: 0

        // Update the pill counts
        pillsServo1 = newPillsServo1
        pillsServo2 = newPillsServo2

        // Update the display and save to SharedPreferences
        updatePillCountDisplay()

        Toast.makeText(this, "Pill counts reset", Toast.LENGTH_SHORT).show()
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, "pill_dispenser_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.notify(1, builder.build())
    }

    private fun savePillCounts() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_PILLS_SERVO1, pillsServo1)
        editor.putInt(KEY_PILLS_SERVO2, pillsServo2)
        editor.apply() // Use apply() for asynchronous saving
    }

    private fun loadPillCounts() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        pillsServo1 = sharedPreferences.getInt(KEY_PILLS_SERVO1, 0) // Default value is 0
        pillsServo2 = sharedPreferences.getInt(KEY_PILLS_SERVO2, 0)
        updatePillCountDisplay() // Update the UI with the loaded values
    }
}