package amalia.skripsi.deteksipadi.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "padi-database"
        ).build()
    }

    @Provides
    @Singleton
    fun providePendingReportDao(appDatabase: AppDatabase): PendingReportDao {
        return appDatabase.pendingReportDao()
    }
}