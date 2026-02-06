package amalia.skripsi.deteksipadi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazardRepository @Inject constructor() {
    private val _isDanger = MutableStateFlow(false)
    val isDanger = _isDanger.asStateFlow()

    private val _currentDistance = MutableStateFlow(0.0)
    val currentDistance = _currentDistance.asStateFlow()

    fun setDangerStatus(isDanger: Boolean, distance: Double) {
        _isDanger.value = isDanger
        _currentDistance.value = distance
    }
}