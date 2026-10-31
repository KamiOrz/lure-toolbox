package cn.lure.toolbox

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

private val android.content.Context.store by preferencesDataStore("reminder_settings")
data class Settings(val seconds: Int = 5, val sound: Boolean = true, val vibration: Boolean = true)
class SettingsViewModel(app: Application): AndroidViewModel(app) {
    private val secondsKey = intPreferencesKey("seconds")
    private val soundKey = booleanPreferencesKey("sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    val settings = app.store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }
        .map { Settings((it[secondsKey] ?: 5).coerceIn(1,300), it[soundKey] ?: true, it[vibrationKey] ?: true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val error = MutableStateFlow<String?>(null)
    fun save(value: Settings) { viewModelScope.launch {
        try { getApplication<Application>().store.edit { it[secondsKey] = value.seconds; it[soundKey] = value.sound; it[vibrationKey] = value.vibration } }
        catch (_: IOException) { error.value = "设置保存失败，请重试" }
    } }
}
