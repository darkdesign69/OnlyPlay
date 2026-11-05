package app.onlyplay.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.onlyplay.database.converters.NetworkProtocolConverter
import app.onlyplay.database.dao.NetworkConnectionDao
import app.onlyplay.database.dao.PlaybackStateDao
import app.onlyplay.database.dao.PlaylistDao
import app.onlyplay.database.dao.RecentlyPlayedDao
import app.onlyplay.database.dao.VideoMetadataDao
import app.onlyplay.database.entities.PlaybackStateEntity
import app.onlyplay.database.entities.PlaylistEntity
import app.onlyplay.database.entities.PlaylistItemEntity
import app.onlyplay.database.entities.RecentlyPlayedEntity
import app.onlyplay.database.entities.VideoMetadataEntity
import app.onlyplay.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
  ],
  version = 8,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class OnlyPlayDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao
}
