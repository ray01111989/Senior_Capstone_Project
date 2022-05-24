package com.example.fp_test

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.example.fp_test.net.Client
import com.example.fp_test.net.ServerCommand
import com.example.fp_test.net.ServerMessage
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.withLock


class MainActivity : AppCompatActivity() {
    private val TAG: String = this.javaClass.name

    private var client: Client? = null
    private var serverAddress: InetAddress? = null
    private var serverPort: Int? = null
    private var clientNeedsUpdate = false

    /**
     * Sensor poll periods, in seconds. All periods must be divisible by
     * `sensorPollPeriodCommonFactor`. When changing, it's important to preserve this common factor!
     *
     * TODO: Check if wrapping period in `AtomicInteger` is necessary.
     */
    private val sensorPollPeriodsSec: ConcurrentHashMap<ServerCommand, AtomicInteger> = ConcurrentHashMap(
        hashMapOf(
            ServerCommand.GET_LIGHT to AtomicInteger(15 * 60),
            ServerCommand.GET_TEMP to AtomicInteger(15 * 60),
            ServerCommand.GET_HUMIDITY to AtomicInteger(60 * 60),
            ServerCommand.GET_MOISTURE to AtomicInteger(60 * 60),
            ServerCommand.GET_FERTILIZER to AtomicInteger(120 * 60),
            ServerCommand.GET_WATER to AtomicInteger(120 * 60),
        )
    )

    /**
     * Common factor for sensor periods. Any changes to `sensorPollPeriodsMin` must preserve this
     * factor.
     */
    private val sensorPollPeriodCommonFactor: Int = 15

    private val sensorDataSeries: ConcurrentHashMap<ServerCommand, LineGraphSeries<DataPoint>> = ConcurrentHashMap(
        hashMapOf(
            ServerCommand.GET_TEMP to LineGraphSeries(arrayOf()),
            ServerCommand.GET_HUMIDITY to LineGraphSeries(arrayOf()),
            ServerCommand.GET_MOISTURE to LineGraphSeries(arrayOf()),
            ServerCommand.GET_FERTILIZER to LineGraphSeries(arrayOf()),
            ServerCommand.GET_WATER to LineGraphSeries(arrayOf()),
            ServerCommand.GET_LIGHT to LineGraphSeries(arrayOf()),
        )
    )

    private val maxGraphDataPoints: Int = 5

    /**
     * The lock for network activity, such as communicating with the server and changing the client,
     * server address, or server port number. See `handleConnection()` and
     * `configureServerAddress()` for example usages.
     */
    private val networkLock: ReentrantLock = ReentrantLock()

    /**
     * The condition variable for any changes in the network, such as a changed server address or
     * modified client.
     */
    private val networkChanged: Condition = networkLock.newCondition()

    /**
     * Queue for messages to send to the server. Associated networking logic is handled in
     * `handleConnection()`.
     */
    private val serverMsgQueue: ConcurrentLinkedQueue<ServerMessage> = ConcurrentLinkedQueue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val commandThread = Thread(this::handleConnection)
        val clientThread = Thread(this::updateClient)
        val sensorThread = Thread(this::pollSensors)

        commandThread.start()
        clientThread.start()
        sensorThread.start()

