package com.finanzaspersonales.di
import android.content.Context
import androidx.room.Room
import com.finanzaspersonales.data.local.FinanceDatabase
import com.finanzaspersonales.data.repository.LocalFinanceRepository
import com.finanzaspersonales.domain.repository.FinanceRepository
import com.finanzaspersonales.domain.repository.BackupRepository
import com.finanzaspersonales.data.backup.BackupManager
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
 @Provides @Singleton fun database(@ApplicationContext context: Context): FinanceDatabase = Room.databaseBuilder(context,FinanceDatabase::class.java,"finance.db").build()
 @Provides @Singleton fun backups(@ApplicationContext context: Context, repository: FinanceRepository): BackupRepository = BackupManager(context,repository)
 @Provides @Singleton fun repository(db: FinanceDatabase): FinanceRepository = LocalFinanceRepository(db)
}
