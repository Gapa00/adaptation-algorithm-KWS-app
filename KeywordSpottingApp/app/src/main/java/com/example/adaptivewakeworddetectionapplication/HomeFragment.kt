package com.example.adaptivewakeworddetectionapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adaptivewakeworddetectionapplication.WordDetectionService.Companion
import com.example.adaptivewakeworddetectionapplication.databinding.FragmentHomeBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private var sharedPreferences: SharedPreferences? = null

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var detectionResultAdapter: DetectionResultAdapter
    private var detectionResults = mutableListOf<DetectionResult>()

    var energyThresh: Float = 0.01f
    var confidenceThresh: Float = 80.0f
    var smallThreshold: Float = 40.0f
    var mediumThreshold: Float = 80.0f

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = activity?.let { PreferenceManager.getDefaultSharedPreferences(it) }
//        sharedPreferences = requireActivity().getSharedPreferences(getString(R.string.preferences_name), Context.MODE_PRIVATE)

        detectionResultAdapter = DetectionResultAdapter(detectionResults)
        registerReceiver()

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = detectionResultAdapter

        loadHistoricalData()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        energyThresh = sharedPreferences?.getFloat(R.string.settings_slider.toString(), 0.01f)!!
        confidenceThresh = sharedPreferences?.getFloat(R.string.settings_input.toString(), 80.0f)!!
        smallThreshold = sharedPreferences?.getFloat(R.string.smallThresh.toString(), 40.0f)!!
        mediumThreshold = sharedPreferences?.getFloat(R.string.mediumThresh.toString(), 80.0f)!!

        sharedViewModel.isServiceRunning.observe(viewLifecycleOwner) { isRunning ->
            binding.btnRecord.setImageResource(
                if (isRunning) R.drawable.baseline_mic_off_24 else R.drawable.baseline_mic_24
            )
        }

        binding.btnRecord.setOnClickListener {
            val isRunning = sharedViewModel.isServiceRunning.value ?: false
            if (isRunning) {
                binding.btnRecord.setImageResource(R.drawable.baseline_mic_24)
                stopRecordingService()
            } else {
                binding.btnRecord.setImageResource(R.drawable.baseline_mic_off_24)
                startRecordingService()
            }
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(activity, WordDetectionService::class.java).apply {
            putExtra("ACTION", "START")
            putExtra("ENERGY_THRESHOLD", energyThresh)
            putExtra("MIN_CONFIDENCE_THRESHOLD", confidenceThresh)
            putExtra("SMALL_TO_MEDIUM_THRESHOLD", smallThreshold)
            putExtra("MEDIUM_TO_LARGE_THRESHOLD", mediumThreshold)
        }

        activity?.startService(serviceIntent)
        sharedViewModel.setServiceRunning(true)

    }

    private fun stopRecordingService() {
        val serviceIntent = Intent(activity, WordDetectionService::class.java).apply {
            putExtra("ACTION", "STOP")
        }
        activity?.startService(serviceIntent)
        sharedViewModel.setServiceRunning(false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerReceiver() {
        val filter = IntentFilter(WordDetectionService.BROADCAST_ACTION)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(wakeWordReceiver, filter)
    }

    private fun unregisterReceiver() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(wakeWordReceiver)
    }

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val detectionResult = intent?.getParcelableExtra<DetectionResult>(WordDetectionService.EXTRA_WAKE_WORD)
            detectionResult?.let {
                activity?.runOnUiThread {
                    detectionResultAdapter.addDetectionResult(it)
                    binding.recyclerView.scrollToPosition(0)
                    saveDetectionResults()
                }
            }
        }
    }

    private fun loadHistoricalData() {
        val json = sharedPreferences?.getString(R.string.detection_results.toString(), "")
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<DetectionResult>>() {}.type
            val historicalData: List<DetectionResult> = Gson().fromJson(json, type)
            detectionResultAdapter.setDetectionResults(historicalData)
        }
    }

    private fun saveDetectionResults() {
        val editor = sharedPreferences?.edit()
        val json = Gson().toJson(detectionResults)
        editor?.putString(R.string.detection_results.toString(), json)
        editor?.apply()
    }

    companion object {
        private val TAG = HomeFragment::class.simpleName
    }
}