        setContentView(R.layout.activity_main)
    }

    /**
     * Network logic to run in a separate thread: check for server commands in `cmdQueue`, then send
     * the command to the server.
     */
    private fun handleConnection() {
        while (true) {
            networkLock.withLock {
                val msg = serverMsgQueue.poll() ?: return@withLock

                while (client == null) {
                    // TODO: Display a graphic somewhere on the screen while waiting to indicate
                    //       pending commands
                    networkChanged.await()
                }

                try {
                    val response = client!!.sendCommand(msg)
                    Log.d(TAG, "Sent to server: $msg (response=$response)")

                    response?.processData { data, time ->
                        val timeVal = time.toEpochSecond(ZoneOffset.UTC)
                        val dataPoint = DataPoint(timeVal.toDouble(), data.toDouble())
                        sensorDataSeries[msg.command]!!.appendData(dataPoint, false, maxGraphDataPoints)
                        Log.d(TAG, "Plotted data: $dataPoint")
                    }
                } catch (e: EOFException) {
                    // Attempt to reconnect on EOF
                    try {
                        client!!.reconnect()
                    } catch (e: SocketException) {
                        // TODO: Handle server connect failure
                    }
                } catch (e: SocketException) {
                    // TODO: Handle command send failure
                }
            }
        }
    }

    /**
     * Client configuration logic to run in a separate thread. This thread waits until the server
     * information has been changed, then disconnects the client from the old server and connects it
     * to the new one.
     */
    private fun updateClient() {
        while (true) {
            networkLock.withLock {
                while (!clientNeedsUpdate) {
                    networkChanged.await()
                }

                if (client?.isClosed != true) {
                    client?.close()
                }

                val newAddr = serverAddress
                val newPort = serverPort

                if (newAddr == null || newPort == null) {
                    client = null
                } else {
                    try {
                        client = Client(InetSocketAddress(newAddr, newPort))
                        networkChanged.signalAll()
                    } catch (e: SocketException) {
                        // TODO: Handle client init failure
                    }
                }

                clientNeedsUpdate = false
            }
        }
    }

    /**
     * Periodically poll sensors, sending a request to the Arduino for new data.
     */
    private fun pollSensors() {
        val msPerSec = 1000L
        var timer = 0L

        val timerStep = sensorPollPeriodCommonFactor

        while (true) {
            for ((cmd, period) in sensorPollPeriodsSec) {
                val pd = period.get()
                val timeToNextPoll = timer % pd

                check(pd % sensorPollPeriodCommonFactor == 0) {
                    "${cmd.name} period ($pd) is not divisible by $sensorPollPeriodCommonFactor"
                }

                if (timeToNextPoll == 0L) {
                    Log.d(TAG, "Queued sensor request: ${cmd.name}")
                    val msg = ServerMessage(cmd, null)
                    serverMsgQueue.add(msg)
                }
            }

            Thread.sleep(timerStep * msPerSec)
            timer += timerStep
        }
    }

    /**
     * Update the server socket address and point the client to the new server location. The command
     * queue is also cleared to avoid sending commands to the wrong server.
     */
    private fun configureServerAddress(serverAddress: InetAddress?, serverPort: Int?) {
        val addrNotNull = serverAddress != null
        val portNotNull = serverPort != null
        require(addrNotNull == portNotNull)

        networkLock.withLock {
            Log.i(TAG, "Changing server socket address from ${this.serverAddress}:${this.serverPort} to $serverAddress:$serverPort")
            this.serverAddress = serverAddress
            this.serverPort = serverPort
            clientNeedsUpdate = true
            networkChanged.signalAll()
            serverMsgQueue.clear() // don't keep old commands in the queue; meant for old server
        }
    }

    fun closeClient(view: View) {
        val oldAddr = serverAddress
        val oldPort = serverPort
        configureServerAddress(null, null)
        displayToast("Disconnected from server at $oldAddr:$oldPort")
    }

    /**
     * Function for dispensing water to flowerpot
     */
    fun dispenseWater(view: View) {
        val choice = findViewById<Spinner>(R.id.mlSelectorSpinner)
        val selection = choice.selectedItem.toString()
        check(selection.isNotEmpty())

        // First digit of selection should be an integer
        val qtyStr = selection[0].toString()
        val amount = try {
            Integer.parseInt(qtyStr).toByte()
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid dispense selection: ${choice.selectedItem}")
            return
        }

        val msg = ServerMessage(ServerCommand.DISPENSE_WATER, amount)
        serverMsgQueue.add(msg)
        displayToast("Queued command for dispensing $amount mL of water")
    }

    /**
     * Initialize graph
     */
    fun setupGraph(
        graph: GraphView,
        series: LineGraphSeries<DataPoint>,
        title: String,
        xAxisTitle: String,
        yAxisTitle: String,
        yMax: Double,
    ) {
        series.color = Color.rgb(226, 91, 34)
        series.isDrawBackground = true
        series.isDrawDataPoints = true
        series.dataPointsRadius = 15F
        series.backgroundColor = Color.parseColor("#0D000000")

        series.setOnDataPointTapListener { series, dataPoint ->
            val t = Toast.makeText(
                this@MainActivity,
                "Data point clicked: $dataPoint",
                Toast.LENGTH_LONG
            )
            t.setGravity(Gravity.CENTER_VERTICAL, 1, 1)
            t.show()
        }

        graph.viewport.setMaxY(yMax)
        graph.viewport.isYAxisBoundsManual = true;
        graph.gridLabelRenderer.horizontalAxisTitle = xAxisTitle;
        graph.gridLabelRenderer.verticalAxisTitle = yAxisTitle;
        graph.title = title

        graph.addSeries(series)
        graph.gridLabelRenderer.labelFormatter = object : DefaultLabelFormatter() {
            override fun formatLabel(value: Double, isValueX: Boolean): String {
                return if (isValueX) {
                    val time = LocalDateTime.ofEpochSecond(value.toLong(), 0, ZoneOffset.UTC)
                    DateTimeFormatter.ofPattern("HH:mm:ss").format(time)
                } else {
                    super.formatLabel(value, isValueX)
                }
            }
        }
    }

    /**
     * Set up temparture graph
     * Currently only working graph
     */
    fun setupTemperatureGraph(
        graph: GraphView,
        series: LineGraphSeries<DataPoint>,
    ) {
        setupGraph(
            graph, series,
            "Thermometer",
            "Time (hh:mm:ss)",
            "Temperature (Degrees F)",
            100.0,
        )
    }

    /**
     * Graphing function that draws graph
     * See Android's graphing library documentation for more info
     */
    @SuppressLint("RestrictedApi")
    fun graphing(view: View) {
        val sensorCmd = ServerCommand.GET_TEMP

        val handler = Handler();

        val myunit = findViewById<Spinner>(R.id.timeUnitsTherm)
        val tunit = findViewById<Spinner>(R.id.timeScaleTherm)
        val multiplier = Integer.parseInt(myunit.selectedItem.toString()).toLong()

        val msPerSec = 1000L
        val msPerMin = msPerSec * 60L
        val msPerHour = msPerMin * 60L

        val delay = when (tunit.selectedItem.toString()) {
            "Seconds" -> msPerSec * multiplier
            "Minutes" -> msPerMin * multiplier
            "Hours" -> msPerHour * multiplier
            else -> 0
        }

        check(delay % msPerSec == 0L) {
            "Delay is not a whole number of seconds"
        }

        val delaySeconds = (delay / msPerSec).toInt()
        sensorPollPeriodsSec[sensorCmd]!!.set(delaySeconds)

        val graph = findViewById<View>(R.id.thermometer) as GraphView
        val series = sensorDataSeries[sensorCmd]!!
        setupTemperatureGraph(graph, series)

        val getDelayMs = { sensorPollPeriodsSec[sensorCmd]!!.get().toLong() * msPerSec }

        handler.postDelayed(object : Runnable {
            override fun run() {
                graph.addSeries(series)
                handler.postDelayed(this, getDelayMs())
            }
        }, getDelayMs())
    }

    /**
     * All functions with the HereToSomewhere format
     * Are page page switching functions referencing my_nav.xml
     * See Nav Graph Documentation for more information
     */
    fun onClickMainToCredits(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_main_to_credits)
    }

    fun onClickMainToConnect(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_main_to_connect)
    }

    fun onClickMainToLicense(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_main_to_license)
    }

    fun onClickMainToManage(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_main_to_manage)
    }

    fun onClickMainToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_main_to_data)
    }

    fun onClickCreditsToMain(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_credits_to_main)
    }

    fun onClickLicenseToMain(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_license_to_main)
    }

    fun onClickManageToMain(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_manage_to_main)
    }

    fun onClickDataToMain(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_main)
    }

    fun onClickConnectToMain(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_connect_to_main)
    }

    fun onClickDataToThermometer(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_graph)
    }

    fun onClickDataToSoil(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_soilsensor)
    }

    fun onClickDataToLight(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_lightsensor)
    }

    fun onClickDataToWater(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_watersensor)
    }

    fun onClickDataToHygro(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_hygrometer)
    }

    fun onClickDataToFert(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_data_to_fertilizer)
    }

    fun onClickThermToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_graph_to_data)
    }

    fun onClickSoilToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_soilsensor_to_data)
    }

    fun onClickLightToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_lightsensor_to_data)
    }

    fun onClickWaterToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_watersensor_to_data)
    }

    fun onClickHygroToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_hygrometer_to_data)
    }

    fun onClickFertToData(view: View){
        val navCon = view.findNavController()
        navCon.navigate(R.id.action_fertilizer_to_data)
    }

    private fun displayToast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Connect Arduino to App
     */
    fun onClickConnectionInfo(view: View) {
        val enteredIp = findViewById<TextView>(R.id.ipAddress)
        val enteredPort = findViewById<TextView>(R.id.portNumber)

        val ipAddrStr = enteredIp.text.toString()
        val portStr = enteredPort.text.toString()

        // IPv4 regex from https://ihateregex.io/expr/ip
        val ipAddrPattern = Pattern.compile("(\\b25[0-5]|\\b2[0-4][0-9]|\\b[01]?[0-9][0-9]?)(\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}")

        if (!ipAddrPattern.matcher(ipAddrStr).matches()) {
            displayToast("Invalid IP address!")
            return
        }

        val ipAddr = try {
            InetAddress.getByName(ipAddrStr)
        } catch (e: UnknownHostException) {
            displayToast("Invalid IP address!")
            return
        }

        val port = try {
            Integer.parseInt(portStr)
        } catch (e: NumberFormatException) {
            displayToast("Invalid port number!")
            return
        }

        if (port < 0) {
            displayToast("Port number must be non-negative!")
            return
        }

        Log.d(TAG, "Successfully parsed server address from user input")
        configureServerAddress(ipAddr, port)
        displayToast("Connecting to server...")

        networkLock.withLock {
            while (clientNeedsUpdate) {
                networkChanged.await()
            }

            if (client?.isClosed == false) {
                displayToast("Connected to server at $serverAddress:$serverPort")
            } else {
                displayToast("Failed to connect to server")
            }
        }

    }
}