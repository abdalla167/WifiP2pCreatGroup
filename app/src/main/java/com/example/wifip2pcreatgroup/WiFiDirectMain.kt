package com.example.wifip2pcreatgroup

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WiFiDirectMain : AppCompatActivity() {
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private var isWifiP2pEnabled = false
    private var serverSocket: ServerSocket? = null
    private var serverThreads: MutableList<Thread> = mutableListOf()
    val receiver = WifiDirectBroadcastReceiver()
    val intentFilter = IntentFilter()
    private var group: WifiP2pGroup? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        registerReceiver(receiver, intentFilter)
        var createGroupButton = findViewById<Button>(R.id.createGroupButton)

        var removeGroupButton = findViewById<Button>(R.id.removeGroupButton)
        var sendToGroupButton = findViewById<Button>(R.id.sendToGroupButton)
        var startServerButton = findViewById<Button>(R.id.startServerButton)
        var stopServerButton = findViewById<Button>(R.id.stopServerButton)
        var messageEditText = findViewById<EditText>(R.id.messageEditText)

        createGroupButton.setOnClickListener {
            createGroup()
        }

        removeGroupButton.setOnClickListener {
            removeGroup()
        }

        sendToGroupButton.setOnClickListener {
            sendMessageToGroup(messageEditText.text.toString()+"")
        }

        startServerButton.setOnClickListener {
            startServerSocket()
        }

        stopServerButton.setOnClickListener {
            stopServerSocket()
        }
    }

    inner class WifiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Code to handle peers change
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        if (ActivityCompat.checkSelfPermission(
                                context!!,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        manager.requestGroupInfo(channel, object : WifiP2pManager.GroupInfoListener {
                            override fun onGroupInfoAvailable(info: WifiP2pGroup?) {
                                group = info
                                // Code to handle group change
                            }
                        })
                    } else {
                        group = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Code to handle this device change
                }
            }
        }
    }
    fun discoverPeers() {
        if (!isWifiP2pEnabled) {
            Toast.makeText(this, "WiFi Direct is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@WiFiDirectMain, "Discovery started", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(this@WiFiDirectMain, "Discovery failed: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
        discoverPeers()

    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    fun sendMessage(message: String, address: String) {
        val socket = Socket()
        socket.bind(null)
        socket.connect(InetSocketAddress(address, 8888), 5000)

        val outputStream = socket.getOutputStream()
        outputStream.write(message.toByteArray(Charsets.UTF_8))
        outputStream.close()
        socket.close()
    }

    fun startServerSocket() {
        serverSocket = ServerSocket(8888)
        val thread = Thread {
            while (true) {
                val socket = serverSocket!!.accept()
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(1024)
                var length = inputStream.read(buffer)
                val message = String(buffer, 0, length, Charsets.UTF_8)
                inputStream.close()
                socket.close()
                runOnUiThread {
                    Toast.makeText(this, "Received message: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
        thread.start()
        serverThreads.add(thread)
    }

    fun stopServerSocket() {
        serverSocket?.close()
        for (thread in serverThreads) {
            thread.interrupt()
        }
        serverThreads.clear()
    }

    fun createGroup() {
        if (!isWifiP2pEnabled) {
            Toast.makeText(this, "WiFi Direct is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@WiFiDirectMain, "Group created", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(this@WiFiDirectMain, "Group creation failed: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }
    fun removeGroup() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@WiFiDirectMain, "Group removed", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(this@WiFiDirectMain, "Group removal failed: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }
    fun sendMessageToGroup(message: String) {
        if (group == null) {
            Toast.makeText(this, "Not connected to a group", Toast.LENGTH_SHORT).show()
            return
        }

        for (device in group!!.clientList) {
            val address = device.deviceAddress
            sendMessage(message, address)
        }
    }
}