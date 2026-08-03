package com.bam.sshfs.data.db

import androidx.room.TypeConverter
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType

/** Stores the model enums as their stable names rather than ordinals. */
class Converters {
    @TypeConverter fun keyTypeToString(value: KeyType): String = value.name
    @TypeConverter fun stringToKeyType(value: String): KeyType = KeyType.valueOf(value)

    @TypeConverter fun keyOriginToString(value: KeyOrigin): String = value.name
    @TypeConverter fun stringToKeyOrigin(value: String): KeyOrigin = KeyOrigin.valueOf(value)
}
