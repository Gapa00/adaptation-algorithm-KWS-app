package com.example.adaptivewakeworddetectionapplication

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.google.android.material.slider.Slider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class SettingsFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        sharedPreferences = activity?.let { PreferenceManager.getDefaultSharedPreferences(it) }!!

        val slider: Slider = view.findViewById(R.id.settingsSlider)
        val editText: EditText = view.findViewById(R.id.settingsEditText)
        val smallThresh: EditText = view.findViewById(R.id.smallToMediumThresholdEditText)
        val mediumThresh: EditText = view.findViewById(R.id.mediumToLargeThresholdEditText)
        val updateButton: Button = view.findViewById(R.id.updateButton)

        val savedSliderValue = sharedPreferences.getFloat(R.string.settings_slider.toString(), 0.01f).toDouble()
        val savedEditTextValue = sharedPreferences.getFloat(R.string.settings_input.toString(), 80.0f)
        val savedSmallThreshValue = sharedPreferences.getFloat(R.string.smallThresh.toString(), 40.0f)
        val savedMediumThreshValue = sharedPreferences.getFloat(R.string.mediumThresh.toString(), 80.0f)

        slider.value = savedSliderValue.toFloat()
        editText.setText(savedEditTextValue.toString())
        smallThresh.setText(savedSmallThreshValue.toString())
        mediumThresh.setText(savedMediumThreshValue.toString())

        slider.setLabelFormatter { value: Float ->
            String.format("%.3f", value)
        }

        updateButton.setOnClickListener {
            val sliderValue = slider.value.toDouble()
            val editTextValue = editText.text.toString().toFloat()
            val smallThreshValue = smallThresh.text.toString().toFloat()
            val mediumThreshValue = mediumThresh.text.toString().toFloat()


            with(sharedPreferences.edit()) {
                putFloat(R.string.settings_slider.toString(), sliderValue.toFloat())
                putFloat(R.string.settings_input.toString(), editTextValue)
                putFloat(R.string.smallThresh.toString(), smallThreshValue)
                putFloat(R.string.mediumThresh.toString(), mediumThreshValue)
                apply()
            }

            (requireActivity() as MainActivity).returnToHomeFragment()
        }

        return view
    }
}
