package com.onetaskman.student.ble

import java.util.UUID

object BleConstants {
    // Matches Python ONEFOCUS_SERVICE_UUID exactly
    const val ONEFOCUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    
    // Matches Python ONEFOCUS_CHAR_UUID exactly
    const val ONEFOCUS_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    // Standard BLE CCCD descriptor UUID for enabling notifications
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    val SERVICE_UUID: UUID = UUID.fromString(ONEFOCUS_SERVICE_UUID)
    val CHAR_UUID: UUID = UUID.fromString(ONEFOCUS_CHAR_UUID)
    val CCCD: UUID = UUID.fromString(CCCD_UUID)
}