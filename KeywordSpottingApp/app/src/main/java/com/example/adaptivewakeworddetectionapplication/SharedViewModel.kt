package com.example.adaptivewakeworddetectionapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _isServiceRunning = MutableLiveData<Boolean>(false)
    val isServiceRunning: LiveData<Boolean> get() = _isServiceRunning

    fun setServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }
}
