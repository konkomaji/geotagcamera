package com.geotagcamera.geotagginglocationonphoto.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val stampPreferences = StampPreferences(application)

    val fields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampFields())

    fun update(transform: (StampFields) -> StampFields) {
        viewModelScope.launch {
            stampPreferences.update(transform(fields.value))
        }
    }
}
