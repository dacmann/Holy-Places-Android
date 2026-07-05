package net.dacworld.android.holyplacesofthelord.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

object NameChangeContract {
    const val TABLE_NAME = "temple_name_changes"
    const val COLUMN_ID = "name_change_id"
    const val COLUMN_TEMPLE_ID = "temple_id"
    const val COLUMN_OLD_NAME = "old_name"
    const val COLUMN_CHANGE_DATE = "change_date"
    const val COLUMN_OLD_IMAGE_URL = "old_image_url"
    const val COLUMN_OLD_IMAGE_DATA = "old_image_data"
}

/**
 * A historical name for a place, sourced from `<oldName>` elements in HolyPlaces.xml.
 *
 * Example: `<oldName changeDate="1999-10-16" oldImage="https://...">Swiss Temple</oldName>`
 */
@Entity(
    tableName = NameChangeContract.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Temple::class,
            parentColumns = [TempleContract.COLUMN_ID],
            childColumns = [NameChangeContract.COLUMN_TEMPLE_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [NameChangeContract.COLUMN_TEMPLE_ID])]
)
data class TempleNameChange(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = NameChangeContract.COLUMN_ID)
    val id: Long = 0,

    @ColumnInfo(name = NameChangeContract.COLUMN_TEMPLE_ID)
    val templeId: String,

    @ColumnInfo(name = NameChangeContract.COLUMN_OLD_NAME)
    val oldName: String,

    /**
     * The first day the new name applies. Visits dated before this date keep
     * the historical name. Null means the rename applies to all dates (the
     * old name is used for matching only, never for date-based display).
     */
    @ColumnInfo(name = NameChangeContract.COLUMN_CHANGE_DATE)
    val changeDate: LocalDate? = null,

    /** URL of the place image that was valid under the old name. */
    @ColumnInfo(name = NameChangeContract.COLUMN_OLD_IMAGE_URL)
    val oldImageUrl: String? = null,

    /** Downloaded historical image cached locally for offline display. */
    @ColumnInfo(name = NameChangeContract.COLUMN_OLD_IMAGE_DATA, typeAffinity = ColumnInfo.BLOB)
    val oldImageData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TempleNameChange
        return id == other.id &&
            templeId == other.templeId &&
            oldName == other.oldName &&
            changeDate == other.changeDate &&
            oldImageUrl == other.oldImageUrl
        // oldImageData intentionally excluded (cache only)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + templeId.hashCode()
        result = 31 * result + oldName.hashCode()
        result = 31 * result + (changeDate?.hashCode() ?: 0)
        result = 31 * result + (oldImageUrl?.hashCode() ?: 0)
        return result
    }
}

/**
 * Returns the name that was in use on [onDate]: the oldest historical name whose
 * [TempleNameChange.changeDate] is after the given date, or [currentName] if none apply.
 * Mirrors iOS `Temple.effectiveName(for:)`.
 */
fun List<TempleNameChange>.effectiveName(currentName: String, onDate: LocalDate): String {
    return applicableNameChange(onDate)?.oldName ?: currentName
}

/**
 * Returns the name change whose historical name/image applies on [onDate], if any.
 */
fun List<TempleNameChange>.applicableNameChange(onDate: LocalDate): TempleNameChange? {
    return this
        .mapNotNull { change -> change.changeDate?.let { date -> change to date } }
        .sortedBy { (_, date) -> date }
        .firstOrNull { (_, date) -> onDate.isBefore(date) }
        ?.first
}
