package cz.novotny.cyclingsensorsoverlay.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sensor_slots_prefs")

/**
 * Jetpack DataStore-backed implementation of [SensorSlotRepository].
 *
 * Handles persistent storage and real-time reactive reading of sensor MAC addresses,
 * device names, and auto-connect preferences for each cycling sensor slot.
 */
class SensorSlotRepositoryImpl(
    private val context: Context
) : SensorSlotRepository {

    private object PreferencesKeys {
        val POWER_MAC = stringPreferencesKey("power_mac")
        val POWER_NAME = stringPreferencesKey("power_name")
        val POWER_AUTOCONNECT = booleanPreferencesKey("power_autoconnect")

        val HR_MAC = stringPreferencesKey("hr_mac")
        val HR_NAME = stringPreferencesKey("hr_name")
        val HR_AUTOCONNECT = booleanPreferencesKey("hr_autoconnect")

        val RADAR_MAC = stringPreferencesKey("radar_mac")
        val RADAR_NAME = stringPreferencesKey("radar_name")
        val RADAR_AUTOCONNECT = booleanPreferencesKey("radar_autoconnect")
    }

    override fun getAssignedSlots(): Flow<Map<SensorType, SensorSlot>> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                mapOf(
                    SensorType.POWER to SensorSlot(
                        slotId = "power_slot",
                        sensorType = SensorType.POWER,
                        name = prefs[PreferencesKeys.POWER_NAME] ?: "",
                        macAddress = prefs[PreferencesKeys.POWER_MAC],
                        autoConnect = prefs[PreferencesKeys.POWER_AUTOCONNECT] ?: true
                    ),
                    SensorType.HEART_RATE to SensorSlot(
                        slotId = "hr_slot",
                        sensorType = SensorType.HEART_RATE,
                        name = prefs[PreferencesKeys.HR_NAME] ?: "",
                        macAddress = prefs[PreferencesKeys.HR_MAC],
                        autoConnect = prefs[PreferencesKeys.HR_AUTOCONNECT] ?: true
                    ),
                    SensorType.RADAR to SensorSlot(
                        slotId = "radar_slot",
                        sensorType = SensorType.RADAR,
                        name = prefs[PreferencesKeys.RADAR_NAME] ?: "",
                        macAddress = prefs[PreferencesKeys.RADAR_MAC],
                        autoConnect = prefs[PreferencesKeys.RADAR_AUTOCONNECT] ?: true
                    )
                )
            }
    }

    override suspend fun saveSlot(slot: SensorSlot) {
        context.dataStore.edit { prefs ->
            when (slot.sensorType) {
                SensorType.POWER -> {
                    slot.macAddress?.let { prefs[PreferencesKeys.POWER_MAC] = it } ?: prefs.remove(PreferencesKeys.POWER_MAC)
                    prefs[PreferencesKeys.POWER_NAME] = slot.name
                    prefs[PreferencesKeys.POWER_AUTOCONNECT] = slot.autoConnect
                }
                SensorType.HEART_RATE -> {
                    slot.macAddress?.let { prefs[PreferencesKeys.HR_MAC] = it } ?: prefs.remove(PreferencesKeys.HR_MAC)
                    prefs[PreferencesKeys.HR_NAME] = slot.name
                    prefs[PreferencesKeys.HR_AUTOCONNECT] = slot.autoConnect
                }
                SensorType.RADAR -> {
                    slot.macAddress?.let { prefs[PreferencesKeys.RADAR_MAC] = it } ?: prefs.remove(PreferencesKeys.RADAR_MAC)
                    prefs[PreferencesKeys.RADAR_NAME] = slot.name
                    prefs[PreferencesKeys.RADAR_AUTOCONNECT] = slot.autoConnect
                }
            }
        }
    }

    override suspend fun clearSlot(sensorType: SensorType) {
        context.dataStore.edit { prefs ->
            when (sensorType) {
                SensorType.POWER -> {
                    prefs.remove(PreferencesKeys.POWER_MAC)
                    prefs.remove(PreferencesKeys.POWER_NAME)
                    prefs.remove(PreferencesKeys.POWER_AUTOCONNECT)
                }
                SensorType.HEART_RATE -> {
                    prefs.remove(PreferencesKeys.HR_MAC)
                    prefs.remove(PreferencesKeys.HR_NAME)
                    prefs.remove(PreferencesKeys.HR_AUTOCONNECT)
                }
                SensorType.RADAR -> {
                    prefs.remove(PreferencesKeys.RADAR_MAC)
                    prefs.remove(PreferencesKeys.RADAR_NAME)
                    prefs.remove(PreferencesKeys.RADAR_AUTOCONNECT)
                }
            }
        }
    }

    override suspend fun clearAllSlots() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
